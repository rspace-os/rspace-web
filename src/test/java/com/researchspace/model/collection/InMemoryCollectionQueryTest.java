package com.researchspace.model.collection;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.model.collection.CollectionDescription.Field;
import com.researchspace.model.collection.CollectionDescription.Operator;
import com.researchspace.model.collection.CollectionDescription.Sort;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryCollectionQueryTest {

  private static final CollectionDescription<Widget> WIDGETS =
      new CollectionDescription<>(
          "widgets",
          Widget.class,
          List.of(
              Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), Widget::id),
              Field.readOnly("name", "name", CollectionFieldTypes.text(), Widget::name),
              Field.readOnly("enabled", "enabled", CollectionFieldTypes.bool(), Widget::enabled)
                  .allowNull()),
          List.of(),
          "id",
          List.of(new Sort("name", true), new Sort("id", true)));

  private final InMemoryCollectionQuery<Widget> query = new InMemoryCollectionQuery<>(WIDGETS);
  private final List<Widget> widgets =
      List.of(
          new Widget(1L, "Alpha rotor", true),
          new Widget(2L, "Beta rotor", false),
          new Widget(3L, "alpha tray", null));

  @Test
  void filtersSortsAndPagesAResourceSnapshot() {
    FilterExpression filter =
        new FilterExpression.And(
            List.of(
                comparison("name", Operator.EQUAL, List.of("*rotor"), true),
                comparison("enabled", Operator.IN, List.of(true, false), false)));
    ResourceRequest request = request(filter, List.of(new Sort("name", false)), 1, 1);

    assertEquals(new ResourcePage<>(List.of(widgets.get(1)), 2), query.page(widgets, request));
    assertEquals(2, query.count(widgets, request));
  }

  @Test
  void supportsTextAndLogicalOperators() {
    FilterExpression filter =
        new FilterExpression.Or(
            List.of(
                comparison("name", Operator.LIKE, List.of("tray alpha"), false),
                comparison("name", Operator.CONTAINS, List.of("BETA"), false)));

    // "alpha tray" sorts before "Beta rotor": ordering ignores case, as the database collation
    // does.
    assertEquals(
        List.of(widgets.get(2), widgets.get(1)),
        query.page(widgets, request(filter, WIDGETS.defaultSort(), 1, 20)).resources());
  }

  @Test
  void comparesTextWithoutCase() {
    assertEquals(
        List.of(widgets.get(0)),
        matching(comparison("name", Operator.EQUAL, List.of("ALPHA ROTOR"), false)),
        "the database matches this row with a case-insensitive collation");
    assertEquals(
        List.of(widgets.get(1)),
        matching(comparison("name", Operator.IN, List.of("beta ROTOR"), false)));
    assertEquals(
        List.of(widgets.get(0), widgets.get(2)),
        matching(comparison("name", Operator.EQUAL, List.of("ALPHA*"), true)));
  }

  /** An accented value cannot match ASCII text, so the caller gets an empty page. */
  @Test
  void doesNotFoldAccents() {
    assertEquals(
        List.of(), matching(comparison("name", Operator.CONTAINS, List.of("álpha"), false)));
  }

  @Test
  void appliesSqlNullSemanticsAndExistsChecks() {
    FilterExpression notFalse = comparison("enabled", Operator.NOT_EQUAL, List.of(false), false);
    FilterExpression missing = comparison("enabled", Operator.EXISTS, List.of(false), false);

    assertEquals(1, query.count(widgets, request(notFalse, List.of(), 1, 20)));
    assertEquals(1, query.count(widgets, request(missing, List.of(), 1, 20)));
  }

  @Test
  void returnsAnEmptyPagePastTheEnd() {
    assertEquals(
        new ResourcePage<>(List.of(), 3),
        query.page(widgets, request(null, WIDGETS.defaultSort(), Integer.MAX_VALUE, 100)));
  }

  private List<Widget> matching(FilterExpression filter) {
    return query.page(widgets, request(filter, WIDGETS.defaultSort(), 1, 20)).resources();
  }

  private static FilterExpression.Comparison comparison(
      String field, Operator operator, List<Object> values, boolean wildcard) {
    return new FilterExpression.Comparison(field, operator, values, wildcard);
  }

  private static ResourceRequest request(
      FilterExpression filter, List<Sort> sort, int page, int limit) {
    return new ResourceRequest(
        filter,
        sort,
        new ResourceRequest.Page(page, limit),
        FieldSelection.all(),
        IncludeTree.empty());
  }

  private record Widget(Long id, String name, Boolean enabled) {}
}
