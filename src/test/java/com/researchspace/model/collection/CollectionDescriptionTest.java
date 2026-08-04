package com.researchspace.model.collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.collection.CollectionDescription.Field;
import com.researchspace.model.collection.CollectionDescription.FieldSchema;
import com.researchspace.model.collection.CollectionDescription.Operator;
import com.researchspace.model.collection.CollectionDescription.Sort;
import com.researchspace.model.collection.CollectionDescription.WriteOperation;
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
            ApiV2AnnotatedResource.class, List.of(), List.of(new Sort("id", true)));
    AnnotatedEntity entity = new AnnotatedEntity();

    assertEquals(
        List.of("id", "value", "serverManaged", "createdAt", "updatedAt", "createdBy", "updatedBy"),
        description.fields().stream().map(Field::name).toList());
    Map<String, FieldSchema> schemaFields =
        description.schema().fields().stream()
            .collect(Collectors.toMap(FieldSchema::name, Function.identity()));
    assertEquals(10, schemaFields.get("value").type().maxLength());
    assertTrue(schemaFields.get("id").readOnly());
    assertTrue(schemaFields.get("serverManaged").readOnly());
    assertEquals(
        description.schema().access().readAccess(), schemaFields.get("value").readAccess());
    Map<String, Object> expected = new LinkedHashMap<>();
    expected.put("id", 1L);
    expected.put("value", "original");
    expected.put("serverManaged", "generated");
    expected.put("createdAt", "2026-08-01T10:15:30Z");
    expected.put("updatedAt", "2026-08-02T11:16:31Z");
    expected.put("createdBy", "creator");
    expected.put("updatedBy", "editor");
    assertEquals(expected, description.toDocument(entity));
    assertInstanceOf(FilterSelector.Property.class, description.requireFilterSelector("createdAt"));
    assertInstanceOf(FilterSelector.Property.class, description.requireFilterSelector("updatedAt"));
    assertInstanceOf(FilterSelector.Property.class, description.requireFilterSelector("createdBy"));
    assertInstanceOf(FilterSelector.Property.class, description.requireFilterSelector("updatedBy"));

    description.apply(entity, Map.of("value", "changed"), WriteOperation.UPDATE);

    assertEquals("changed", entity.getValue());
  }

  @Test
  void mapsAutomaticAuditFieldsToLegacyBeanPropertiesAndOmitsUnavailableProperties() {
    CollectionDescription<LegacyAuditedEntity> description =
        CollectionDescription.fromApiV2Resource(
            LegacyAuditedResource.class, List.of(), List.of(new Sort("id", true)));

    assertEquals(
        List.of("id", "createdAt"), description.fields().stream().map(Field::name).toList());
    assertEquals("creationDate", description.requireField("createdAt").property());
    assertTrue(description.findRelationship("createdBy").isEmpty());
    assertEquals(
        "2026-08-01T10:15:30Z", description.toDocument(new LegacyAuditedEntity()).get("createdAt"));
  }

  @Test
  void describesAWritablePolymorphicRelationship() {
    CollectionDescription.Relationship<TestEntity> relationship = relationship();

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

    CollectionDescription<TestEntity> description =
        new CollectionDescription<>(
            "testEntities",
            TestEntity.class,
            List.of(idField()),
            List.of(relationship),
            "id",
            List.of(new Sort("id", true)));
    CollectionDescription.RelationshipSchema schema = description.schema().relationships().get(0);
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
            CollectionDescription.Relationship.polymorphicToOne(
                "target",
                CollectionFieldTypes.longNumber(),
                List.of(instrument, instrument),
                new SplitReferenceBinding<>(TestEntity::target, "targetType", "targetId")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            CollectionDescription.Relationship.polymorphicToOne(
                "target",
                CollectionFieldTypes.longNumber(),
                List.of(
                    instrument,
                    new RelationshipTarget<>("other", TargetKind.SAMPLE, "IN", TestEntity.class)),
                new SplitReferenceBinding<>(TestEntity::target, "targetType", "targetId")));
  }

  @Test
  void validatesReferenceValuesAndTypedResolvedEntities() {
    TestEntity entity = new TestEntity();
    ResourceReference<TargetKind, Long> reference =
        new ResourceReference<>(TargetKind.INSTRUMENT, 1L);
    ResolvedResourceReference<TargetKind, Long> resolved =
        new ResolvedResourceReference<>(reference, entity);

    assertEquals(entity, resolved.entityAs(TestEntity.class));
    assertThrows(ClassCastException.class, () -> resolved.entityAs(String.class));
    assertThrows(NullPointerException.class, () -> new ResourceReference<>(null, 1L));
    assertThrows(
        NullPointerException.class, () -> new ResourceReference<>(TargetKind.SAMPLE, null));
    assertThrows(
        NullPointerException.class, () -> new ResolvedResourceReference<>(reference, null));
  }

  private static CollectionDescription.Relationship<TestEntity> relationship() {
    return CollectionDescription.Relationship.polymorphicToOne(
            "target",
            CollectionFieldTypes.longNumber(),
            List.of(
                new RelationshipTarget<>(
                    "instruments", TargetKind.INSTRUMENT, "IN", TestEntity.class)),
            new SplitReferenceBinding<>(TestEntity::target, "targetType", "targetId"))
        .required()
        .acceptGlobalIdOn(WriteOperation.UPDATE);
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
      @ApiV2ResourceField(maxLength = 10) String value,
      @ApiV2ResourceField String serverManaged) {}

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

    public String getCreatedBy() {
      return "creator";
    }

    public String getModifiedBy() {
      return "editor";
    }
  }

  @ApiV2ResourceDefinition(
      name = "legacyAuditedEntities",
      entity = LegacyAuditedEntity.class,
      id = "id")
  private record LegacyAuditedResource(@ApiV2ResourceField Long id) {}

  public static final class LegacyAuditedEntity {

    public Long getId() {
      return 1L;
    }

    public Date getCreationDate() {
      return Date.from(Instant.parse("2026-08-01T10:15:30Z"));
    }
  }
}
