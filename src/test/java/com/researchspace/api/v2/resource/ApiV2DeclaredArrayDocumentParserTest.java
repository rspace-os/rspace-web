package com.researchspace.api.v2.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.AccessFunction;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionFieldTypes;
import com.researchspace.model.collection.DeclaredFieldType;
import com.researchspace.model.collection.DocumentValidationException;
import com.researchspace.model.collection.DocumentValidationException.Reason;
import com.researchspace.model.collection.DocumentValidationException.Violation;
import com.researchspace.model.collection.Field;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.Sort;
import com.researchspace.model.collection.WriteOperation;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApiV2DeclaredArrayDocumentParserTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Field<Row, String> NAME =
      Field.embedded("name", CollectionFieldTypes.text(10), Row::name).required();
  private static final Field<Row, Integer> NUMBER =
      Field.embedded("number", CollectionFieldTypes.integer(), Row::number).defaultValue(2);
  private static final DeclaredFieldType<List<Row>> ROWS =
      CollectionFieldTypes.array(
          Row.class,
          List.of(NAME, NUMBER),
          values -> new Row((String) values.get("name"), (Integer) values.get("number")));

  @Test
  void replacementsRequireChildrenAndApplyChildDefaultsOnBothWriteOperations() throws Exception {
    CollectionDescription<Item> items = items(ROWS);
    for (WriteOperation operation : WriteOperation.values()) {
      ParsedDocument parsed = parse("{\"rows\":[{\"name\":\"first\"}]}", items, operation);
      assertEquals(List.of(new Row("first", 2)), parsed.values().get("rows"));
      assertViolation(
          "{\"rows\":[{\"name\":\"first\"},{}]}",
          items,
          operation,
          "rows[1].name",
          Reason.REQUIRED);
    }
    ParsedDocument omitted = parse("{\"label\":\"changed\"}", items, WriteOperation.UPDATE);
    assertFalse(omitted.values().containsKey("rows"));
    assertEquals(
        List.of(), parse("{\"rows\":[]}", items, WriteOperation.UPDATE).values().get("rows"));
    assertViolation(
        "{\"rows\":null}", items, WriteOperation.UPDATE, "rows", Reason.NULL_NOT_ALLOWED);
    assertViolation(
        "{\"rows\":[{\"name\":\"first\",\"extra\":1}]}",
        items,
        WriteOperation.UPDATE,
        "rows[0].extra",
        Reason.UNKNOWN_FIELD);
    assertViolation(
        "{\"rows\":[{\"name\":\"too-long-name\"}]}",
        items,
        WriteOperation.UPDATE,
        "rows[0].name",
        Reason.INVALID_VALUE);
    for (String row : List.of("1", "null", "[]")) {
      assertViolation(
          "{\"rows\":[" + row + "]}", items, WriteOperation.UPDATE, "rows[0]", Reason.WRONG_TYPE);
    }
  }

  @Test
  void childAccessReceivesTheFullParsedInputAndCannotWidenTheParentGate() throws Exception {
    DeclaredFieldType<List<Row>> gated =
        CollectionFieldTypes.array(
            Row.class,
            List.of(
                NAME.updatableBy(
                    AccessFunction.when(
                        context ->
                            context.targets(9L)
                                && "allowed".equals(context.requireInput().values().get("label")))),
                NUMBER.writableBy(AccessFunction.never())),
            values -> new Row((String) values.get("name"), (Integer) values.get("number")));
    CollectionDescription<Item> items = items(gated);
    ParsedDocument parsed =
        parse(
            "{\"rows\":[{\"name\":\"first\"}],\"label\":\"allowed\"}",
            items,
            WriteOperation.UPDATE);
    assertEquals(List.of(new Row("first", 2)), parsed.values().get("rows"));
    assertViolation(
        "{\"rows\":[{\"name\":\"first\"}],\"label\":\"denied\"}",
        items,
        WriteOperation.UPDATE,
        "rows[0].name",
        Reason.READ_ONLY);
    assertViolation(
        "{\"rows\":[{\"name\":\"first\",\"number\":2}],\"label\":\"allowed\"}",
        items,
        WriteOperation.UPDATE,
        "rows[0].number",
        Reason.READ_ONLY);
    CollectionDescription<Item> parentDenied =
        description(
            Field.writable("rows", "rows", ROWS, Item::rows, (item, value) -> {})
                .updatableBy(AccessFunction.never()));
    assertViolation(
        "{\"rows\":[{\"name\":\"first\"}]}",
        parentDenied,
        WriteOperation.UPDATE,
        "rows",
        Reason.READ_ONLY);
  }

  @Test
  void nestedGroupsAndArraysReportIndexedChildFailures() throws Exception {
    DeclaredFieldType<List<Group>> groups =
        CollectionFieldTypes.array(
            Group.class,
            List.of(Field.embedded("rows", ROWS, Group::rows).required()),
            values -> new Group(rows(values.get("rows"))));
    DeclaredFieldType<Envelope> group =
        CollectionFieldTypes.object(
            Envelope.class,
            List.of(Field.embedded("groups", groups, Envelope::groups)),
            values -> new Envelope(groups(values.get("groups"))));
    CollectionDescription<Item> items =
        new CollectionDescription<>(
            "items",
            Item.class,
            List.of(
                Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), Item::id),
                Field.writable("group", "group", group, ignored -> null, (item, value) -> {})),
            "id",
            List.of(new Sort("id", true)));
    assertViolation(
        "{\"group\":{\"groups\":[{\"rows\":[{}]}]}}",
        items,
        WriteOperation.UPDATE,
        "group.groups[0].rows[0].name",
        Reason.REQUIRED);
  }

  @Test
  void bulkCreatesPrefixChildPathsAndCheckRowAndWholeArrayRules() throws Exception {
    DeclaredFieldType<List<Row>> bounded =
        CollectionFieldTypes.array(
            Row.class,
            List.of(NAME, NUMBER),
            values -> new Row((String) values.get("name"), (Integer) values.get("number")),
            row -> {
              if (row.number() < 1) throw new IllegalArgumentException("Positive number required");
            },
            rows -> {
              if (rows.stream().map(Row::name).distinct().count() != rows.size()) {
                throw new IllegalArgumentException("Duplicate names");
              }
            },
            1,
            2);
    CollectionDescription<Item> items = items(bounded);
    assertViolation(
        "{\"rows\":[{\"name\":\"first\",\"number\":0}]}",
        items,
        WriteOperation.UPDATE,
        "rows[0]",
        Reason.INVALID_VALUE);
    assertViolation(
        "{\"rows\":[{\"name\":\"first\"},{\"name\":\"first\"}]}",
        items,
        WriteOperation.UPDATE,
        "rows",
        Reason.INVALID_VALUE);
    assertViolation("{\"rows\":[]}", items, WriteOperation.CREATE, "rows", Reason.INVALID_VALUE);
    JsonNode body = JSON.readTree("{\"docs\":[{\"rows\":[{}]}]}");
    DocumentValidationException failure =
        assertThrows(
            DocumentValidationException.class,
            () ->
                ApiV2DocumentParser.parseMany(
                    body, items, "error", context(WriteOperation.CREATE)));
    assertEquals(
        List.of(new Violation("docs[0].rows[0].name", Reason.REQUIRED)), failure.getViolations());
  }

  private static CollectionDescription<Item> items(DeclaredFieldType<List<Row>> type) {
    return description(Field.writable("rows", "rows", type, Item::rows, (item, value) -> {}));
  }

  private static CollectionDescription<Item> description(Field<Item, List<Row>> rows) {
    return new CollectionDescription<>(
        "items",
        Item.class,
        List.of(
            Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), Item::id),
            rows,
            Field.writable(
                "label", "label", CollectionFieldTypes.text(), Item::label, (item, value) -> {})),
        "id",
        List.of(new Sort("id", true)));
  }

  private static ParsedDocument parse(
      String body, CollectionDescription<Item> items, WriteOperation operation) throws Exception {
    return ApiV2DocumentParser.parse(
        JSON.readTree(body), items, operation, "error", context(operation));
  }

  private static void assertViolation(
      String body,
      CollectionDescription<Item> items,
      WriteOperation operation,
      String path,
      Reason reason)
      throws Exception {
    JsonNode input = JSON.readTree(body);
    DocumentValidationException failure =
        assertThrows(
            DocumentValidationException.class,
            () -> ApiV2DocumentParser.parse(input, items, operation, "error", context(operation)));
    assertEquals(List.of(new Violation(path, reason)), failure.getViolations());
  }

  private static AccessContext context(WriteOperation operation) {
    return new AccessContext(
        null,
        operation == WriteOperation.CREATE
            ? AccessContext.Operation.CREATE
            : AccessContext.Operation.UPDATE,
        "items",
        9L);
  }

  @SuppressWarnings("unchecked")
  private static List<Row> rows(Object value) {
    return (List<Row>) value;
  }

  @SuppressWarnings("unchecked")
  private static List<Group> groups(Object value) {
    return (List<Group>) value;
  }

  private record Row(String name, Integer number) {}

  private record Group(List<Row> rows) {}

  private record Envelope(List<Group> groups) {}

  private record Item(Long id, List<Row> rows, String label) {}
}
