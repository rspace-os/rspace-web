package com.researchspace.booking.api.v2;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.api.v2.resource.ApiV2ErrorMapping;
import com.researchspace.api.v2.resource.ApiV2ResourceSpec;
import com.researchspace.api.v2.resource.CreateResult;
import com.researchspace.api.v2.resource.DeletePolicy;
import com.researchspace.api.v2.resource.IdempotencyPolicy;
import com.researchspace.api.v2.resource.MutationPolicy;
import com.researchspace.api.v2.resource.OpenApiOperationDocumentation;
import com.researchspace.api.v2.resource.ResourceAccessSpec;
import com.researchspace.api.v2.resource.ResourceAuditPolicy;
import com.researchspace.api.v2.resource.ResourceDeleteOptions;
import com.researchspace.api.v2.resource.ResourceDeleteResult;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.api.v2.resource.ResourceOperations;
import com.researchspace.api.v2.resource.VersionPolicy;
import com.researchspace.booking.config.BookingResourceAccessConfiguration;
import com.researchspace.booking.config.BookingTimeConfig;
import com.researchspace.booking.service.BookingConcurrentModificationException;
import com.researchspace.booking.service.BookingConfigurationLifecycleException;
import com.researchspace.booking.service.BookingConfigurationManager;
import com.researchspace.booking.service.BookingConfigurationManager.Create;
import com.researchspace.booking.service.BookingConfigurationManager.Patch;
import com.researchspace.booking.service.BookingConfigurationProtectedResourceAccess;
import com.researchspace.booking.service.BookingConfigurationTargetConflictException;
import com.researchspace.booking.service.BookingLocationFilterManager;
import com.researchspace.booking.service.InvalidBookableTargetException;
import com.researchspace.booking.service.InvalidBookingSchedulingSettingsException;
import com.researchspace.inventory.model.ApiV2InstrumentResource;
import com.researchspace.model.User;
import com.researchspace.model.booking.ApiV2BookingConfigurationResource;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingConfigurationState;
import com.researchspace.model.booking.BookingIncrementUnit;
import com.researchspace.model.booking.BookingOpeningHoursEntry;
import com.researchspace.model.booking.BookingSchedulingSettings;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.service.FeatureFlagManager;
import jakarta.ws.rs.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

