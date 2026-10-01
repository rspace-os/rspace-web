package com.researchspace.model.booking;

import static com.researchspace.model.collection.ApiV2ResourceField.AccessPreset.NEVER;

import com.researchspace.model.collection.AccessFunction;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.ApiV2RelationshipTarget;
import com.researchspace.model.collection.ApiV2ResourceDefinition;
import com.researchspace.model.collection.ApiV2ResourceField;
import com.researchspace.model.collection.ApiV2ResourceMember;
import com.researchspace.model.collection.ApiV2ResourceRelationship;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionFieldTypes;
import com.researchspace.model.collection.CollectionMutationLimits;
import com.researchspace.model.collection.Field;
import com.researchspace.model.collection.ResourceReference;
import com.researchspace.model.collection.Sort;
import com.researchspace.model.collection.WriteOperation;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.resourceaccess.ResourceRoleSource;
import java.util.List;
import java.util.Map;

@ApiV2ResourceDefinition(
    name = "booking-configurations",
    entity = BookingConfiguration.class,
    id = "id",
    auditFields = true)
public record ApiV2BookingConfigurationResource(
    @ApiV2ResourceField(description = "Stable booking-configuration identifier.", example = "7")
        Long id,
    @ApiV2ResourceRelationship(
            relationTo =
                @ApiV2RelationshipTarget(
                    resource = ApiV2BookingInstrumentResource.RESOURCE_NAME,
                    entity = Instrument.class,
                    kind = "INSTRUMENT",
                    globalIdPrefix = "IN"),
            kindProperty = "target.type",
            requiredOnCreate = true,
            writeOn = WriteOperation.CREATE,
            title = "Booking target",
            description = "Instrument to which this booking configuration applies.")
        ResourceReference<BookableTargetType, Long> target,
    @ApiV2ResourceField(description = "Whether bookings are enabled for the target.")
        boolean enabled,
    @ApiV2ResourceField(
            createAccess = NEVER,
            description = "Lifecycle state. Archive with DELETE and restore by PATCHing ACTIVE.")
        BookingConfigurationState state,
    @ApiV2ResourceField(
            property = "timeZone",
            createAccess = NEVER,
            updateAccess = NEVER,
            maxLength = 255,
            description =
                "Read-only scheduling timezone used for opening hours and booking policy.",
            example = "Europe/Berlin")
        String timezone,
    @ApiV2ResourceField(
            description = "Allowed wall-clock booking increment in minutes.",
            example = "5")
        long slotGranularityMinutes,
    @ApiV2ResourceField(
            description = "Inclusive daily opening time in the configured time zone.",
            example = "08:00")
        String openingStart,
    @ApiV2ResourceField(
            description = "Daily closing time, where 24:00 means the next local midnight.",
            example = "18:00")
        String openingEnd,
    @ApiV2ResourceField(description = "Unavailable minutes before a booking.", example = "15")
        long bufferBeforeMinutes,
    @ApiV2ResourceField(description = "Unavailable minutes after a booking.", example = "15")
        long bufferAfterMinutes,
    @ApiV2ResourceField(
            description =
                "Maximum elapsed minutes for one booking, where 0 disables the item limit.",
            example = "120")
        long maxBookingDurationMinutes,
    @ApiV2ResourceField(description = "Whether overlapping bookings are permitted.")
        boolean allowDoubleBooking,
    @ApiV2ResourceField(
            createAccess = NEVER,
            updateAccess = NEVER,
            description = "Optimistic configuration revision.")
        long configurationVersion,
    @ApiV2ResourceMember(factory = "createdByNameField") String createdByName,
    @ApiV2ResourceMember(factory = "effectiveRoleField") String effectiveRole,
    @ApiV2ResourceMember(factory = "roleSourcesField") List<ResourceRoleSource> roleSources,
    @ApiV2ResourceMember(factory = "capabilitiesField") Map<String, Object> capabilities,
    @ApiV2ResourceMember(factory = "ownerHealthField") Map<String, Object> ownerHealth) {

  public static final CollectionMutationLimits MUTATION_LIMITS =
      new CollectionMutationLimits(50, 1000);

  /** Stable shape used by isolated metadata tests; production injects {@link #description}. */
  public static final CollectionDescription<BookingConfiguration> DESCRIPTION =
      description(AccessFunction.authenticated());

  /** Builds the collection with its registered resource-role read policy. */
  public static CollectionDescription<BookingConfiguration> description(AccessFunction readAccess) {
    AccessPolicy access =
        new AccessPolicy(
            readAccess,
            AccessFunction.authenticated(),
            AccessFunction.authenticated(),
            AccessFunction.authenticated(),
            AccessFunction.authenticated());
    return CollectionDescription.fromApiV2Resource(
        ApiV2BookingConfigurationResource.class,
        BookingConfiguration.class,
        List.of(new Sort("id", true)),
        access);
  }

  private static Field<BookingConfiguration, ?> createdByNameField() {
    return Field.readOnly(
            "createdByName",
            "createdByName",
            CollectionFieldTypes.text(),
            BookingConfiguration::getCreatedByName)
        .allowNull()
        .withQueryCapabilities(false, false);
  }

  private static Field<BookingConfiguration, ?> effectiveRoleField() {
    return Field.readOnly(
            "effectiveRole",
            "effectiveRole",
            CollectionFieldTypes.text(64),
            BookingConfiguration::getEffectiveRole)
        .allowNull()
        .withQueryCapabilities(false, false);
  }

  private static Field<BookingConfiguration, ?> roleSourcesField() {
    return Field.readOnly(
            "roleSources",
            "roleSources",
            CollectionFieldTypes.array(),
            BookingConfiguration::getRoleSources)
        .withQueryCapabilities(false, false);
  }

  private static Field<BookingConfiguration, ?> capabilitiesField() {
    return Field.readOnly(
            "capabilities",
            "capabilities",
            CollectionFieldTypes.object(),
            BookingConfiguration::getCapabilities)
        .withQueryCapabilities(false, false);
  }

  private static Field<BookingConfiguration, ?> ownerHealthField() {
    return Field.readOnly(
            "ownerHealth",
            "ownerHealth",
            CollectionFieldTypes.object(),
            BookingConfiguration::getOwnerHealth)
        .readableBy(AccessFunction.sysadmin())
        .withQueryCapabilities(false, false);
  }
}
