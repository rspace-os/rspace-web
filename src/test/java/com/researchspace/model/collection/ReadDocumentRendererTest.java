package com.researchspace.model.collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.model.User;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReadDocumentRendererTest {

  @Test
  void scalarDocumentsUseRowIdAndCallerAccessWithoutReadingEntities() {
    User actor = new User("reader");
    var rows =
        CollectionFieldTypes.array(
            Row.class,
            List.of(
                Field.embedded("name", CollectionFieldTypes.text(), Row::name).required(),
                Field.embedded("secret", CollectionFieldTypes.text(), Row::secret)
                    .required()
                    .readableBy(
                        AccessFunction.when(
                            context -> context.user() == actor && context.targets(1L)))),
            values -> new Row((String) values.get("name"), (String) values.get("secret")));
    CollectionDescription<Item> items = description(rows, AccessFunction.anyone());
    ResourceRenderer renderer = new ResourceRenderer(new ResourceRegistry(List.of(items)));
    List<Map<String, Object>> documents =
        List.of(
            Map.of("id", 1L, "rows", List.of(Map.of("name", "one", "secret", "private"))),
            Map.of("id", 2L, "rows", List.of(Map.of("name", "two", "secret", "private"))));

    assertEquals(
        List.of(
            Map.of("id", 1L, "rows", List.of(Map.of("name", "one", "secret", "private"))),
            Map.of("id", 2L, "rows", List.of(Map.of("name", "two")))),
        renderer.renderReadDocuments(
            documents,
            items,
            ignored -> FieldSelection.all(),
            new AccessContext(actor, AccessContext.Operation.READ, "items")));
    assertEquals(
        List.of(Map.of("id", 1L), Map.of("id", 2L)),
        renderer.renderReadDocuments(
            documents,
            items,
            ignored -> FieldSelection.include(Set.of("id")),
            new AccessContext(actor, AccessContext.Operation.READ, "items")));
  }

  @Test
  void validatesDeclaredValuesBeforeSelectionAndAccessCanHideThem() {
    var rows =
        CollectionFieldTypes.array(
            Row.class,
            List.of(
                Field.embedded("name", CollectionFieldTypes.text(3), Row::name)
                    .required()
                    .readableBy(AccessFunction.never())),
            values -> new Row((String) values.get("name"), null));
    CollectionDescription<Item> items = description(rows, AccessFunction.never());
    ResourceRenderer renderer = new ResourceRenderer(new ResourceRegistry(List.of(items)));
    AccessContext context = new AccessContext(null, AccessContext.Operation.READ, "items");

    for (Object invalid : List.of(List.of(Map.of("name", "too long")), List.of(Map.of()))) {
      assertThrows(
          EmbeddedFieldException.class,
          () ->
              renderer.renderReadDocuments(
                  List.of(Map.of("id", 1L, "rows", invalid)),
                  items,
                  ignored -> FieldSelection.include(Set.of("id")),
                  context));
    }
  }

  @Test
  void scalarRelationshipsBatchAuthorizeAndExpandWithoutLoadingEntities() {
    CollectionDescription<Item> items =
        new CollectionDescription<>(
            "items",
            Item.class,
            List.of(
                Field.<Item, Long>readOnly(
                    "id", "id", CollectionFieldTypes.longNumber(), item -> unread()),
                Field.<Item, String>readOnly(
                    "name", "name", CollectionFieldTypes.text(), item -> unread()),
                Relationship.referenceToOne(
                    "parent",
                    "items",
                    CollectionFieldTypes.longNumber(),
                    Item.class,
                    item -> unread(),
                    item -> unread(),
                    "parent.id")),
            "id",
            List.of(new Sort("id", true)));
    var registry = new ResourceRegistry(List.of(items));
    var renderer = new ResourceRenderer(registry);
    var batches = new java.util.ArrayList<Set<ResourceRenderer.TargetKey>>();
    ResourceRenderer.TargetResolver resolver =
        new ResourceRenderer.TargetResolver() {
          public java.util.Optional<ResourceRenderer.ResolvedTarget> resolve(
              String resource, Object id) {
            throw new AssertionError("Must resolve the complete page in batches");
          }

          public Map<
                  ResourceRenderer.TargetKey, java.util.Optional<ResourceRenderer.ResolvedTarget>>
              resolveAll(java.util.Collection<ResourceRenderer.TargetKey> keys) {
            batches.add(Set.copyOf(keys));
            var result =
                new java.util.LinkedHashMap<
                    ResourceRenderer.TargetKey,
                    java.util.Optional<ResourceRenderer.ResolvedTarget>>();
            for (var key : keys) {
              Map<String, Object> target =
                  key.id().equals(2L)
                      ? Map.of("id", 2L, "name", "Parent", "globalId", "IT2", "parent", 3L)
                      : Map.of("id", 3L, "name", "Ancestor", "globalId", "IT3");
              result.put(
                  key,
                  key.id().equals(9L)
                      ? java.util.Optional.empty()
                      : java.util.Optional.of(
                          ResourceRenderer.ResolvedTarget.lazy(
                              () -> unread(), FieldSelection.all(), target)));
            }
            return result;
          }
        };
    var context = new AccessContext(new User("reader"), AccessContext.Operation.READ, "items");
    var documents =
        List.of(
            Map.<String, Object>of("id", 1L, "parent", 2L),
            Map.<String, Object>of("id", 4L, "parent", 2L),
            Map.<String, Object>of("id", 5L, "parent", 9L));
    for (int depth : List.of(0, 1, 2)) {
      batches.clear();
      var rendered =
          renderer.renderReadDocuments(
              documents,
              items,
              ignored -> FieldSelection.all(),
              ResourceFieldSelections.root(FieldSelection.all()),
              IncludeTree.toDepth(items, registry, depth),
              resolver,
              context);
      org.junit.jupiter.api.Assertions.assertNull(rendered.get(2).get("parent"));
      assertEquals(rendered.get(0).get("parent"), rendered.get(1).get("parent"));
      var reference = (Map<?, ?>) rendered.get(0).get("parent");
      assertEquals("IT2", reference.get("globalId"));
      if (depth == 0) assertEquals(2L, reference.get("value"));
      else {
        var parent = (Map<?, ?>) reference.get("value");
        assertEquals("Parent", parent.get("name"));
        var ancestor = (Map<?, ?>) parent.get("parent");
        assertEquals(
            depth == 1 ? 3L : "Ancestor",
            depth == 1 ? ancestor.get("value") : ((Map<?, ?>) ancestor.get("value")).get("name"));
      }
      assertEquals(
          Set.of(
              new ResourceRenderer.TargetKey("items", 2L),
              new ResourceRenderer.TargetKey("items", 9L)),
          batches.get(0));
      assertEquals(depth == 0 ? 1 : 2, batches.size());
    }
    batches.clear();
    assertEquals(
        List.of(Map.of("id", 1L), Map.of("id", 4L), Map.of("id", 5L)),
        renderer.renderReadDocuments(
            documents,
            items,
            ignored -> FieldSelection.include(Set.of("id")),
            ResourceFieldSelections.root(FieldSelection.all()),
            IncludeTree.toDepth(items, registry, 2),
            resolver,
            context));
    assertEquals(List.of(), batches);
  }

  private static CollectionDescription<Item> description(
      DeclaredFieldType<List<Row>> rows, AccessFunction rowsReadAccess) {
    return new CollectionDescription<>(
        "items",
        Item.class,
        List.of(
            Field.<Item, Long>readOnly(
                "id", "id", CollectionFieldTypes.longNumber(), item -> unread()),
            Field.<Item, List<Row>>readOnly("rows", "rows", rows, item -> unread())
                .readableBy(rowsReadAccess)),
        "id",
        List.of(new Sort("id", true)));
  }

  private static <V> V unread() {
    throw new AssertionError("A scalar document must not invoke an entity field reader");
  }

  private record Row(String name, String secret) {}

  private record Item(Long id, List<Row> rows) {}
}
