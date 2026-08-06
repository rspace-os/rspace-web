package com.researchspace.booking.api.v2;

import com.researchspace.api.v2.resource.ApiV2ErrorMapping;
import com.researchspace.api.v2.resource.ApiV2ResourceSpec;
import com.researchspace.api.v2.resource.OpenApiOperationDocumentation;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.api.v2.resource.ResourceOperations;
import com.researchspace.booking.service.BookingConfigurationManager;
import com.researchspace.booking.service.BookingConfigurationManager.Create;
import com.researchspace.booking.service.BookingConfigurationManager.Patch;
import com.researchspace.booking.service.BookingConfigurationTargetConflictException;
import com.researchspace.booking.service.InvalidBookableTargetException;
import com.researchspace.model.User;
import com.researchspace.model.booking.ApiV2BookingConfigurationResource;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.ResolvedBookableTarget;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.RelationshipTarget;
import com.researchspace.model.collection.ResolvedResourceReference;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceReference;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.inventory.InventoryRecord;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

/** Adapts REST v2 booking-configuration documents to the shared booking manager. */
@Configuration(proxyBeanMethods = false)
public final class BookingConfigurationResourceOperations
    implements ResourceOperations<BookingConfiguration, Long> {

  private final BookingConfigurationManager manager;

  public BookingConfigurationResourceOperations(BookingConfigurationManager manager) {
    this.manager = manager;
  }

  @Bean
  ApiV2ResourceSpec<BookingConfiguration, Long> bookingConfigurationApiV2Resource() {
    List<ApiV2ErrorMapping> writeErrors =
        List.of(
            ApiV2ErrorMapping.of(
                InvalidBookableTargetException.class,
                HttpStatus.BAD_REQUEST,
                "errors.api.v2.bookingConfiguration.target.invalid",
                "The target is not an eligible instrument."),
            ApiV2ErrorMapping.of(
                BookingConfigurationTargetConflictException.class,
                HttpStatus.CONFLICT,
                "errors.api.v2.bookingConfiguration.target.conflict",
                "The instrument already has a booking configuration."));
    return new ApiV2ResourceSpec<>(
        ApiV2BookingConfigurationResource.DESCRIPTION,
        this,
        Long::valueOf,
        "errors.api.v2.bookingConfiguration.create",
        "errors.api.v2.bookingConfiguration.patch",
        EnumSet.allOf(ResourceOperation.class),
        Map.of(
            ResourceOperation.CREATE,
            OpenApiOperationDocumentation.builder()
                .description(
                    "Creates booking configuration for an instrument. Each instrument may have "
                        + "only one booking configuration.")
                .requestExample(
                    Map.of(
                        "enabled",
                        true,
                        "timezone",
                        "Europe/Berlin",
                        "target",
                        Map.of("relationTo", "instruments", "value", 123)))
                .build()),
        Map.of(
            ResourceOperation.CREATE,
            writeErrors,
            ResourceOperation.BULK_CREATE,
            writeErrors,
            ResourceOperation.UPDATE,
            writeErrors,
            ResourceOperation.BULK_UPDATE,
            writeErrors));
  }

  @Override
  public ResourcePage<BookingConfiguration> find(ResourceRequest request) {
    return manager.getConfigurations(request);
  }

  @Override
  public long count(ResourceRequest request) {
    return manager.countConfigurations(request);
  }

  @Override
  public Optional<BookingConfiguration> findById(Long id) {
    return manager.getConfiguration(id);
  }

  @Override
  public BookingConfiguration create(ParsedDocument document, User actor) {
    return manager.createConfiguration(create(document), actor);
  }

  @Override
  public List<BookingConfiguration> createMany(List<ParsedDocument> documents, User actor) {
    return manager.createConfigurations(documents.stream().map(this::create).toList(), actor);
  }

  @Override
  public Optional<BookingConfiguration> update(Long id, ParsedDocument document, User actor) {
    return manager.updateConfiguration(id, patch(document), actor);
  }

  @Override
  public List<BookingConfiguration> updateMany(
      ResourceRequest request, ParsedDocument document, User actor) {
    return manager.updateConfigurations(request, patch(document), actor);
  }

  @Override
  public Optional<BookingConfiguration> delete(Long id, User actor) {
    return manager.removeConfiguration(id, actor);
  }

  @Override
  public List<BookingConfiguration> deleteMany(ResourceRequest request, User actor) {
    return manager.removeConfigurations(request, actor);
  }

  private static Patch patch(ParsedDocument document) {
    return new Patch(value(document, "enabled"), value(document, "timezone"), target(document));
  }

  private Create create(ParsedDocument document) {
    return new Create(
        (boolean) document.values().getOrDefault("enabled", false),
        value(document, "timezone"),
        target(document));
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
        ApiV2BookingConfigurationResource.DESCRIPTION
            .requireRelationship("target")
            .targetForKind(type);
    Object selectedEntity = resolved.entityAs(metadata.entityType());
    InventoryRecord entity = InventoryRecord.class.cast(selectedEntity);
    return new ResolvedBookableTarget(new BookableTargetReference(type, id), entity);
  }

  @SuppressWarnings("unchecked")
  private static <T> T value(ParsedDocument document, String field) {
    return (T) document.values().get(field);
  }
}
