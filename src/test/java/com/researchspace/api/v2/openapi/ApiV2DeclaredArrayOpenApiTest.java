package com.researchspace.api.v2.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.core.type.TypeReference;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.api.v2.resource.ApiV2ResourceSpec;
import com.researchspace.api.v2.resource.ResourceAccessSpec;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.api.v2.resource.ResourceOperations;
import com.researchspace.model.collection.AccessFunction;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionFieldTypes;
import com.researchspace.model.collection.CollectionFieldTypes.Constraints;
import com.researchspace.model.collection.DeclaredFieldType;
import com.researchspace.model.collection.Field;
import com.researchspace.model.collection.OpenApiSchemaDocumentation;
import com.researchspace.model.collection.Sort;
import com.researchspace.model.collection.WriteOperation;
import com.researchspace.service.resourceaccess.ProtectedResourceAccess;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ApiV2DeclaredArrayOpenApiTest {
  private static final Field<Detail, Integer> COUNT =
      Field.embedded(
              "count",
              CollectionFieldTypes.constrained(
                  CollectionFieldTypes.integer(),
                  new Constraints(BigDecimal.ONE, BigDecimal.valueOf(7), null, null, null)),
              Detail::count)
          .required();
  private static final DeclaredFieldType<Detail> DETAIL =
      CollectionFieldTypes.object(
          Detail.class,
          List.of(COUNT),
          values -> new Detail((Integer) values.get("count"), "private"));
  private static final DeclaredFieldType<List<Detail>> DETAILS =
      CollectionFieldTypes.array(
          Detail.class,
          List.of(COUNT),
          values -> new Detail((Integer) values.get("count"), "private"));
  private static final DeclaredFieldType<List<Row>> ROWS = rows(10);
  private static final String ROW_REF =
      "#/components/schemas/DeclaredApiV2DeclaredArrayOpenApiTestRow";

  private static DeclaredFieldType<List<Row>> rows(int nameLength) {
    return CollectionFieldTypes.array(
        Row.class,
        List.of(
            Field.embedded("name", CollectionFieldTypes.text(nameLength), Row::name)
                .required()
                .documented(
                    OpenApiSchemaDocumentation.builder()
                        .example("123")
                        .additionalExample("true")
                        .additionalExample("null")
                        .additionalExample("\"abc\"")
                        .build()),
            Field.embedded("group", DETAIL, Row::group)
                .required()
                .documented(
                    OpenApiSchemaDocumentation.builder()
                        .example("{\"count\":3}")
                        .additionalExample("{\"count\":4}")
                        .build()),
            Field.embedded("children", DETAILS, Row::children)
                .defaultValue(List.of(new Detail(2, "private")))
                .documented(
                    OpenApiSchemaDocumentation.builder()
                        .example("[{\"count\":5}]")
                        .additionalExample("[{\"count\":6}]")
                        .build()),
            Field.embedded("createOnly", CollectionFieldTypes.text(), Row::createOnly)
                .writeOnlyOn(WriteOperation.CREATE)
                .readableBy(AccessFunction.never())),
        values ->
            new Row(
                (String) values.get("name"),
                (Detail) values.get("group"),
                details(values.get("children")),
                (String) values.get("createOnly")),
        row -> {},
        rows -> {},
        1,
        7);
  }

  @Test
  void generatedSchemasPreserveRecursiveChildrenDefaultsAndReplacementRequirements() {
    Map<String, Object> schemas = schemas(document(description("rows"), false));
    for (String operation : List.of("Read", "Create", "Update")) {
      Map<String, Object> resource = map(schemas.get("Items" + operation));
      Map<String, Object> array = map(map(resource.get("properties")).get("rows"));
      assertEquals(List.of("array", "null"), array.get("type"), operation);
      assertEquals(1, array.get("minItems"), operation);
      assertEquals(7, array.get("maxItems"), operation);
      Map<String, Object> item = resolved(schemas, array.get("items"));
      assertEquals(false, item.get("additionalProperties"), operation);
      Map<String, Object> children = map(item.get("properties"));
      assertEquals(10, map(children.get("name")).get("maxLength"), operation);
      Map<String, Object> group = map(children.get("group"));
      assertEquals(false, group.get("additionalProperties"), operation);
      assertEquals(BigDecimal.ONE, map(map(group.get("properties")).get("count")).get("minimum"));
      Map<String, Object> childArray = map(children.get("children"));
      assertEquals(false, map(childArray.get("items")).get("additionalProperties"));
      if ("Read".equals(operation)) {
        assertFalse(item.containsKey("required"));
        assertFalse(group.containsKey("required"));
      } else {
        assertEquals(Set.of("name", "group"), Set.copyOf((List<?>) item.get("required")));
        assertEquals(List.of("count"), group.get("required"));
        assertEquals(List.of(Map.of("count", 2)), childArray.get("default"));
        assertFalse(resource.containsKey("required"));
      }
      assertEquals(!"Update".equals(operation), children.containsKey("createOnly"));
      assertEquals("Create".equals(operation), array.containsKey("default"));
      if ("Create".equals(operation)) {
        assertEquals(
            List.of(
                Map.of(
                    "name",
                    "example",
                    "group",
                    Map.of("count", 3),
                    "children",
                    List.of(Map.of("count", 2)),
                    "createOnly",
                    "creation")),
            array.get("default"));
      }
    }
  }

  @Test
  void json31AndGeneratedOpenApiKeepEmptyMapsAndListsInRecursiveFieldMetadata() throws Exception {
    Map<String, Object> document = document(description("rows"), false);
    Map<String, Object> array =
        map(map(map(schemas(document).get("ItemsRead")).get("properties")).get("rows"));
    List<?> fields = (List<?>) array.get("x-rspace-fields");
    Map<String, Object> name = map(fields.get(0));
    String policy = String.valueOf(map(name.get("createAccess")).get("$ref"));
    assertEquals("#/components/x-rspace-access-policies/Authenticated", policy);
    assertEquals(
        Set.of("description", "authentication", "denialReasonCodes"),
        map(map(map(document.get("components")).get("x-rspace-access-policies"))
                .get(policy.substring(policy.lastIndexOf('/') + 1)))
            .keySet());
    assertEquals(Map.of(), name.get("writeConstraints"));
    assertEquals(List.of(), name.get("fields"));
    assertEquals(Map.of(), map(name.get("type")).get("structure"));
    Map<String, Object> group = map(fields.get(1));
    Map<String, Object> count = map(((List<?>) group.get("fields")).get(0));
    assertEquals(List.of(), count.get("fields"));
    assertEquals(Map.of(), map(count.get("type")).get("structure"));
    assertEquals(
        Map.of("minimum", BigDecimal.ONE, "maximum", BigDecimal.valueOf(7)),
        count.get("writeConstraints"));
    Map<String, Object> jsonRoundTrip =
        Json31.mapper()
            .readValue(
                Json31.mapper().writeValueAsString(document),
                new TypeReference<LinkedHashMap<String, Object>>() {});
    Map<String, Object> roundTripArray =
        map(map(map(schemas(jsonRoundTrip).get("ItemsRead")).get("properties")).get("rows"));
    List<?> roundTripFields = (List<?>) roundTripArray.get("x-rspace-fields");
    Map<String, Object> roundTripName = map(roundTripFields.get(0));
    assertEquals(Map.of(), roundTripName.get("writeConstraints"));
    assertEquals(List.of(), roundTripName.get("fields"));
    assertEquals(Map.of(), map(roundTripName.get("type")).get("structure"));
    Map<String, Object> roundTripCount =
        map(((List<?>) map(roundTripFields.get(1)).get("fields")).get(0));
    assertEquals(Map.of("minimum", 1, "maximum", 7), roundTripCount.get("writeConstraints"));
  }

  @Test
  void collectionsDeclaringOneRowTypeAlikeShareItsReadComponent() {
    Map<String, Object> schemas =
        schemas(
            document(
                List.of(description("items", "rows", ROWS), description("others", "entries", ROWS)),
                false));

    for (var property : List.of(List.of("ItemsRead", "rows"), List.of("OthersRead", "entries"))) {
      Map<String, Object> array =
          map(map(map(schemas.get(property.get(0))).get("properties")).get(property.get(1)));
      assertEquals(Map.of("$ref", ROW_REF), array.get("items"));
    }
    Map<String, Object> row = map(schemas.get("DeclaredApiV2DeclaredArrayOpenApiTestRow"));
    assertEquals(false, row.get("additionalProperties"));
    assertEquals(
        Set.of("name", "group", "children", "createOnly"), map(row.get("properties")).keySet());
    assertTrue(map(map(row.get("properties")).get("group")).containsKey("properties"));
    Map<String, Object> created =
        map(map(map(schemas.get("ItemsCreate")).get("properties")).get("rows"));
    assertTrue(map(created.get("items")).containsKey("properties"));
  }

  @Test
  void differentDeclarationsOfOneRowTypeEachGetTheirOwnComponent() {
    Map<String, Object> schemas =
        schemas(
            document(
                List.of(
                    description("items", "rows", ROWS), description("others", "entries", rows(20))),
                false));

    assertFalse(schemas.containsKey("DeclaredApiV2DeclaredArrayOpenApiTestRow"));
    assertEquals(
        Map.of("$ref", "#/components/schemas/ItemsRows"),
        map(map(map(schemas.get("ItemsRead")).get("properties")).get("rows")).get("items"));
    assertEquals(
        Map.of("$ref", "#/components/schemas/OthersEntries"),
        map(map(map(schemas.get("OthersRead")).get("properties")).get("entries")).get("items"));
    assertEquals(
        20,
        map(map(map(schemas.get("OthersEntries")).get("properties")).get("name")).get("maxLength"));
  }

  @Test
  void nullableDeclaredObjectReferencesItsComponentOrNull() {
    Map<String, Object> detail =
        map(
            map(map(schemas(document(description("rows"), false)).get("ItemsRead"))
                    .get("properties"))
                .get("detail"));

    assertEquals(
        List.of(
            Map.of("$ref", "#/components/schemas/DeclaredApiV2DeclaredArrayOpenApiTestDetail"),
            Map.of("type", "null")),
        detail.get("anyOf"));
    assertFalse(detail.containsKey("properties"));
    assertTrue(detail.containsKey("x-rspace-fields"));
  }

  @Test
  void readOnlyObjectPublishesItsTypeAsAComponentWithFieldMetadata() {
    Map<String, Object> schemas = schemas(document(description("rows"), false));
    Map<String, Object> summary =
        map(map(map(schemas.get("ItemsRead")).get("properties")).get("summary"));

    assertEquals("#/components/schemas/ApiV2DeclaredArrayOpenApiTestSummary", summary.get("$ref"));
    assertEquals(true, summary.get("readOnly"));
    assertTrue(summary.containsKey("x-rspace-access"));
    assertEquals(
        "Rows counted",
        map(map(map(schemas.get("ApiV2DeclaredArrayOpenApiTestSummary")).get("properties"))
                .get("rows"))
            .get("description"));
    assertFalse(map(map(schemas.get("ItemsCreate")).get("properties")).containsKey("summary"));
  }

  @Test
  void resourceAccessReplacementMatchesItsPublishedSchemaContract() {
    Map<String, Object> schemas = schemas(document(description("rows"), true));

    assertEquals(
        Map.of(
            "type",
            "object",
            "additionalProperties",
            false,
            "required",
            List.of("assignments"),
            "properties",
            Map.of(
                "assignments",
                Map.of(
                    "type",
                    "array",
                    "items",
                    Map.of("$ref", "#/components/schemas/ResourceAccessGrant")))),
        schemas.get("ResourceAccessReplacement"));
  }

  @Test
  void publishesStructuredExamplesAsJsonValuesAndKeepsStringExamplesAsRawText() {
    Map<String, Object> schemas = schemas(document(description("rows"), false));
    List<Map<String, Object>> expectedExample =
        List.of(Map.of("name", "ok", "group", Map.of("count", 3)));
    List<Map<String, Object>> expectedAdditionalExample =
        List.of(Map.of("name", "next", "group", Map.of("count", 4)));
    for (String operation : List.of("Read", "Create", "Update")) {
      Map<String, Object> array =
          map(map(map(schemas.get("Items" + operation)).get("properties")).get("rows"));
      assertEquals(expectedExample, array.get("example"), operation);
      assertEquals(List.of(expectedAdditionalExample), array.get("examples"), operation);
      assertEquals(
          List.of(new Row("ok", new Detail(3, "private"), List.of(new Detail(2, "private")), null)),
          ROWS.parse(Json31.mapper().valueToTree(array.get("example")), WriteOperation.UPDATE));
      Map<String, Object> children = map(resolved(schemas, array.get("items")).get("properties"));
      assertEquals("123", map(children.get("name")).get("example"), operation);
      assertEquals(
          List.of("true", "null", "\"abc\""), map(children.get("name")).get("examples"), operation);
      assertEquals(Map.of("count", 3), map(children.get("group")).get("example"), operation);
      assertEquals(
          List.of(Map.of("count", 4)), map(children.get("group")).get("examples"), operation);
      assertEquals(
          List.of(Map.of("count", 5)), map(children.get("children")).get("example"), operation);
      assertEquals(
          List.of(List.of(Map.of("count", 6))),
          map(children.get("children")).get("examples"),
          operation);
    }
  }

  private static CollectionDescription<Item> description(String name) {
    return description("items", name, ROWS);
  }

  private static CollectionDescription<Item> description(
      String resource, String name, DeclaredFieldType<List<Row>> rows) {
    return new CollectionDescription<>(
        resource,
        Item.class,
        List.of(
            Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), Item::id),
            Field.readOnly("detail", "detail", DETAIL, (Item item) -> new Detail(1, "private"))
                .allowNull(),
            Field.readOnly(
                "summary",
                "summary",
                CollectionFieldTypes.readOnlyObject(Summary.class),
                (Item item) -> new Summary(1)),
            Field.writable(name, "rows", rows, Item::rows, (item, value) -> {})
                .allowNull()
                .defaultValue(
                    List.of(
                        new Row(
                            "example",
                            new Detail(3, "private"),
                            List.of(new Detail(2, "private")),
                            "creation")))
                .documented(
                    OpenApiSchemaDocumentation.builder()
                        .example("[{\"name\":\"ok\",\"group\":{\"count\":3}}]")
                        .additionalExample("[{\"name\":\"next\",\"group\":{\"count\":4}}]")
                        .build())),
        "id",
        List.of(new Sort("id", true)));
  }

  private static Map<String, Object> document(
      CollectionDescription<Item> description, boolean protectedResource) {
    return document(List.of(description), protectedResource);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> document(
      List<CollectionDescription<Item>> descriptions, boolean protectedResource) {
    List<ApiV2ResourceSpec<?, ?>> specs = new ArrayList<>();
    for (CollectionDescription<Item> description : descriptions) {
      ResourceOperations<Item, Long> operations = mock(ResourceOperations.class);
      ProtectedResourceAccess<Item, Long> access = mock(ProtectedResourceAccess.class);
      var builder =
          ApiV2ResourceSpec.builder(description, operations, Long::valueOf)
              .mutationErrors("create-error", "update-error")
              .expose(EnumSet.range(ResourceOperation.LIST, ResourceOperation.BULK_DELETE));
      if (protectedResource) builder.resourceAccess(new ResourceAccessSpec<>(access));
      specs.add(builder.build());
    }
    return Json31.mapper()
        .convertValue(
            new ApiV2OpenApiGenerator(
                    new ApiV2ResourceCatalog(specs),
                    ApiV2OpenApiTestText.english(),
                    "Test API",
                    "2.0.0")
                .generate(),
            new TypeReference<LinkedHashMap<String, Object>>() {});
  }

  private static Map<String, Object> schemas(Map<String, Object> document) {
    return map(map(document.get("components")).get("schemas"));
  }

  private static Map<String, Object> resolved(Map<String, Object> schemas, Object schema) {
    Object ref = map(schema).get("$ref");
    return ref == null
        ? map(schema)
        : map(schemas.get(String.valueOf(ref).substring("#/components/schemas/".length())));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object value) {
    return (Map<String, Object>) value;
  }

  @SuppressWarnings("unchecked")
  private static List<Detail> details(Object value) {
    return (List<Detail>) value;
  }

  private record Detail(Integer count, String privateValue) {}

  private record Row(String name, Detail group, List<Detail> children, String createOnly) {}

  private record Summary(@Schema(description = "Rows counted") int rows) {}

  private record Item(Long id, List<Row> rows) {}
}
