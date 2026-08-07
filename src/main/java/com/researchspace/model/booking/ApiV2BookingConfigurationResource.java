package com.researchspace.model.booking;

import static com.researchspace.model.collection.ApiV2ResourceField.AccessPreset.NEVER;

import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.ApiV2ResourceDefinition;
import com.researchspace.model.collection.ApiV2ResourceField;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionDescription.Sort;
import com.researchspace.model.collection.CollectionDescription.WriteOperation;
import com.researchspace.model.collection.CollectionFieldTypes;
import com.researchspace.model.collection.OpenApiSchemaDocumentation;
import com.researchspace.model.collection.RelationshipTarget;
import com.researchspace.model.collection.ResourceReference;
import com.researchspace.model.collection.SplitReferenceBinding;
import com.researchspace.model.inventory.Instrument;
import java.util.List;

@ApiV2ResourceDefinition(
    name = "booking-configurations",
    entity = BookingConfiguration.class,
    id = "id",
    auditFields = true)
public record ApiV2BookingConfigurationResource(
    @ApiV2ResourceField(description = "Stable booking-configuration identifier.", example = "7")
        Long id,
    @ApiV2ResourceField(description = "Whether bookings are enabled for the target.")
        boolean enabled,
    @ApiV2ResourceField(
            property = "timeZone",
            requiredOnCreate = true,
            maxLength = 255,
            description = "IANA time-zone identifier used for booking windows.",
            example = "Europe/Berlin")
        String timezone,
    @ApiV2ResourceField(
            createAccess = NEVER,
            updateAccess = NEVER,
            description = "Optimistic configuration revision.")
        long configurationVersion) {

  private static final AccessPolicy ACCESS = AccessPolicy.authenticatedReadsSysadminWrites();

  private static final CollectionDescription.Relationship<BookingConfiguration> TARGET =
      CollectionDescription.Relationship.polymorphicToOne(
              "target",
              CollectionFieldTypes.longNumber(),
              List.of(
                  new RelationshipTarget<>(
                      "instruments", BookableTargetType.INSTRUMENT, "IN", Instrument.class)),
              new SplitReferenceBinding<>(
                  (BookingConfiguration configuration) ->
                      configuration.getTarget() == null
                          ? null
                          : new ResourceReference<>(
                              configuration.getTarget().type(), configuration.getTarget().id()),
                  "target.type",
                  "target.id"))
          .required()
          .acceptGlobalIdOn(WriteOperation.UPDATE)
          .documented(
              new OpenApiSchemaDocumentation(
                  "Booking target",
                  "Instrument to which this booking configuration applies.",
                  null,
                  null,
                  null,
                  null,
                  List.of(),
                  false));

  public static final CollectionDescription<BookingConfiguration> DESCRIPTION =
      CollectionDescription.fromApiV2Resource(
          ApiV2BookingConfigurationResource.class,
          BookingConfiguration.class,
          List.of(TARGET),
          List.of(new Sort("id", true)),
          ACCESS);
}
