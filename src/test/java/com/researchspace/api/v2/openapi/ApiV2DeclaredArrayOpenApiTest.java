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
import com.researchspace.model.collection.CollectionMutationLimits;
import com.researchspace.model.collection.DeclaredFieldType;
import com.researchspace.model.collection.Field;
import com.researchspace.model.collection.Sort;
import com.researchspace.model.collection.WriteOperation;
import com.researchspace.service.resourceaccess.ProtectedResourceAccess;
import io.swagger.v3.core.util.Json31;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
  private static final DeclaredFieldType<List<Row>> ROWS =
      CollectionFieldTypes.array(
          Row.class,
          List.of(
              Field.embedded("name", CollectionFieldTypes.text(10), Row::name).required(),
              Field.embedded("group", DETAIL, Row::group).required(),
              Field.embedded("children", DETAILS, Row::children)
                  .defaultValue(List.of(new Detail(2, "private"))),
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

  @Test
  void generatedSchemasPreserveRecursiveChildrenDefaultsAndReplacementRequirements() {
    Map<String, Object> schemas = schemas(document(description("rows"), false));
    for (String operation : List.of("Read", "Create", "Update")) {
      Map<String, Object> resource = map(schemas.get("Items" + operation));
      Map<String, Object> array = map(map(resource.get("properties")).get("rows"));
      assertEquals(List.of("array", "null"), array.get("type"), operation);
      assertEquals(1, array.get("minItems"), operation);
      assertEquals(7, array.get("maxItems"), operation);
      Map<String, Object> item = map(array.get("items"));
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
  void protectedRoleSourceComponentUsesDeclaredChildrenAndRetainsItsNamedReference() {
    Map<String, Object> schemas = schemas(document(description("roleSources"), true));
    Map<String, Object> array =
        map(map(map(schemas.get("ItemsRead")).get("properties")).get("roleSources"));
    assertEquals(Map.of("$ref", "#/components/schemas/ResourceRoleSource"), array.get("items"));
    Map<String, Object> row = map(schemas.get("ResourceRoleSource"));
    assertEquals(false, row.get("additionalProperties"));
    assertEquals(
        Set.of("name", "group", "children", "createOnly"), map(row.get("properties")).keySet());
    assertTrue(map(map(row.get("properties")).get("group")).containsKey("properties"));
  }

  private static CollectionDescription<Item> description(String name) {
    return new CollectionDescription<>(
        "items",
        Item.class,
        List.of(
            Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), Item::id),
            Field.writable(name, "rows", ROWS, Item::rows, (item, value) -> {})
                .allowNull()
                .defaultValue(
                    List.of(
                        new Row(
                            "example",
                            new Detail(3, "private"),
                            List.of(new Detail(2, "private")),
                            "creation")))),
        "id",
        List.of(new Sort("id", true)));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> document(
      CollectionDescription<Item> description, boolean protectedResource) {
    ResourceOperations<Item, Long> operations = mock(ResourceOperations.class);
    ProtectedResourceAccess<Item, Long> access = mock(ProtectedResourceAccess.class);
    ApiV2ResourceSpec<Item, Long> spec =
        new ApiV2ResourceSpec<>(
            description,
            operations,
            Long::valueOf,
            "create-error",
            "update-error",
            EnumSet.allOf(ResourceOperation.class),
            Map.of(),
            Map.of(),
            CollectionMutationLimits.DEFAULT,
            List.of(),
            protectedResource ? Optional.of(new ResourceAccessSpec<>(access)) : Optional.empty());
    return Json31.mapper()
        .convertValue(
            new ApiV2OpenApiGenerator(new ApiV2ResourceCatalog(List.of(spec)), "Test API", "2.0.0")
                .generate(),
            new TypeReference<LinkedHashMap<String, Object>>() {});
  }

  private static Map<String, Object> schemas(Map<String, Object> document) {
    return map(map(document.get("components")).get("schemas"));
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

  private record Item(Long id, List<Row> rows) {}
}
