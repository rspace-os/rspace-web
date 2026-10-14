package com.researchspace.model.booking;

import static com.researchspace.model.collection.ApiV2ResourceField.AccessPreset.NEVER;

import com.researchspace.model.collection.AccessFunction;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.ApiV2ResourceDefinition;
import com.researchspace.model.collection.ApiV2ResourceField;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionFieldType;
import com.researchspace.model.collection.CollectionFieldTypes;
import com.researchspace.model.collection.CollectionMutationLimits;
import com.researchspace.model.collection.Field;
import com.researchspace.model.collection.OpenApiSchemaDocumentation;
import com.researchspace.model.collection.Relationship;
import com.researchspace.model.collection.RelationshipTarget;
import com.researchspace.model.collection.ResourceMember;
import com.researchspace.model.collection.ResourceReference;
import com.researchspace.model.collection.Sort;
import com.researchspace.model.collection.SplitReferenceBinding;
import com.researchspace.model.collection.WriteOperation;
import com.researchspace.model.resourceaccess.ResourceRoleSource;
import com.researchspace.model.resourceaccess.ResourceRoleSourceKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

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
            createAccess = NEVER,
            description = "Lifecycle state. Archive with DELETE and restore by PATCHing ACTIVE.")
        BookingConfigurationState state,
    @ApiV2ResourceField(
            property = "timeZone",
            updateAccess = NEVER,
            maxLength = 255,
            description =
                "Named IANA scheduling timezone used for browser-based opening hours and booking"
                    + " policy. Java-only IDs, numeric offsets, SystemV IDs, and"
                    + " browser-incompatible legacy aliases CST6CDT, EST5EDT, MST7MDT, and PST8PDT"
                    + " are not accepted. Set only on create; defaults to the institution"
                    + " timezone.",
            example = "Europe/Berlin")
        String timezone,
    @ApiV2ResourceField(
            description =
                "Booking time increment in minutes, from 1 to 527040. Bookings last a whole number"
                    + " of increments. For DAYS it is the day count times 1440 and must be a whole"
                    + " number of days. Defaults to the institution defaults on create.",
            example = "15")
        long slotGranularityMinutes,
    @ApiV2ResourceField(
            description =
                "Unit of the time increment. MINUTES and HOURS measure elapsed time. DAYS counts"
                    + " calendar days in the scheduling timezone, so a booking ends at its start's"
                    + " local time; it requires the item to be open 00:00 to 24:00 on all seven"
                    + " weekdays.",
            example = "MINUTES")
        BookingIncrementUnit incrementUnit,
    @ApiV2ResourceField(
            description =
                "Whether booking starts must fall on the increment counted from local midnight."
                    + " The stored value is ignored for DAYS and for increments of 1440 minutes or"
                    + " more.")
        boolean alignToIncrement,
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
        long configurationVersion) {

  public static final CollectionMutationLimits MUTATION_LIMITS =
      new CollectionMutationLimits(50, 1000);

  private static final Relationship<BookingConfiguration> TARGET =
      instrumentTarget(
              (BookingConfiguration configuration) -> {
                BookableTargetReference target = configuration.getTarget();
                return target == null ? null : new ResourceReference<>(target.type(), target.id());
              },
              "target.type",
              "target.id")
          .documented(
              OpenApiSchemaDocumentation.of(
                  "Booking target",
                  "Instrument to which this booking configuration applies.",
                  null));

  /** Creates the shared instrument relationship binding for booking resources. */
  static <T> Relationship<T> instrumentTarget(
      Function<T, ResourceReference<BookableTargetType, Long>> referenceReader,
      String typeProperty,
      String idProperty) {
    return Relationship.polymorphicToOne(
            "target",
            CollectionFieldTypes.longNumber(),
            List.of(
                new RelationshipTarget<>(
                    ApiV2BookingInstrumentResource.RESOURCE_NAME,
                    BookableTargetType.INSTRUMENT,
                    "IN",
                    com.researchspace.model.inventory.Instrument.class)),
            new SplitReferenceBinding<>(referenceReader, typeProperty, idProperty))
        .required()
        .writeOnlyOn(WriteOperation.CREATE);
  }

  private record RoleSourceGrantee(String kind, String key, Object id, String name) {
    static RoleSourceGrantee from(ResourceRoleSource source) {
      Map<String, Object> grantee = source.grantee();
      return grantee == null
          ? null
          : new RoleSourceGrantee(
              (String) grantee.get("kind"),
              (String) grantee.get("key"),
              grantee.get("id"),
              (String) grantee.get("name"));
    }
  }

  private static final CollectionFieldType<Object> GRANTEE_ID_TYPE = new GranteeIdFieldType();

  private static final CollectionFieldType<RoleSourceGrantee> GRANTEE_TYPE =
      CollectionFieldTypes.object(
          RoleSourceGrantee.class,
          List.of(
              Field.embedded("kind", CollectionFieldTypes.text(), RoleSourceGrantee::kind)
                  .required(),
              Field.embedded("key", CollectionFieldTypes.text(), RoleSourceGrantee::key).required(),
              Field.embedded("id", GRANTEE_ID_TYPE, RoleSourceGrantee::id).required(),
              Field.embedded("name", CollectionFieldTypes.text(), RoleSourceGrantee::name)
                  .required()),
          values ->
              new RoleSourceGrantee(
                  (String) values.get("kind"),
                  (String) values.get("key"),
                  values.get("id"),
                  (String) values.get("name")));

  private static final CollectionFieldType<List<ResourceRoleSource>> ROLE_SOURCES_TYPE =
      CollectionFieldTypes.array(
          ResourceRoleSource.class,
          List.of(
              Field.embedded(
                      "kind",
                      CollectionFieldTypes.enumeration(ResourceRoleSourceKind.class),
                      ResourceRoleSource::kind)
                  .required(),
              Field.embedded("role", CollectionFieldTypes.text(), ResourceRoleSource::roleKey)
                  .required(),
              Field.embedded("grantee", GRANTEE_TYPE, RoleSourceGrantee::from)),
          values -> {
            RoleSourceGrantee grantee = (RoleSourceGrantee) values.get("grantee");
            return new ResourceRoleSource(
                (ResourceRoleSourceKind) values.get("kind"),
                (String) values.get("role"),
                grantee == null ? null : grantee.key(),
                grantee == null ? null : grantee.name());
          });

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
    CollectionDescription<BookingConfiguration> base =
        CollectionDescription.fromApiV2Resource(
            ApiV2BookingConfigurationResource.class,
            BookingConfiguration.class,
            List.of(TARGET, ApiV2BookingLocationResource.<BookingConfiguration>relationship()),
            List.of(new Sort("id", true)),
            access);
    List<Field<BookingConfiguration, ?>> fields = new ArrayList<>(base.fields());
    int afterAlignment =
        fields.indexOf(
                fields.stream()
                    .filter(field -> field.name().equals("alignToIncrement"))
                    .findFirst()
                    .orElseThrow())
            + 1;
    fields.add(
        afterAlignment,
        Field.writable(
                "openingHours",
                "openingHours",
                BookingOpeningHoursCodec.TYPE,
                BookingConfiguration::getOpeningHours,
                BookingConfiguration::setOpeningHours)
            .documented(
                OpenApiSchemaDocumentation.of(
                    null,
                    "Independent interval for each open ISO weekday (1 = Monday to 7 = Sunday)."
                        + " Entries are returned in weekday order; omitted weekdays are closed."
                        + " Replaces the entire schedule and defaults to institution settings on"
                        + " create.",
                    null))
            .withQueryCapabilities(false, false));
    fields.add(
        Field.readOnly(
                "createdByName",
                "createdByName",
                CollectionFieldTypes.text(),
                BookingConfiguration::getCreatedByName)
            .allowNull()
            .withQueryCapabilities(false, false));
    fields.add(
        Field.readOnly(
                "effectiveRole",
                "effectiveRole",
                CollectionFieldTypes.text(64),
                BookingConfiguration::getEffectiveRole)
            .allowNull()
            .withQueryCapabilities(false, false));
    fields.add(
        Field.readOnly(
                "roleSources",
                "roleSources",
                ROLE_SOURCES_TYPE,
                BookingConfiguration::getRoleSources)
            .withQueryCapabilities(false, false));
    fields.add(
        Field.readOnly(
                "capabilities",
                "capabilities",
                CollectionFieldTypes.object(),
                BookingConfiguration::getCapabilities)
            .withQueryCapabilities(false, false));
    fields.add(
        Field.readOnly(
                "ownerHealth",
                "ownerHealth",
                CollectionFieldTypes.object(),
                BookingConfiguration::getOwnerHealth)
            .readableBy(AccessFunction.sysadmin())
            .withQueryCapabilities(false, false));
    List<ResourceMember<BookingConfiguration>> members = new ArrayList<>(fields);
    members.addAll(base.relationships());
    return new CollectionDescription<>(
        base.resourceName(),
        BookingConfiguration.class,
        members,
        base.idField(),
        base.defaultSort(),
        access,
        List.of());
  }
}