/** Adapts REST v2 booking-configuration documents to the shared booking manager. */
@Configuration(proxyBeanMethods = false)
public final class BookingConfigurationResourceOperations
    implements ResourceOperations<BookingConfiguration, Long> {

  private final BookingConfigurationManager manager;
  private final BookingLocationFilterManager locations;
  private final BookingConfigurationProtectedResourceAccess protectedResourceAccess;
  private final FeatureFlagManager featureFlags;
  private final Clock institutionClock;
  private final CollectionDescription<BookingConfiguration> description;

  public BookingConfigurationResourceOperations(
      BookingConfigurationManager manager,
      BookingLocationFilterManager locations,
      BookingConfigurationProtectedResourceAccess protectedResourceAccess,
      FeatureFlagManager featureFlags,
      @Qualifier(BookingTimeConfig.INSTITUTION_CLOCK) Clock institutionClock,
      @Qualifier(BookingResourceAccessConfiguration.BOOKING_CONFIGURATION_DESCRIPTION)
          CollectionDescription<BookingConfiguration> description) {
    this.manager = manager;
    this.locations = locations;
    this.protectedResourceAccess = protectedResourceAccess;
    this.featureFlags = featureFlags;
    this.institutionClock = institutionClock;
    this.description = description;
  }

  @Bean
  ApiV2ResourceSpec<BookingConfiguration, Long> bookingConfigurationApiV2Resource() {
    List<ApiV2ErrorMapping> writeErrors =
        List.of(
            ApiV2ErrorMapping.forException(InvalidBookingSchedulingSettingsException.class)
                .status(HttpStatus.BAD_REQUEST)
                .documentedError(
                    "errors.api.v2.bookingConfiguration.granularity.invalid",
                    "The scheduling settings are invalid.")
                .code(exception -> exception.reason().errorCode())
                .build(),
            ApiV2ErrorMapping.forException(InvalidBookableTargetException.class)
                .status(HttpStatus.BAD_REQUEST)
                .documentedError(
                    "errors.api.v2.bookingConfiguration.target.invalid",
                    "The target is not an eligible instrument.")
                .build(),
            ApiV2ErrorMapping.forException(BookingConfigurationTargetConflictException.class)
                .status(HttpStatus.CONFLICT)
                .documentedError(
                    "errors.api.v2.bookingConfiguration.target.conflict",
                    "The instrument already has a booking configuration.")
                .build());
    List<ApiV2ErrorMapping> updateErrors =
        Stream.concat(
                writeErrors.stream(),
                Stream.of(
                    ApiV2ErrorMapping.forException(BookingConcurrentModificationException.class)
                        .status(HttpStatus.PRECONDITION_FAILED)
                        .documentedError(
                            "errors.api.v2.bookingConfiguration.concurrentModification",
                            "The configuration changed while it was being edited.")
                        .build(),
                    ApiV2ErrorMapping.forException(BookingConfigurationLifecycleException.class)
                        .status(HttpStatus.CONFLICT)
                        .documentedError(
                            "errors.api.v2.bookingConfiguration.lifecycleConflict",
                            "Restore an archived configuration before changing it.")
                        .build()))
            .toList();
    return ApiV2ResourceSpec.builder(description, this, Long::valueOf)
        .mutationPolicy(
            new MutationPolicy(
                VersionPolicy.required(
                    "configurationVersion",
                    "errors.api.v2.bookingConfiguration.ifMatchRequired",
                    ResourceOperation.UPDATE,
                    ResourceOperation.DELETE),
                DeletePolicy.SOFT.withPermanentOption(),
                IdempotencyPolicy.NONE))
        .mutationErrors(
            "errors.api.v2.bookingConfiguration.create", "errors.api.v2.bookingConfiguration.patch")
        .expose(EnumSet.range(ResourceOperation.LIST, ResourceOperation.BULK_DELETE))
        .documentation(
            ResourceOperation.LIST,
            OpenApiOperationDocumentation.builder()
                .description(
                    "Lists readable configurations on undeleted targets. The where member"
                        + " targetOwner accepts only ==me outside OR groups, resolves to the"
                        + " effective subject on each request, and includes the Inventory read"
                        + " constraint.")
                .build())
        .documentation(
            ResourceOperation.CREATE,
            OpenApiOperationDocumentation.builder()
                .description(
                    "Creates booking configuration for an instrument. Each instrument may have "
                        + "only one booking configuration.")
                .requestExample(
                    Map.of(
                        "enabled",
                        true,
                        "slotGranularityMinutes",
                        15,
                        "incrementUnit",
                        "MINUTES",
                        "openingHours",
                        List.of(Map.of("dayOfWeek", 1, "start", "08:00", "end", "18:00")),
                        "bufferBeforeMinutes",
                        15,
                        "bufferAfterMinutes",
                        15,
                        "maxBookingDurationMinutes",
                        120,
                        "allowDoubleBooking",
                        false,
                        "target",
                        Map.of("relationTo", ApiV2InstrumentResource.RESOURCE_NAME, "value", 123)))
                .build())
        .errorMappings(ResourceOperation.CREATE, writeErrors)
        .errorMappings(ResourceOperation.BULK_CREATE, writeErrors)
        .errorMappings(ResourceOperation.UPDATE, updateErrors)
        .errorMappings(ResourceOperation.DELETE, updateErrors)
        .errorMappings(
            ResourceOperation.BULK_UPDATE,
            Stream.concat(
                    writeErrors.stream(),
                    updateErrors.stream()
                        .filter(
                            error ->
                                error.exceptionType()
                                    == BookingConfigurationLifecycleException.class))
                .toList())
        .mutationLimits(ApiV2BookingConfigurationResource.MUTATION_LIMITS)
        .resourceAccess(new ResourceAccessSpec<>(protectedResourceAccess))
        .audit(
            new ResourceAuditPolicy<>(
                (id, actor) ->
                    enabled(actor) ? manager.getConfigurationForAudit(id, actor) : Optional.empty(),
                (configuration, actor) -> {},
                configuration ->
                    Map.of(
                        "target.type",
                        configuration.getTarget().type().name(),
                        "target.id",
                        configuration.getTarget().id().toString()),
                configuration ->
                    configuration.getCreatedAt() == null
                        ? null
                        : configuration.getCreatedAt().toInstant(),
                Set.of(
                    "bookingConfigurationId",
                    "start",
                    "end",
                    "kind",
                    "state",
                    "purpose",
                    "deleted",
                    "deletedAt"),
                Set.of("bookingConfigurationId"),
                true))
        .tagDocumentation(
            "Bookable resource configurations and related subscription settings.", "Booking")
        .build();
  }

  @Override
  public ResourcePage<BookingConfiguration> find(ResourceRequest request, User actor) {
    if (!enabled(actor)) {
      return new ResourcePage<>(List.of(), 0);
    }
    return manager.getConfigurations(locations.resolveLocations(request, actor), actor);
  }

  @Override
  public long count(ResourceRequest request, User actor) {
    if (!enabled(actor)) {
      return 0;
    }
    return manager.countConfigurations(locations.resolveLocations(request, actor), actor);
  }

  @Override
  public Optional<BookingConfiguration> findById(Long id, User actor) {
    if (!enabled(actor)) {
      return Optional.empty();
    }
    return manager.getConfiguration(id, actor);
  }

  public Map<String, String> requiredAuditFields(BookingConfiguration configuration) {
    return Map.of(
        "target.type",
        configuration.getTarget().type().name(),
        "target.id",
        configuration.getTarget().id().toString());
  }

  public Optional<BookingConfiguration> findByIdForAudit(Long id, User actor) {
    return enabled(actor) ? manager.getConfigurationForAudit(id, actor) : Optional.empty();
  }

  public Instant relatedAuditFallbackFrom(BookingConfiguration configuration) {
    return configuration.getCreatedAt() == null ? null : configuration.getCreatedAt().toInstant();
  }

  public Set<String> relatedAuditFields() {
    return Set.of(
        "bookingConfigurationId",
        "start",
        "end",
        "kind",
        "state",
        "purpose",
        "deleted",
        "deletedAt");
  }

  public Set<String> relatedAuditIdentifierFields() {
    return Set.of("bookingConfigurationId");
  }

  public boolean auditBypassesActorDirectory() {
    return true;
  }

  @Override
  public CreateResult<BookingConfiguration> create(
      ParsedDocument document, ApiV2Caller caller, String idempotencyKey) {
    if (!enabled(caller.subject())) {
      throw new NotFoundException();
    }
    return new CreateResult<>(
        manager.createConfiguration(create(document), caller.subject(), caller.actor()), false);
  }

  @Override
  public List<BookingConfiguration> createMany(List<ParsedDocument> documents, ApiV2Caller caller) {
    if (!enabled(caller.subject())) {
      throw new NotFoundException();
    }
    return manager.createConfigurations(
        documents.stream().map(this::create).toList(), caller.subject(), caller.actor());
  }

  @Override
  public Optional<BookingConfiguration> update(
      Long id, ParsedDocument document, Long expectedVersion, ApiV2Caller caller) {
    if (!enabled(caller.subject())) {
      throw new NotFoundException();
    }
    if (expectedVersion == null) {
      return manager.updateConfiguration(id, patch(document), caller.subject(), caller.actor());
    }
    return manager.updateConfiguration(
        id, patch(document), expectedVersion, caller.subject(), caller.actor());
  }

  @Override
  public List<BookingConfiguration> updateMany(
      ResourceRequest request, ParsedDocument document, ApiV2Caller caller) {
    if (!enabled(caller.subject())) {
      throw new NotFoundException();
    }
    return manager.updateConfigurations(
        locations.resolveLocations(request, caller.subject()),
        patch(document),
        caller.subject(),
        caller.actor());
  }

  @Override
  public Optional<ResourceDeleteResult<BookingConfiguration>> delete(
      Long id, ResourceDeleteOptions options, ApiV2Caller caller) {
    if (!enabled(caller.subject())) {
      throw new NotFoundException();
    }
    long expectedVersion =
        Objects.requireNonNull(options.expectedVersion(), "Expected configuration version");
    if (options.permanent()) {
      if (caller.isDelegated()) {
        throw new AuthorizationException("errors.api.v2.forbidden");
      }
      return manager
          .permanentlyDeleteConfiguration(id, expectedVersion, caller.subject(), caller.actor())
          .map(ignored -> ResourceDeleteResult.permanentlyDeleted());
    }
    return manager
        .archiveConfiguration(id, expectedVersion, caller.subject(), caller.actor())
        .map(ResourceDeleteResult::retained);
  }

  @Override
  public List<BookingConfiguration> deleteMany(ResourceRequest request, ApiV2Caller caller) {
    if (!enabled(caller.subject())) {
      throw new NotFoundException();
    }
    return manager.archiveConfigurations(
        locations.resolveLocations(request, caller.subject()), caller.subject(), caller.actor());
  }

  private boolean enabled(User actor) {
    return featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, actor);
  }

  private static Patch patch(ParsedDocument document) {
    return new Patch(
        BookingDocumentValues.value(document, "enabled", Boolean.class),
        null,
        schedulingPatch(document),
        BookingDocumentValues.value(document, "state", BookingConfigurationState.class));
  }

  private Create create(ParsedDocument document) {
    return new Create(
        (boolean) document.values().getOrDefault("enabled", true),
        institutionClock.getZone().getId(),
        BookingDocumentValues.target(document, description),
        schedulingPatch(document));
  }

  private static BookingSchedulingSettings.Patch schedulingPatch(ParsedDocument document) {
    return new BookingSchedulingSettings.Patch(
        BookingDocumentValues.value(document, "slotGranularityMinutes", Long.class),
        BookingDocumentValues.value(document, "incrementUnit", BookingIncrementUnit.class),
        openingHours(document),
        BookingDocumentValues.value(document, "bufferBeforeMinutes", Long.class),
        BookingDocumentValues.value(document, "bufferAfterMinutes", Long.class),
        BookingDocumentValues.value(document, "maxBookingDurationMinutes", Long.class),
        BookingDocumentValues.value(document, "allowDoubleBooking", Boolean.class));
  }

  private static List<BookingOpeningHoursEntry> openingHours(ParsedDocument document) {
    Object value = document.values().get("openingHours");
    return value == null
        ? null
        : List.class.cast(value).stream().map(BookingOpeningHoursEntry.class::cast).toList();
  }
}
