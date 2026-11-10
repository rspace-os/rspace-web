package com.researchspace.booking.api.v2;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.api.v2.resource.ApiV2ErrorMapping;
import com.researchspace.api.v2.resource.ApiV2ResourceException;
import com.researchspace.api.v2.resource.ApiV2ResourceSpec;
import com.researchspace.api.v2.resource.CreateResult;
import com.researchspace.api.v2.resource.DeletePolicy;
import com.researchspace.api.v2.resource.IdempotencyPolicy;
import com.researchspace.api.v2.resource.MutationPolicy;
import com.researchspace.api.v2.resource.OpenApiOperationDocumentation;
import com.researchspace.api.v2.resource.ResourceAuditPolicy;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.api.v2.resource.ResourceOperations;
import com.researchspace.api.v2.resource.VersionPolicy;
import com.researchspace.booking.config.BookingResourceAccessConfiguration;
import com.researchspace.booking.service.BookingBufferConflictException;
import com.researchspace.booking.service.BookingConcurrentModificationException;
import com.researchspace.booking.service.BookingCreateExecutor;
import com.researchspace.booking.service.BookingDurationException;
import com.researchspace.booking.service.BookingIdempotencyConflictException;
import com.researchspace.booking.service.BookingLocationFilterManager;
import com.researchspace.booking.service.BookingOverlapException;
import com.researchspace.booking.service.BookingPolicyException;
import com.researchspace.booking.service.BookingStartInPastException;
import com.researchspace.booking.service.BookingStateTransitionException;
import com.researchspace.booking.service.BookingTargetUnavailableException;
import com.researchspace.booking.service.BookingWindowException;
import com.researchspace.booking.service.ConflictingEvent;
import com.researchspace.booking.service.TimeSlotBookingManager;
import com.researchspace.booking.service.TimeSlotBookingManager.Create;
import com.researchspace.booking.service.TimeSlotBookingManager.Patch;
import com.researchspace.inventory.model.ApiV2InstrumentResource;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookingEventKind;
import com.researchspace.model.booking.BookingRequesterFilters;
import com.researchspace.model.booking.BookingState;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.MessageSourceUtils;
import jakarta.ws.rs.NotFoundException;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.i18n.LocaleContextHolder;
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

  private final MessageSourceUtils messages;
  private final TimeSlotBookingManager manager;
  private final BookingCreateExecutor creates;
  private final FeatureFlagManager featureFlags;
  private final CollectionDescription<TimeSlotBooking> description;
  private final BookingLocationFilterManager locations;

  public TimeSlotBookingResourceOperations(
      TimeSlotBookingManager manager,
      FeatureFlagManager featureFlags,
      @Qualifier(BookingResourceAccessConfiguration.TIME_SLOT_BOOKING_DESCRIPTION)
          CollectionDescription<TimeSlotBooking> description,
      MessageSourceUtils messages,
      BookingLocationFilterManager locations,
      BookingCreateExecutor creates) {
    this.messages = messages;
    this.creates = creates;
    this.manager = manager;
    this.featureFlags = featureFlags;
    this.description = description;
    this.locations = locations;
  }

  @Bean
  ApiV2ResourceSpec<TimeSlotBooking, Long> timeSlotBookingApiV2Resource() {
    List<ApiV2ErrorMapping> commonErrors =
        List.of(
            ApiV2ErrorMapping.forException(BookingPolicyException.class)
                .status(HttpStatus.BAD_REQUEST)
                .documentedError(
                    "errors.api.v2.booking.granularity",
                    "The interval violates the scheduling policy.")
                .code(exception -> exception.reason().errorCode())
                .arguments(
                    exception -> {
                      Locale locale = LocaleContextHolder.getLocale();
                      if (exception.missingDayEnd().isPresent()) {
                        BookingPolicyException.MissingDayEnd missing =
                            exception.missingDayEnd().get();
                        return new Object[] {
                          missing.time().format(DateTimeFormatter.ofPattern("HH:mm")),
                          missing
                              .date()
                              .format(
                                  DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
                                      .withLocale(locale)),
                          missing.zone().getId()
                        };
                      }
                      return exception.maximumDurationMinutes().isEmpty()
                          ? new Object[0]
                          : new Object[] {
                            BookingDurationWords.format(
                                exception.maximumDurationMinutes().getAsLong(), messages, locale)
                          };
                    })
                .extensions(
                    exception ->
                        exception.maximumDurationMinutes().isEmpty()
                            ? Map.of()
                            : BookingProblem.maximumDuration(
                                exception.maximumDurationMinutes().getAsLong()))
                .build(),
            ApiV2ErrorMapping.of(
                BookingStartInPastException.class,
                HttpStatus.BAD_REQUEST,
                "errors.api.v2.booking.startInPast",
                "The booking start must be in the future."),
            ApiV2ErrorMapping.of(
                BookingWindowException.class,
                HttpStatus.BAD_REQUEST,
                "errors.api.v2.booking.window",
                "The booking interval is invalid."),
            ApiV2ErrorMapping.of(
                BookingDurationException.class,
                HttpStatus.BAD_REQUEST,
                "errors.api.v2.booking.duration",
                "The booking interval exceeds 527040 elapsed minutes."),
            ApiV2ErrorMapping.of(
                BookingTargetUnavailableException.class,
                HttpStatus.CONFLICT,
                "errors.api.v2.booking.target.unavailable",
                "The selected target is unavailable."),
            ApiV2ErrorMapping.forException(BookingOverlapException.class)
                .status(HttpStatus.CONFLICT)
                .documentedError(
                    "errors.api.v2.booking.overlap",
                    "The interval overlaps another booking or maintenance event. The problem's"
                        + " conflict member identifies the first such event.")
                .arguments(ignored -> new Object[0])
                .extensions(
                    exception -> BookingProblem.bookingConflict(conflict(exception.conflict())))
                .build(),
            ApiV2ErrorMapping.forException(BookingBufferConflictException.class)
                .status(HttpStatus.CONFLICT)
                .documentedError(
                    "errors.api.v2.booking.buffer",
                    "The interval is free but falls inside the buffer around another booking or"
                        + " maintenance event. The problem's conflict, bufferBeforeMinutes, and"
                        + " bufferAfterMinutes members describe it.")
                .arguments(ignored -> new Object[0])
                .extensions(
                    exception ->
                        BookingProblem.bookingBuffer(
                            conflict(exception.conflict()),
                            exception.bufferBeforeMinutes(),
                            exception.bufferAfterMinutes()))
                .build());
    List<ApiV2ErrorMapping> updateErrors =
        Stream.concat(
                commonErrors.stream(),
                Stream.of(
                    ApiV2ErrorMapping.of(
                        BookingStateTransitionException.class,
                        HttpStatus.CONFLICT,
                        "errors.api.v2.booking.state.transition",
                        "The requested state transition is invalid."),
                    ApiV2ErrorMapping.of(
                        BookingConcurrentModificationException.class,
                        HttpStatus.PRECONDITION_FAILED,
                        "errors.api.v2.booking.concurrentModification",
                        "The event changed while it was being edited.")))
            .toList();
    List<ApiV2ErrorMapping> createErrors =
        Stream.concat(
                commonErrors.stream(),
                Stream.of(
                    ApiV2ErrorMapping.of(
                        BookingIdempotencyConflictException.class,
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "errors.api.v2.booking.idempotencyKey.conflict",
                        "The Idempotency-Key was already used for a different booking request."),
                    ApiV2ErrorMapping.of(
                        BookingConcurrentModificationException.class,
                        HttpStatus.CONFLICT,
                        "errors.api.v2.booking.concurrentModification",
                        "The schedule changed while the booking was being created.")))
            .toList();
    return ApiV2ResourceSpec.builder(description, this, Long::valueOf)
        .mutationPolicy(
            new MutationPolicy(
                VersionPolicy.required(
                    "version", "errors.api.v2.booking.ifMatchRequired", ResourceOperation.UPDATE),
                DeletePolicy.HARD,
                IdempotencyPolicy.CREATOR_SCOPED))
        .mutationErrors("errors.api.v2.booking.create", "errors.api.v2.booking.patch")
        .expose(OPERATIONS)
        .documentation(
            ResourceOperation.CREATE,
            OpenApiOperationDocumentation.builder()
                .description(
                    "Creates one confirmed booking for an instrument. Intervals may not exceed the"
                        + " instrument's configured maximum or the 366-day system maximum. Optional"
                        + " Idempotency-Key (1-64 ASCII letters, digits, dot, underscore, colon or"
                        + " hyphen) is scoped to the creating actor. The normalized original"
                        + " request is retained for the booking row's lifetime. Identical retries"
                        + " return the current booking with 200, including after edits or"
                        + " cancellation; a different request returns 422.")
                .responseDescription(200, "Current booking returned for an identical create retry.")
                .requestExample(
                    Map.of(
                        "target",
                        Map.of("relationTo", ApiV2InstrumentResource.RESOURCE_NAME, "value", 123),
                        "start",
                        "2026-10-25T07:30:00Z",
                        "end",
                        "2026-10-25T09:00:00Z",
                        "purpose",
                        "Image plate 4"))
                .build())
        .errorMappings(ResourceOperation.CREATE, createErrors)
        .errorMappings(ResourceOperation.UPDATE, updateErrors)
        .audit(
            new ResourceAuditPolicy<>(
                (id, subject) ->
                    enabled(subject) ? manager.getBooking(id, subject) : Optional.empty(),
                manager::requireCanViewAudit,
                entity -> Map.of(),
                entity -> null,
                Set.of(),
                Set.of(),
                // The booking's audit capability is already checked by requireCanViewAudit.
                // People who book an item often share no group with its managers, so the
                // user-directory filter would hide events that the configuration audit shows.
                true))
        .tagDocumentation("Bookings, cancellations and maintenance reservations.", "Booking")
        .build();
  }

  @Override
  public ResourcePage<TimeSlotBooking> find(ResourceRequest request, User subject) {
    return enabled(subject)
        ? manager.getBookings(readableRequest(request, subject), subject)
        : new ResourcePage<>(List.of(), 0);
  }

  @Override
  public long count(ResourceRequest request, User subject) {
    return enabled(subject) ? manager.countBookings(readableRequest(request, subject), subject) : 0;
  }

  /** Resolves location rules against readable Containers and hides unseen requesters. */
  private ResourceRequest readableRequest(ResourceRequest request, User subject) {
    return locations.resolveLocations(visibleRequestersOnly(request, subject), subject);
  }

  /** A requester comparison may match only an event whose requester the response would show. */
  static ResourceRequest visibleRequestersOnly(ResourceRequest request, User subject) {
    FilterExpression filter = request.filter();
    if (filter == null) {
      return request;
    }
    return new ResourceRequest(
        BookingRequesterFilters.visibleRequestersOnly(filter, subject.getId()),
        request.serverConstraint(),
        request.sort(),
        request.page(),
        request.fieldSelections(),
        request.includes(),
        request.runtime());
  }

  @Override
  public Optional<TimeSlotBooking> findById(Long id, User subject) {
    return enabled(subject) ? manager.getBooking(id, subject) : Optional.empty();
  }

  public Optional<TimeSlotBooking> findByIdForAudit(Long id, User subject) {
    return enabled(subject) ? manager.getBooking(id, subject) : Optional.empty();
  }

  public void requireAuditAccess(TimeSlotBooking booking, User subject) {
    manager.requireCanViewAudit(booking, subject);
  }

  @Override
  public CreateResult<TimeSlotBooking> create(
      ParsedDocument document, ApiV2Caller caller, String key) {
    requireEnabled(caller.subject());
    if (key != null && !key.matches("[A-Za-z0-9._:-]{1,64}")) {
      throw ApiV2ResourceException.badRequest("errors.api.v2.booking.idempotencyKey.invalid");
    }
    if (key == null) {
      return new CreateResult<>(
          manager.createBooking(createCommand(document), caller.subject(), caller.actor()), false);
    }
    var result = creates.create(createCommand(document), key, caller.subject(), caller.actor());
    return new CreateResult<>(result.booking(), result.replayed());
  }

  @Override
  public Optional<TimeSlotBooking> update(
      Long id, ParsedDocument document, Long expectedVersion, ApiV2Caller caller) {
    requireEnabled(caller.subject());
    if (expectedVersion == null) {
      return manager.updateBooking(id, patchCommand(document), caller.subject(), caller.actor());
    }
    return manager.updateBooking(
        id, patchCommand(document), expectedVersion, caller.subject(), caller.actor());
  }

  private Create createCommand(ParsedDocument document) {
    return new Create(
        BookingDocumentValues.target(document, description),
        BookingDocumentValues.value(document, "start", Date.class),
        BookingDocumentValues.value(document, "end", Date.class),
        BookingDocumentValues.value(document, "purpose", String.class),
        BookingDocumentValues.value(document, "kind", BookingEventKind.class));
  }

  private static Patch patchCommand(ParsedDocument document) {
    return new Patch(
        BookingDocumentValues.value(document, "start", Date.class),
        BookingDocumentValues.value(document, "end", Date.class),
        document.changed("purpose"),
        BookingDocumentValues.value(document, "purpose", String.class),
        BookingDocumentValues.value(document, "state", BookingState.class));
  }

  private boolean enabled(User subject) {
    return featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, subject);
  }

  private void requireEnabled(User subject) {
    if (!enabled(subject)) {
      throw new NotFoundException();
    }
  }

  private static BookingProblem.BookingConflict conflict(ConflictingEvent event) {
    return new BookingProblem.BookingConflict(
        event.id(),
        event.kind().name(),
        DateTimeFormatter.ISO_INSTANT.format(event.start()),
        DateTimeFormatter.ISO_INSTANT.format(event.end()));
  }
}
