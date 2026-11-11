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
