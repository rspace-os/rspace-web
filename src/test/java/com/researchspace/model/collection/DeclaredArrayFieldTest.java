package com.researchspace.model.collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DeclaredArrayFieldTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Field<Row, String> NAME =
      Field.embedded("name", CollectionFieldTypes.text(10), Row::name).required();
  private static final Field<Row, Integer> NUMBER =
      Field.embedded("number", CollectionFieldTypes.integer(), Row::number).defaultValue(2);
  private static final DeclaredFieldType<List<Row>> ROWS =
      CollectionFieldTypes.array(Row.class, List.of(NAME, NUMBER), DeclaredArrayFieldTest::row);

  @Test
  void requiresUniqueChildrenAndRejectsEmbeddedQueriesAndOpaqueArrays() {
    assertThrows(
        IllegalArgumentException.class,
        () -> CollectionFieldTypes.array(Row.class, List.of(), DeclaredArrayFieldTest::row));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            CollectionFieldTypes.array(
                Row.class, List.of(NAME, NAME), DeclaredArrayFieldTest::row));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            CollectionFieldTypes.array(
                Row.class,
                List.of(NAME.withQueryCapabilities(true, false)),
                DeclaredArrayFieldTest::row));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            CollectionFieldTypes.structured(
                List.class,
                CollectionFieldType.InputKind.ARRAY,
                Map.of("items", Map.of("type", "string")),
                node -> List.of(),
                value -> value));
    assertThrows(
        IllegalArgumentException.class,
        () -> description(Field.embedded("rows", ROWS, Item::rows)));
    assertFalse(ROWS.sortable());
    assertTrue(ROWS.operators().isEmpty());
  }

  @Test
  void registrationChecksCustomDeclarationsAgainForCycles() {
    MutableArray type = new MutableArray();
    type.children.add(NAME);
    Field<Row, List<Row>> self = Field.embedded("children", type, row -> List.of(row));
    type.children.add(self);
    assertThrows(
        IllegalArgumentException.class,
        () -> description(Field.readOnly("rows", "rows", type, Item::rows)));
    MutableArray registered = new MutableArray();
    registered.children.add(NAME);
    CollectionDescription<Item> valid =
        description(Field.readOnly("rows", "rows", registered, Item::rows));
    registered.children.clear();
    assertThrows(IllegalArgumentException.class, () -> new ResourceRegistry(List.of(valid)));
  }

  @Test
  void constructsAfterValidationAndSuppliesDefaultsOnReplacementUpdates() throws Exception {
    assertEquals(
        List.of(new Row("first", 2, "private")),
        ROWS.parse(JSON.readTree("[{\"name\":\"first\"}]"), WriteOperation.UPDATE));
    EmbeddedFieldException required =
        assertThrows(
            EmbeddedFieldException.class,
            () -> ROWS.parse(JSON.readTree("[{\"number\":3}]"), WriteOperation.UPDATE));
    assertEquals("[0].name", required.path());
    assertEquals(DocumentValidationException.Reason.REQUIRED, required.reason());
    EmbeddedFieldException unknown =
        assertThrows(
            EmbeddedFieldException.class,
            () -> ROWS.parse(JSON.readTree("[{\"name\":\"first\",\"privateValue\":\"x\"}]")));
    assertEquals("[0].privateValue", unknown.path());
    assertEquals(DocumentValidationException.Reason.UNKNOWN_FIELD, unknown.reason());
    for (String invalid :
        List.of("[1]", "[null]", "[[{}]]", "[{\"name\":null}]", "[{\"name\":\"too-long-name\"}]")) {
      assertThrows(EmbeddedFieldException.class, () -> ROWS.parse(JSON.readTree(invalid)));
    }
    assertEquals(List.of(), ROWS.parse(JSON.readTree("[]")));
  }

  @Test
  void normalAndOverrideReadsProjectDtoRowsAndNarrowChildAccess() {
    DeclaredFieldType<List<Row>> hiddenNumbers =
        CollectionFieldTypes.array(
            Row.class,
            List.of(NAME, NUMBER.readableBy(AccessFunction.when(context -> context.targets(9L)))),
            DeclaredArrayFieldTest::row);
    CollectionDescription<Item> description =
        description(Field.readOnly("rows", "rows", hiddenNumbers, Item::rows));
    Item item = new Item(9L, List.of(new Row("first", 4, "never-public")));
    AccessContext context = new AccessContext(null, AccessContext.Operation.READ, "items", 9L);
    assertEquals(
        List.of(Map.of("name", "first", "number", 4)),
        description.toDocument(item, name -> true, Map.of(), context).get("rows"));
    assertEquals(List.of(Map.of("name", "first")), description.toDocument(item).get("rows"));
    assertEquals(
        List.of(Map.of("name", "override")),
        description
            .toDocument(
                item,
                name -> true,
                Map.of(
                    "rows",
                    List.of(Map.of("name", "override", "number", 7, "privateValue", "hidden"))))
            .get("rows"));
    assertThrows(
        EmbeddedFieldException.class,
        () -> description.toDocument(item, name -> true, Map.of("rows", List.of(1))));
    CollectionDescription<Item> denied =
        description(
            Field.readOnly("rows", "rows", ROWS, Item::rows).readableBy(AccessFunction.never()));
    assertFalse(denied.toDocument(item).containsKey("rows"));
  }

  @Test
  void includedReadDocumentsCannotBypassChildProjectionOrArrayValidation() {
    CollectionDescription<Item> items =
        description(Field.readOnly("rows", "rows", ROWS, Item::rows));
    CollectionDescription<Parent> parents =
        new CollectionDescription<>(
            "parents",
            Parent.class,
            List.of(
                Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), Parent::id),
                Relationship.referenceToOne(
                    "item",
                    "items",
                    CollectionFieldTypes.longNumber(),
                    Item.class,
                    Parent::item,
                    Item::id,
                    "item.id")),
            "id",
            List.of(new Sort("id", true)));
    ResourceRegistry registry = new ResourceRegistry(List.of(parents, items));
    ResourceRenderer renderer = new ResourceRenderer(registry);
    Item item = new Item(9L, List.of());
    Parent parent = new Parent(1L, item);
    ResourceRenderer.TargetResolver good =
        (resource, id) ->
            Optional.of(
                ResourceRenderer.ResolvedTarget.lazy(
                    () -> item,
                    FieldSelection.all(),
                    Map.of(
                        "id",
                        9L,
                        "rows",
                        List.of(
                            Map.of("name", "included", "number", 3, "privateValue", "hidden")))));
    Map<?, ?> reference =
        (Map<?, ?>)
            renderer
                .render(
                    parent,
                    parents,
                    FieldSelection.all(),
                    IncludeTree.toDepth(parents, registry, 1),
                    good)
                .get("item");
    assertEquals(
        List.of(Map.of("name", "included", "number", 3)),
        ((Map<?, ?>) reference.get("value")).get("rows"));
    ResourceRenderer.TargetResolver bad =
        (resource, id) ->
            Optional.of(
                ResourceRenderer.ResolvedTarget.lazy(
                    () -> item, FieldSelection.all(), Map.of("id", 9L, "rows", List.of("scalar"))));
    assertThrows(
        EmbeddedFieldException.class,
        () ->
            renderer.render(
                parent,
                parents,
                FieldSelection.all(),
                IncludeTree.toDepth(parents, registry, 1),
                bad));
  }

  @Test
  void customSerializersDefaultsAndExamplesCannotReplaceDeclaredObjectRows() {
    MutableArray custom =
        new MutableArray() {
          @Override
          public Object serialize(List<Row> rows, AccessContext context) {
            return List.of("scalar");
          }
        };
    custom.children.add(NAME);
    CollectionDescription<Item> description =
        description(Field.readOnly("rows", "rows", custom, Item::rows));
    assertThrows(
        EmbeddedFieldException.class,
        () -> description.toDocument(new Item(1L, List.of(new Row("first", null, "hidden")))));
    Field<Item, List<Row>> field =
        Field.writable("rows", "rows", ROWS, Item::rows, (item, rows) -> {});
    assertThrows(
        EmbeddedFieldException.class,
        () -> field.defaultValue(List.of(new Row(null, 2, "hidden"))));
    assertThrows(
        EmbeddedFieldException.class,
        () -> field.documented(OpenApiSchemaDocumentation.builder().example("[1]").build()));
    assertThrows(
        EmbeddedFieldException.class,
        () ->
            field.documented(
                OpenApiSchemaDocumentation.builder()
                    .additionalExample("[{\"name\":\"first\",\"privateValue\":\"hidden\"}]")
                    .build()));
    FieldSchema schema = field.schema(AccessPolicy.authenticated());
    assertEquals(
        List.of("name", "number"), schema.fields().stream().map(FieldSchema::name).toList());
    assertEquals(2, schema.fields().get(1).defaultValue());
  }

  @Test
  void customSerializersCannotConcealInvalidBackingRowsOrBounds() {
    MutableArray custom =
        new MutableArray() {
          @Override
          public Object serialize(List<Row> rows, AccessContext context) {
            return List.of(Map.of("name", "valid"));
          }

          @Override
          public Map<String, Object> writeConstraints() {
            return Map.of("minItems", 1, "maxItems", 2);
          }
        };
    custom.children.add(NAME);
    CollectionDescription<Item> items =
        description(Field.readOnly("rows", "rows", custom, Item::rows));
    assertThrows(
        EmbeddedFieldException.class,
        () -> items.toDocument(new Item(1L, List.of(new Row(null, 2, "hidden")))));
    assertThrows(EmbeddedFieldException.class, () -> items.toDocument(new Item(1L, List.of())));
    assertThrows(
        EmbeddedFieldException.class,
        () ->
            items.toDocument(
                new Item(1L, List.of()),
                name -> true,
                Map.of(
                    "rows",
                    List.of(
                        Map.of("name", "one"), Map.of("name", "two"), Map.of("name", "three")))));
  }

  @Test
  void nestedRestrictedRequiredChildrenRemainOmittedInAlternateReadDocuments() {
    DeclaredFieldType<Row> detail =
        CollectionFieldTypes.object(
            Row.class,
            List.of(NAME.readableBy(AccessFunction.never()), NUMBER),
            DeclaredArrayFieldTest::row);
    DeclaredFieldType<List<Group>> groups =
        CollectionFieldTypes.array(
            Group.class,
            List.of(Field.embedded("detail", detail, Group::detail).required()),
            values -> new Group((Row) values.get("detail")));
    CollectionDescription<GroupItem> items =
        new CollectionDescription<>(
            "groups",
            GroupItem.class,
            List.of(
                Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), GroupItem::id),
                Field.readOnly("groups", "groups", groups, GroupItem::groups)),
            "id",
            List.of(new Sort("id", true)));
    GroupItem item = new GroupItem(1L, List.of(new Group(new Row("secret", 3, "private"))));
    assertEquals(
        List.of(Map.of("detail", Map.of("number", 3))), items.toDocument(item).get("groups"));
    assertThrows(
        EmbeddedFieldException.class,
        () ->
            items.toDocument(
                item,
                name -> true,
                Map.of("groups", List.of(Map.of("detail", Map.of("number", 4))))));
    assertEquals(
        List.of(Map.of("detail", Map.of("number", 4))),
        EmbeddedObjects.project(
            groups,
            List.of(Map.of("detail", Map.of("number", 4))),
            new AccessContext(null, AccessContext.Operation.READ, "groups")));
    Field<GroupItem, List<Group>> defaulted =
        Field.writable("groups", "groups", groups, GroupItem::groups, (target, value) -> {})
            .defaultValue(item.groups());
    assertEquals(
        List.of(Map.of("detail", Map.of("name", "secret", "number", 3))),
        defaulted.schema(AccessPolicy.authenticated()).defaultValue());
  }

  private static Row row(Map<String, Object> values) {
    return new Row((String) values.get("name"), (Integer) values.get("number"), "private");
  }

  @Test
  void freshCustomSerializerOutputRequiresHiddenChildrenBeforeValidatingTheArray() {
    MutableArray custom =
        new MutableArray() {
          @Override
          public Object serialize(List<Row> rows, AccessContext context) {
            return List.of(Map.of("number", 7), Map.of("number", 7));
          }

          @Override
          public void validate(List<Row> rows) {
            if (rows.stream().map(Row::number).distinct().count() != rows.size()) {
              throw new IllegalArgumentException("Duplicate numbers");
            }
          }
        };
    custom.children.add(NAME.readableBy(AccessFunction.never()));
    custom.children.add(NUMBER);
    CollectionDescription<Item> items =
        description(Field.readOnly("rows", "rows", custom, Item::rows));
    Item item =
        new Item(1L, List.of(new Row("first", 1, "private"), new Row("second", 2, "private")));
    EmbeddedFieldException failure =
        assertThrows(EmbeddedFieldException.class, () -> items.toDocument(item));
    assertEquals("[0].name", failure.path());
    assertEquals(DocumentValidationException.Reason.REQUIRED, failure.reason());
  }

  @Test
  void scalarChildExamplesUseTheirJsonTypeAndConstraints() {
    assertThrows(
        IllegalArgumentException.class,
        () -> NUMBER.documented(OpenApiSchemaDocumentation.builder().example("\"bad\"").build()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            NAME.documented(
                OpenApiSchemaDocumentation.builder()
                    .additionalExample("\"too-long-name\"")
                    .build()));
    assertEquals(
        "4",
        NUMBER
            .documented(OpenApiSchemaDocumentation.builder().example("4").build())
            .schema(AccessPolicy.authenticated())
            .openApi()
            .example());
  }

  @Test
  void normalAndIncludedReadDocumentsKeepTheIdentifierDespiteItsFieldGate() {
    CollectionDescription<Item> items =
        new CollectionDescription<>(
            "items",
            Item.class,
            List.of(
                Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), Item::id)
                    .readableBy(AccessFunction.never()),
                Field.readOnly("rows", "rows", ROWS, Item::rows)),
            "id",
            List.of(new Sort("id", true)));
    Item item = new Item(9L, List.of());
    assertEquals(9L, items.toDocument(item).get("id"));
    CollectionDescription<Parent> parents =
        new CollectionDescription<>(
            "parents",
            Parent.class,
            List.of(
                Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), Parent::id),
                Relationship.referenceToOne(
                    "item",
                    "items",
                    CollectionFieldTypes.longNumber(),
                    Item.class,
                    Parent::item,
                    Item::id,
                    "item.id")),
            "id",
            List.of(new Sort("id", true)));
    ResourceRegistry registry = new ResourceRegistry(List.of(parents, items));
    ResourceRenderer.TargetResolver resolver =
        (resource, id) ->
            Optional.of(
                ResourceRenderer.ResolvedTarget.lazy(
                    () -> item, FieldSelection.all(), Map.of("id", 9L, "rows", List.of())));
    Map<?, ?> reference =
        (Map<?, ?>)
            new ResourceRenderer(registry)
                .render(
                    new Parent(1L, item),
                    parents,
                    FieldSelection.all(),
                    IncludeTree.toDepth(parents, registry, 1),
                    resolver)
                .get("item");
    assertEquals(9L, ((Map<?, ?>) reference.get("value")).get("id"));
  }

  @Test
  void annotatedArraysExplainHowToDeclareTheirChildren() {
    IllegalArgumentException failure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                CollectionDescription.fromApiV2Resource(
                    ArrayResource.class,
                    ArrayBean.class,
                    List.of(),
                    List.of(new Sort("id", true))));
    assertTrue(failure.getMessage().contains("declared children"));
    assertTrue(failure.getMessage().contains("Field.embedded"));
  }

  @ApiV2ResourceDefinition(name = "arrayBeans", entity = ArrayBean.class)
  private record ArrayResource(@ApiV2ResourceField Long id, @ApiV2ResourceField List<Row> rows) {}

  public static class ArrayBean {
    public Long getId() {
      return 1L;
    }

    public List<Row> getRows() {
      return List.of();
    }
  }

  private static CollectionDescription<Item> description(Field<Item, List<Row>> rows) {
    return new CollectionDescription<>(
        "items",
        Item.class,
        List.of(Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), Item::id), rows),
        "id",
        List.of(new Sort("id", true)));
  }

  private static class MutableArray implements DeclaredFieldType<List<Row>> {
    final List<Field<Row, ?>> children = new ArrayList<>();

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Class<List<Row>> javaType() {
      return (Class) List.class;
    }

    public InputKind inputKind() {
      return InputKind.ARRAY;
    }

    public Class<Row> rowType() {
      return Row.class;
    }

    public List<Field<Row, ?>> fields() {
      return children;
    }

    public Object assemble(Map<String, Object> values) {
      return row(values);
    }

    public Set<Operator> operators() {
      return Set.of();
    }

    public boolean sortable() {
      return false;
    }
  }

  private record Row(String name, Integer number, String privateValue) {}

  private record Item(Long id, List<Row> rows) {}

  private record Parent(Long id, Item item) {}

  private record Group(Row detail) {}

  private record GroupItem(Long id, List<Group> groups) {}
}
