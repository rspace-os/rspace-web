package com.researchspace.booking.api.v2;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.api.v2.resource.ApiV2ErrorMapping;
import com.researchspace.api.v2.resource.ApiV2ResourceSpec;
import com.researchspace.api.v2.resource.OpenApiOperationDocumentation;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.api.v2.resource.ResourceOperations;
import com.researchspace.booking.service.BookingOverlapException;
import com.researchspace.booking.service.BookingStateTransitionException;
import com.researchspace.booking.service.BookingTargetUnavailableException;
import com.researchspace.booking.service.BookingWindowException;
import com.researchspace.booking.service.TimeSlotBookingManager;
import com.researchspace.booking.service.TimeSlotBookingManager.Create;
import com.researchspace.booking.service.TimeSlotBookingManager.Patch;
import com.researchspace.model.User;
import com.researchspace.model.booking.ApiV2TimeSlotBookingResource;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingState;
import com.researchspace.model.booking.ResolvedBookableTarget;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.RelationshipTarget;
import com.researchspace.model.collection.ResolvedResourceReference;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceReference;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.service.FeatureFlagManager;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

/** Adapts REST API v2 booking documents to the booking domain manager. */
@Configuration(proxyBeanMethods = false)
public final class TimeSlotBookingResourceOperations
    implements ResourceOperations<TimeSlotBooking, Long> {

  private static final EnumSet<ResourceOperation> OPERATIONS =
      EnumSet.of(
          ResourceOperation.LIST,
          ResourceOperation.COUNT,
          ResourceOperation.READ,
          ResourceOperation.CREATE,
          ResourceOperation.UPDATE);

  private final TimeSlotBookingManager manager;
  private final FeatureFlagManager featureFlags;

  public TimeSlotBookingResourceOperations(
      TimeSlotBookingManager manager, FeatureFlagManager featureFlags) {
    this.manager = manager;
    this.featureFlags = featureFlags;
  }

  @Bean
  ApiV2ResourceSpec<TimeSlotBooking, Long> timeSlotBookingApiV2Resource() {
    List<ApiV2ErrorMapping> createErrors =
        List.of(
            mapping(
                BookingWindowException.class,
                HttpStatus.BAD_REQUEST,
                "errors.api.v2.booking.window",
                "The booking interval is invalid."),
            mapping(
                BookingTargetUnavailableException.class,
                HttpStatus.CONFLICT,
                "errors.api.v2.booking.target.unavailable",
                "The selected target is unavailable."),
            mapping(
                BookingOverlapException.class,
                HttpStatus.CONFLICT,
                "errors.api.v2.booking.overlap",
                "The interval overlaps another booking."));
    List<ApiV2ErrorMapping> updateErrors =
        java.util.stream.Stream.concat(
                createErrors.stream(),
                java.util.stream.Stream.of(
                    mapping(
                        BookingStateTransitionException.class,
                        HttpStatus.CONFLICT,
                        "errors.api.v2.booking.state.transition",
                        "The requested state transition is invalid.")))
            .toList();
    return new ApiV2ResourceSpec<>(
        ApiV2TimeSlotBookingResource.DESCRIPTION,
        this,
        Long::valueOf,
        "errors.api.v2.booking.create",
        "errors.api.v2.booking.patch",
        OPERATIONS,
        Map.of(
            ResourceOperation.CREATE,
            OpenApiOperationDocumentation.builder()
                .description("Creates one confirmed booking for an instrument.")
                .requestExample(
                    Map.of(
                        "target",
                        Map.of("relationTo", "instruments", "value", 123),
                        "start",
                        "2026-10-25T07:30:00Z",
                        "end",
                        "2026-10-25T09:00:00Z",
                        "purpose",
                        "Image plate 4"))
                .build()),
        Map.of(ResourceOperation.CREATE, createErrors, ResourceOperation.UPDATE, updateErrors),
        com.researchspace.model.collection.CollectionMutationLimits.DEFAULT);
  }

  @Override
  public ResourcePage<TimeSlotBooking> find(ResourceRequest request, User subject) {
    return enabled(subject)
        ? manager.getBookings(request, subject)
        : new ResourcePage<>(List.of(), 0);
  }

  @Override
  public long count(ResourceRequest request, User subject) {
    return enabled(subject) ? manager.countBookings(request, subject) : 0;
  }

  @Override
  public Optional<TimeSlotBooking> findById(Long id, User subject) {
    return enabled(subject) ? manager.getBooking(id, subject) : Optional.empty();
  }

  @Override
  public TimeSlotBooking create(ParsedDocument document, ApiV2Caller caller) {
    requireEnabled(caller.subject());
    return manager.createBooking(createCommand(document), caller.subject(), caller.actor());
  }

  @Override
  public Optional<TimeSlotBooking> update(Long id, ParsedDocument document, ApiV2Caller caller) {
    if (!enabled(caller.subject())) {
      return Optional.empty();
    }
    return manager.updateBooking(id, patchCommand(document), caller.subject(), caller.actor());
  }

  private Create createCommand(ParsedDocument document) {
    return new Create(
        target(document),
        value(document, "start", Date.class),
        value(document, "end", Date.class),
        value(document, "purpose", String.class));
  }

  private static Patch patchCommand(ParsedDocument document) {
    return new Patch(
        value(document, "start", Date.class),
        value(document, "end", Date.class),
        document.changed("purpose"),
        value(document, "purpose", String.class),
        value(document, "state", BookingState.class));
  }

  private static ResolvedBookableTarget target(ParsedDocument document) {
    Object value = document.values().get("target");
    if (value == null) {
      return null;
    }
    ResolvedResourceReference<?, ?> resolved = ResolvedResourceReference.class.cast(value);
    ResourceReference<?, ?> reference = resolved.reference();
    BookableTargetType type = BookableTargetType.class.cast(reference.kind());
    Long id = Long.class.cast(reference.id());
    RelationshipTarget<?> metadata =
        ApiV2TimeSlotBookingResource.DESCRIPTION.requireRelationship("target").targetForKind(type);
    InventoryRecord entity = InventoryRecord.class.cast(resolved.entityAs(metadata.entityType()));
    return new ResolvedBookableTarget(new BookableTargetReference(type, id), entity);
  }

  private static <T> T value(ParsedDocument document, String field, Class<T> type) {
    return type.cast(document.values().get(field));
  }

  private boolean enabled(User subject) {
    return featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, subject);
  }

  private void requireEnabled(User subject) {
    if (!enabled(subject)) {
      throw new AuthorizationException("errors.api.v2.forbidden");
    }
  }

  private static ApiV2ErrorMapping mapping(
      Class<? extends RuntimeException> type, HttpStatus status, String code, String description) {
    return ApiV2ErrorMapping.of(type, status, code, description);
  }
}
