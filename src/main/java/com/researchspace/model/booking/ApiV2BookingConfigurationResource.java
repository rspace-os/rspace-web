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
import com.researchspace.model.collection.ResourceReference;
import com.researchspace.model.collection.Sort;
import com.researchspace.model.collection.SplitReferenceBinding;
import com.researchspace.model.collection.WriteOperation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    @ApiV2ResourceField(description = "Whether bookings are enabled for the target.")
        boolean enabled,
    @ApiV2ResourceField(
            createAccess = NEVER,
            description = "Lifecycle state. Archive with DELETE and restore by PATCHing ACTIVE.",
            enumValues = {"ACTIVE", "ARCHIVED"})
        BookingConfigurationState state,
    @ApiV2ResourceField(
            property = "timeZone",
            updateAccess = NEVER,
            maxLength = 255,
            description =
                "IANA scheduling timezone used for opening hours and booking policy. Set only on"
                    + " create; defaults to the institution timezone.",
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
        long configurationVersion) {

  public static final CollectionMutationLimits MUTATION_LIMITS =
      new CollectionMutationLimits(50, 1000);

  private static final Relationship<BookingConfiguration> TARGET =
      Relationship.polymorphicToOne(
              "target",
              CollectionFieldTypes.longNumber(),
              List.of(
                  new RelationshipTarget<>(
                      ApiV2BookingInstrumentResource.RESOURCE_NAME,
                      BookableTargetType.INSTRUMENT,
                      "IN",
                      com.researchspace.model.inventory.Instrument.class)),
              new SplitReferenceBinding<>(
                  (BookingConfiguration configuration) ->
                      configuration.getTarget() == null
                          ? null
                          : new ResourceReference<>(
                              configuration.getTarget().type(), configuration.getTarget().id()),
                  "target.type",
                  "target.id"))
          .required()
          .writeOnlyOn(WriteOperation.CREATE)
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

  private static final String WALL_TIME_PATTERN = "^(?:[01]\\d|2[0-3]):[0-5]\\d$";
  private static final String CLOSING_TIME_PATTERN = "^(?:(?:[01]\\d|2[0-3]):[0-5]\\d|24:00)$";

  private static final Map<String, Object> WEEKDAY_SCHEMA =
      ordered("type", "integer", "minimum", 1, "maximum", 7);

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static final CollectionFieldType<List<Integer>> OPEN_DAYS_TYPE =
      CollectionFieldTypes.structured(
          (Class) List.class,
          CollectionFieldType.InputKind.ARRAY,
          ordered(
              "items",
              WEEKDAY_SCHEMA,
              "minItems",
              1,
              "maxItems",
              7,
              "uniqueItems",
              true,
              "example",
              List.of(1, 2, 3, 4, 5, 6)),
          BookingOpeningHoursCodec::openDays,
          days -> days);

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static final CollectionFieldType<List<BookingOpeningException>> OPENING_EXCEPTIONS_TYPE =
      CollectionFieldTypes.structured(
          (Class) List.class,
          CollectionFieldType.InputKind.ARRAY,
          ordered(
              "items",
              ordered(
                  "type",
                  "object",
                  "additionalProperties",
                  false,
                  "required",
                  List.of("dayOfWeek", "start", "end"),
                  "properties",
                  ordered(
                      "dayOfWeek",
                      WEEKDAY_SCHEMA,
                      "start",
                      ordered("type", "string", "pattern", WALL_TIME_PATTERN),
                      "end",
                      ordered("type", "string", "pattern", CLOSING_TIME_PATTERN))),
              "maxItems",
              7,
              "example",
              List.of(ordered("dayOfWeek", 6, "start", "10:00", "end", "16:00"))),
          BookingOpeningHoursCodec::openingExceptions,
          ApiV2BookingConfigurationResource::renderExceptions);

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
    int afterOpeningEnd =
        fields.indexOf(
                fields.stream()
                    .filter(field -> field.name().equals("openingEnd"))
                    .findFirst()
                    .orElseThrow())
            + 1;
    fields.add(
        afterOpeningEnd,
        Field.writable(
                "openDays",
                "openDays",
                OPEN_DAYS_TYPE,
                BookingConfiguration::getOpenDays,
                BookingConfiguration::setOpenDays)
            .documented(
                OpenApiSchemaDocumentation.of(
                    null,
                    "ISO weekdays (1 = Monday to 7 = Sunday) on which the item opens, in ascending"
                        + " order. Omitted weekdays are closed. Replaces the whole selection;"
                        + " defaults to the institution defaults on create.",
                    null))
            .withQueryCapabilities(false, false));
    fields.add(
        afterOpeningEnd + 1,
        Field.writable(
                "openingExceptions",
                "openingExceptions",
                OPENING_EXCEPTIONS_TYPE,
                BookingConfiguration::getOpeningExceptions,
                BookingConfiguration::setOpeningExceptions)
            .documented(
                OpenApiSchemaDocumentation.of(
                    null,
                    "Open weekdays whose hours differ from openingStart/openingEnd, in ascending"
                        + " weekday order. Each dayOfWeek must be in openDays. An empty array means"
                        + " every open day uses the shared hours. Replaces the whole list.",
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
                CollectionFieldTypes.array(),
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
    return new CollectionDescription<>(
        base.resourceName(),
        BookingConfiguration.class,
        fields,
        base.relationships(),
        base.idField(),
        base.defaultSort(),
        access,
        List.of());
  }

  private static Object renderExceptions(List<BookingOpeningException> exceptions) {
    return exceptions.stream()
        .map(
            exception ->
                ordered(
                    "dayOfWeek",
                    exception.dayOfWeek(),
                    "start",
                    exception.start(),
                    "end",
                    exception.end()))
        .toList();
  }

  private static Map<String, Object> ordered(Object... entries) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (int index = 0; index < entries.length; index += 2) {
      result.put((String) entries[index], entries[index + 1]);
    }
    return result;
  }
}
