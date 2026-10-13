package com.researchspace.model.collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CollectionDescriptionTest {

  @Test
  void rejectsInvalidDefinitionsAtConstruction() {
    Field<TestEntity, Long> id = idField();

    assertThrows(IllegalArgumentException.class, () -> description(List.of(id, id), List.of()));

    Field<TestEntity, Long> writableId =
        Field.writable(
            "id", "id", CollectionFieldTypes.longNumber(), TestEntity::id, TestEntity::setId);
    assertThrows(IllegalArgumentException.class, () -> description(List.of(writableId), List.of()));

    Field<TestEntity, String> unsortable =
        Field.readOnly("value", "value", unsortableText(), TestEntity::value);
    assertThrows(
        IllegalArgumentException.class,
        () -> description(List.of(id, unsortable), List.of(new Sort("value", true))));
    assertThrows(
        IllegalArgumentException.class,
        () -> description(List.of(id), List.of(new Sort("id", true), new Sort("id", false))));
    Field<TestEntity, String> value =
        Field.readOnly("value", "value", CollectionFieldTypes.text(10), TestEntity::value);
    assertThrows(
        IllegalArgumentException.class,
        () -> description(List.of(id, value), List.of(new Sort("value", true))));
  }

  @Test
  void rejectsContradictoryWriteRules() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            Field.writable(
                    "value",
                    "value",
                    CollectionFieldTypes.text(10),
                    TestEntity::value,
                    TestEntity::setValue)
                .required()
                .allowNull());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            Field.writable(
                    "value",
                    "value",
                    CollectionFieldTypes.text(10),
                    TestEntity::value,
                    TestEntity::setValue)
                .required()
                .writeOnlyOn(WriteOperation.UPDATE));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            Field.writable(
                    "value",
                    "value",
                    CollectionFieldTypes.text(10),
                    TestEntity::value,
                    TestEntity::setValue)
                .defaultValue("fixed")
                .writeOnlyOn(WriteOperation.UPDATE));

    Field<TestEntity, String> defaulted =
        Field.writable(
                "value",
                "value",
                CollectionFieldTypes.text(10),
                TestEntity::value,
                TestEntity::setValue)
            .defaultValue("fixed");
    assertEquals(
        "fixed",
        description(List.of(idField(), defaulted), List.of(new Sort("id", true)))
            .schema()
            .fields()
            .get(1)
            .defaultValue());
  }

  @Test
  void validatesDefaultsWhenTheFieldIsDefined() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            Field.writable(
                    "value",
                    "value",
                    CollectionFieldTypes.text(3),
                    TestEntity::value,
                    TestEntity::setValue)
                .defaultValue("abcd"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            Field.writable(
                    "value",
                    "value",
                    CollectionFieldTypes.text(10),
                    TestEntity::value,
                    TestEntity::setValue)
                .defaultValue("fixed")
                .required());
  }

  @Test
  void enforcesAnnotatedConstraintsAndAppliesAnnotatedDefaults() {
    CollectionDescription<ConstrainedEntity> description =
        CollectionDescription.fromApiV2Resource(
            ConstrainedResource.class,
            ConstrainedEntity.class,
            List.of(),
            List.of(new Sort("id", true)));
    Map<String, FieldSchema> fields =
        description.schema().fields().stream()
            .collect(Collectors.toMap(FieldSchema::name, Function.identity()));

    assertEquals(
        new CollectionFieldType.Schema("integer", "int32", null), fields.get("size").type());
    assertEquals(
        Map.of("minimum", BigDecimal.ONE, "maximum", BigDecimal.valueOf(7)),
        fields.get("size").writeConstraints());
    assertEquals(
        Map.of("minLength", 2, "pattern", "^[A-Z]"), fields.get("code").writeConstraints());
    assertEquals("AA", fields.get("code").defaultValue());
    assertEquals(Map.of("enum", List.of("OPEN", "CLOSED")), fields.get("state").type().structure());

    ConstrainedEntity entity = new ConstrainedEntity();
    Map<String, Object> invalid = new LinkedHashMap<>();
    invalid.put("code", "BC");
    invalid.put("size", 8);
    assertThrows(
        IllegalArgumentException.class,
        () -> description.apply(entity, invalid, WriteOperation.UPDATE));
    assertEquals("ZZ", entity.getCode(), "no setter runs before every value is valid");

    description.apply(entity, Map.of("code", "BC", "size", 7), WriteOperation.UPDATE);
    assertEquals(7, entity.getSize());
  }

  @Test
  void rejectsAnnotatedRulesThatDoNotFitAtStartup() {
    for (Class<?> resource :
        List.of(
            MinimumOnText.class,
            PatternOnNumber.class,
            EnumValuesOnEnum.class,
            InvalidPattern.class,
            ExampleOutOfRange.class,
            DefaultTooLong.class,
            DefaultOnRequired.class)) {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              CollectionDescription.fromApiV2Resource(
                  resource, ConstrainedEntity.class, List.of(), List.of(new Sort("id", true))),
          resource.getSimpleName());
    }
  }

  @Test
  void selectionSkipsUnrequestedReadersAndApplyValidatesBeforeMutation() {
    TestEntity entity = new TestEntity();
    CollectionDescription<TestEntity> description =
        description(
            List.of(
                idField(),
                Field.writable(
                    "value",
                    "value",
                    CollectionFieldTypes.text(10),
                    TestEntity::readValue,
                    TestEntity::setValue)),
            List.of(new Sort("id", true)));

    assertEquals(Map.of("id", 1L), description.toDocument(entity, "id"::equals));
    assertEquals(0, entity.reads);

    Map<String, Object> invalid = new LinkedHashMap<>();
    invalid.put("value", "changed");
    invalid.put("unknown", "rejected");
    assertThrows(
        CollectionQueryException.class,
        () -> description.apply(entity, invalid, WriteOperation.UPDATE));
    assertEquals("original", entity.value);
  }

  @Test
  void buildsACollectionDescriptionFromAnAnnotatedResourceRecord() {
    CollectionDescription<AnnotatedEntity> description =
        CollectionDescription.fromApiV2Resource(
            ApiV2AnnotatedResource.class,
            AnnotatedEntity.class,
            List.of(),
            List.of(new Sort("id", true)));
    AnnotatedEntity entity = new AnnotatedEntity();

    assertEquals(
        List.of("id", "value", "serverManaged", "createdAt", "updatedAt"),
        description.fields().stream().map(Field::name).toList());
    Map<String, FieldSchema> schemaFields =
        description.schema().fields().stream()
            .collect(Collectors.toMap(FieldSchema::name, Function.identity()));
    assertEquals(10, schemaFields.get("value").type().maxLength());
    assertTrue(schemaFields.get("id").readOnly());
    assertTrue(schemaFields.get("serverManaged").readOnly());
    assertFalse(schemaFields.get("value").sortable());
    assertFalse(schemaFields.get("value").filterOperators().isEmpty());
    assertTrue(schemaFields.get("serverManaged").sortable());
    assertTrue(schemaFields.get("serverManaged").filterOperators().isEmpty());
    assertEquals("value", description.findField("value").orElseThrow().name());
    assertTrue(description.findField("unknown").isEmpty());
    assertInstanceOf(FilterSelector.Property.class, description.requireFilterSelector("value"));
    assertThrows(
        CollectionQueryException.class, () -> description.requireFilterSelector("serverManaged"));
    assertEquals(
        description.schema().access().readAccess(), schemaFields.get("value").readAccess());
    Map<String, Object> expected = new LinkedHashMap<>();
    expected.put("id", 1L);
    expected.put("value", "original");
    expected.put("serverManaged", "generated");
    expected.put("createdAt", "2026-08-01T10:15:30Z");
    expected.put("updatedAt", "2026-08-02T11:16:31Z");
    assertEquals(expected, description.toDocument(entity));
    assertInstanceOf(FilterSelector.Property.class, description.requireFilterSelector("createdAt"));
    assertInstanceOf(FilterSelector.Property.class, description.requireFilterSelector("updatedAt"));

    description.apply(entity, Map.of("value", "changed"), WriteOperation.UPDATE);

    assertEquals("changed", entity.getValue());
  }

  @Test
  void mapsAutomaticAuditFieldsToBeanPropertiesAndOmitsUnavailableProperties() {
    CollectionDescription<DateAuditedEntity> description =
        CollectionDescription.fromApiV2Resource(
            DateAuditedResource.class,
            DateAuditedEntity.class,
            List.of(),
            List.of(new Sort("id", true)));

    assertEquals(
        List.of("id", "createdAt"), description.fields().stream().map(Field::name).toList());
    assertEquals("creationDate", description.requireField("createdAt").property());
    assertTrue(description.findRelationship("createdBy").isEmpty());
    assertEquals(
        "2026-08-01T10:15:30Z", description.toDocument(new DateAuditedEntity()).get("createdAt"));
  }

  @Test
  void rejectsAnAuditUserPropertyThatIsNotAUser() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            CollectionDescription.fromApiV2Resource(
                UsernameAuditedResource.class,
                UsernameAuditedEntity.class,
                List.of(),
                List.of(new Sort("id", true))));
  }

  @Test
  void describesAWritablePolymorphicRelationship() {
    Relationship<TestEntity> relationship = relationship();

    assertTrue(relationship.writableOn(WriteOperation.CREATE));
    assertTrue(relationship.acceptsInput(WriteOperation.UPDATE, RelationshipInputForm.OBJECT));
    assertTrue(relationship.acceptsInput(WriteOperation.UPDATE, RelationshipInputForm.GLOBAL_ID));
    assertFalse(relationship.selfReferenceAllowed());
    assertTrue(relationship.allowSelfReference().selfReferenceAllowed());
    assertEquals(
        new ResourceReference<>(TargetKind.INSTRUMENT, 42L),
        relationship.parseGlobalReference("IN42"));
    assertThrows(IllegalArgumentException.class, () -> relationship.parseGlobalReference("SA42"));
    assertThrows(IllegalArgumentException.class, () -> relationship.parseGlobalReference("IN42v1"));
    // Seeded and baseline rows have negative IDs, rendered as for example "US-3".
    assertEquals(
        new ResourceReference<>(TargetKind.INSTRUMENT, -3L),
        relationship.parseGlobalReference("IN-3"));
    assertThrows(IllegalArgumentException.class, () -> relationship.parseGlobalReference("IN--3"));

    CollectionDescription<TestEntity> description =
        new CollectionDescription<>(
            "testEntities",
            TestEntity.class,
            List.of(idField()),
            List.of(relationship),
            "id",
            List.of(new Sort("id", true)));
    RelationshipSchema schema = description.schema().relationships().get(0);
    assertEquals(List.of("instruments"), schema.targetResources());
    assertTrue(schema.requiredOnCreate());
    assertFalse(schema.selfReferenceAllowed());
    assertInstanceOf(
        FilterSelector.RelationshipPart.class,
        description.requireFilterSelector("target.relationTo"));
    assertThrows(
        CollectionQueryException.class, () -> description.requireFilterSelector("target.globalId"));
  }

  @Test
  void rejectsDuplicateRelationshipMappings() {
    RelationshipTarget<TargetKind> instrument =
        new RelationshipTarget<>("instruments", TargetKind.INSTRUMENT, "IN", TestEntity.class);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            Relationship.polymorphicToOne(
                "target",
                CollectionFieldTypes.longNumber(),
                List.of(instrument, instrument),
                new SplitReferenceBinding<>(TestEntity::target, "targetType", "targetId")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            Relationship.polymorphicToOne(
                "target",
                CollectionFieldTypes.longNumber(),
                List.of(
                    instrument,
                    new RelationshipTarget<>("other", TargetKind.SAMPLE, "IN", TestEntity.class)),
                new SplitReferenceBinding<>(TestEntity::target, "targetType", "targetId")));
  }

  private static Relationship<TestEntity> relationship() {
    return Relationship.polymorphicToOne(
            "target",
            CollectionFieldTypes.longNumber(),
            List.of(
                new RelationshipTarget<>(
                    "instruments", TargetKind.INSTRUMENT, "IN", TestEntity.class)),
            new SplitReferenceBinding<>(TestEntity::target, "targetType", "targetId"))
        .required()
        .acceptGlobalIdOn(WriteOperation.UPDATE);
  }

  @Test
  void keepsInternalFiltersOutOfReachOfCallers() {
    CollectionDescription<TestEntity> described = withInternalFilter();

    assertInstanceOf(
        FilterSelector.Property.class,
        described.requireFilterSelector("sharingAcl"),
        "the query compiler resolves it, so an access constraint can name it");
    assertThrows(
        CollectionQueryException.class,
        () -> described.requirePublicFilterSelector("sharingAcl"),
        "a caller must not be able to filter on it");
    assertFalse(
        described.schema().filters().stream()
            .anyMatch(filter -> filter.selector().equals("sharingAcl")),
        "an unpublished filter must not appear in the generated OpenAPI document");
  }

  @Test
  void refusesAnInternalFilterThatShadowsAPublicOne() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CollectionDescription<>(
                "testEntities",
                TestEntity.class,
                List.of(idField()),
                List.of(),
                "id",
                List.of(new Sort("id", true)),
                AccessPolicy.authenticated(),
                List.of(new InternalFilter("id", "id", CollectionFieldTypes.longNumber()))));
  }

  @Test
  void refusesToReadAnInternalFilterInMemory() {
    CollectionDescription<TestEntity> described = withInternalFilter();

    assertThrows(
        IllegalStateException.class,
        () -> described.readFilterValue(new TestEntity(), "sharingAcl"),
        "an internal filter has no reader, so only a database query can evaluate it");
  }

  private static CollectionDescription<TestEntity> withInternalFilter() {
    return new CollectionDescription<>(
        "testEntities",
        TestEntity.class,
        List.of(idField()),
        List.of(),
        "id",
        List.of(new Sort("id", true)),
        AccessPolicy.authenticated(),
        List.of(new InternalFilter("sharingAcl", "sharingACL.acl", CollectionFieldTypes.text())));
  }

  private static Field<TestEntity, Long> idField() {
    return Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), TestEntity::id);
  }

  private static CollectionDescription<TestEntity> description(
      List<Field<TestEntity, ?>> fields, List<Sort> defaultSort) {
    return new CollectionDescription<>(
        "testEntities", TestEntity.class, fields, List.of(), "id", defaultSort);
  }

  private static CollectionFieldType<String> unsortableText() {
    return new CollectionFieldType<>() {
      @Override
      public Class<String> javaType() {
        return String.class;
      }

      @Override
      public InputKind inputKind() {
        return InputKind.STRING;
      }

      @Override
      public String parse(String value) {
        return value;
      }

      @Override
      public Object serialize(String value) {
        return value;
      }

      @Override
      public Set<Operator> operators() {
        return Set.of(Operator.EQUAL);
      }

      @Override
      public boolean sortable() {
        return false;
      }
    };
  }

  private static final class TestEntity {

    private Long id = 1L;
    private String value = "original";
    private int reads;
    private ResourceReference<TargetKind, Long> target =
        new ResourceReference<>(TargetKind.INSTRUMENT, 1L);

    private Long id() {
      return id;
    }

    private void setId(Long id) {
      this.id = id;
    }

    private String value() {
      return value;
    }

    private String readValue() {
      reads++;
      return value;
    }

    private void setValue(String value) {
      this.value = value;
    }

    private ResourceReference<TargetKind, Long> target() {
      return target;
    }
  }

  private enum TargetKind {
    INSTRUMENT,
    SAMPLE
  }

  @ApiV2ResourceDefinition(name = "annotatedEntities", entity = AnnotatedEntity.class, id = "id")
  private record ApiV2AnnotatedResource(
      @ApiV2ResourceField Long id,
      @ApiV2ResourceField(maxLength = 10, sortable = false) String value,
      @ApiV2ResourceField(filterable = false) String serverManaged) {}

  public static final class AnnotatedEntity {

    private String value = "original";
    private final Date creationDate = Date.from(Instant.parse("2026-08-01T10:15:30Z"));
    private final Date modificationDate = Date.from(Instant.parse("2026-08-02T11:16:31Z"));

    public Long getId() {
      return 1L;
    }

    public String getValue() {
      return value;
    }

    public void setValue(String value) {
      this.value = value;
    }

    public String getServerManaged() {
      return "generated";
    }

    public Date getCreationDate() {
      return creationDate;
    }

    public Date getModificationDate() {
      return modificationDate;
    }
  }

  private enum ConstrainedState {
    OPEN,
    CLOSED
  }

  @ApiV2ResourceDefinition(name = "constrained", entity = ConstrainedEntity.class, id = "id")
  private record ConstrainedResource(
      @ApiV2ResourceField Long id,
      @ApiV2ResourceField(minLength = 2, pattern = "^[A-Z]", example = "AB", defaultValue = "AA")
          String code,
      @ApiV2ResourceField(minimum = "1", maximum = "7", example = "3") Integer size,
      @ApiV2ResourceField ConstrainedState state) {}

  @ApiV2ResourceDefinition(name = "minimumOnText", entity = ConstrainedEntity.class, id = "id")
  private record MinimumOnText(
      @ApiV2ResourceField Long id, @ApiV2ResourceField(minimum = "1") String code) {}

  @ApiV2ResourceDefinition(name = "patternOnNumber", entity = ConstrainedEntity.class, id = "id")
  private record PatternOnNumber(
      @ApiV2ResourceField Long id, @ApiV2ResourceField(pattern = "1") Integer size) {}

  @ApiV2ResourceDefinition(name = "enumValuesOnEnum", entity = ConstrainedEntity.class, id = "id")
  private record EnumValuesOnEnum(
      @ApiV2ResourceField Long id,
      @ApiV2ResourceField(enumValues = "OPEN") ConstrainedState state) {}

  @ApiV2ResourceDefinition(name = "invalidPattern", entity = ConstrainedEntity.class, id = "id")
  private record InvalidPattern(
      @ApiV2ResourceField Long id, @ApiV2ResourceField(pattern = "(") String code) {}

  @ApiV2ResourceDefinition(name = "exampleOutOfRange", entity = ConstrainedEntity.class, id = "id")
  private record ExampleOutOfRange(
      @ApiV2ResourceField Long id,
      @ApiV2ResourceField(minimum = "1", example = "0") Integer size) {}

  @ApiV2ResourceDefinition(name = "defaultTooLong", entity = ConstrainedEntity.class, id = "id")
  private record DefaultTooLong(
      @ApiV2ResourceField Long id,
      @ApiV2ResourceField(maxLength = 2, defaultValue = "ABC") String code) {}

  @ApiV2ResourceDefinition(name = "defaultOnRequired", entity = ConstrainedEntity.class, id = "id")
  private record DefaultOnRequired(
      @ApiV2ResourceField Long id,
      @ApiV2ResourceField(requiredOnCreate = true, defaultValue = "AB") String code) {}

  public static final class ConstrainedEntity {

    private String code = "ZZ";
    private Integer size = 3;
    private ConstrainedState state = ConstrainedState.OPEN;

    public Long getId() {
      return 1L;
    }

    public String getCode() {
      return code;
    }

    public void setCode(String code) {
      this.code = code;
    }

    public Integer getSize() {
      return size;
    }

    public void setSize(Integer size) {
      this.size = size;
    }

    public ConstrainedState getState() {
      return state;
    }

    public void setState(ConstrainedState state) {
      this.state = state;
    }
  }

  @ApiV2ResourceDefinition(
      name = "dateAuditedEntities",
      entity = DateAuditedEntity.class,
      id = "id")
  private record DateAuditedResource(@ApiV2ResourceField Long id) {}

  public static final class DateAuditedEntity {

    public Long getId() {
      return 1L;
    }

    public Date getCreationDate() {
      return Date.from(Instant.parse("2026-08-01T10:15:30Z"));
    }
  }

  @ApiV2ResourceDefinition(
      name = "usernameAuditedEntities",
      entity = UsernameAuditedEntity.class,
      id = "id")
  private record UsernameAuditedResource(@ApiV2ResourceField Long id) {}

  public static final class UsernameAuditedEntity {

    public Long getId() {
      return 1L;
    }

    public String getCreatedBy() {
      return "creator";
    }
  }
}
