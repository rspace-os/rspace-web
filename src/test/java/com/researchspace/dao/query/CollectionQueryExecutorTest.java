package com.researchspace.dao.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blazebit.persistence.CriteriaBuilder;
import com.blazebit.persistence.CriteriaBuilderFactory;
import com.blazebit.persistence.MultipleSubqueryInitiator;
import com.blazebit.persistence.PagedList;
import com.blazebit.persistence.PaginatedCriteriaBuilder;
import com.blazebit.persistence.SelectObjectBuilder;
import com.blazebit.persistence.SubqueryBuilder;
import com.blazebit.persistence.SubqueryInitiator;
import com.researchspace.dao.query.RsqlCollectionQuery.Predicate;
import com.researchspace.dao.query.RsqlCollectionQuery.Subquery;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionFieldTypes;
import com.researchspace.model.collection.Field;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.RelationshipReadAccess;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.Sort;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.Map;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CollectionQueryExecutorTest {

  private static final CollectionDescription<Widget> WIDGETS =
      new CollectionDescription<>(
          "widgets",
          Widget.class,
          List.of(
              Field.readOnly("id", "entityId", CollectionFieldTypes.longNumber(), Widget::entityId),
              Field.readOnly("displayName", "name", CollectionFieldTypes.text(), Widget::name)),
          "id",
          List.of(new Sort("id", true)));

  private final CriteriaBuilderFactory factory = mock(CriteriaBuilderFactory.class);
  private final Session session = mock(Session.class);
  private final CollectionQueryExecutor<Widget> executor =
      new CollectionQueryExecutor<>(Widget.class, WIDGETS, "item");
  private CriteriaBuilder<Widget> query;

  @BeforeEach
  void setUp() {
    query = erasedMock(CriteriaBuilder.class);
    when(factory.create(session, Widget.class, "item")).thenReturn(query);
  }

  @Test
  void pagesWithTranslatedFiltersAndMappedSortProperties() {
    Widget third = new Widget(3L, "Grace");
    Widget fourth = new Widget(4L, "Linus");
    PaginatedCriteriaBuilder<Widget> paginated = stubPage(query, 2, 2, 5, third, fourth);

    ResourcePage<Widget> result =
        executor.page(
            factory,
            session,
            request(
                new FilterExpression.Comparison(
                    "displayName", Operator.EQUAL, List.of("Ada"), false),
                List.of(new Sort("displayName", false)),
                2,
                2));

    assertEquals(5L, result.total());
    assertEquals(List.of(third, fourth), result.resources());
    verify(query).whereExpression("item.name = :rsql0");
    verify(query).setParameter("rsql0", "Ada");
    verify(query).orderBy("item.name", false);
    verify(query).page(2, 2);
    verify(paginated).withInlineCountQuery(false);
  }

  @Test
  void countsWithoutSelectingAnEntity() {
    stubCount(query, 7);

    assertEquals(7L, executor.count(factory, session, request(null, List.of(), 1, 20)));
    verify(query, never()).page(0, 1);
  }

  @Test
  void overflowCountsWithoutFetchingEntityPaths() {
    stubCount(query, 7);

    ResourcePage<Widget> result =
        executor.page(
            factory,
            session,
            request(null, List.of(), Integer.MAX_VALUE, 2),
            null,
            RelationshipReadAccess.none(),
            List.of("owner"));

    assertEquals(List.of(), result.resources());
    assertEquals(7L, result.total());
    verify(query, never()).fetch("item.owner");
    verify(query, never()).page(0, 1);
  }

  @Test
  void projectsScalarRowsWithTheSameFilterConstraintsSortAndPage() {
    CriteriaBuilder<WidgetRow> projection = scalarQuery();
    WidgetRow row = new WidgetRow(4L, "Ada");
    stubPage(projection, 2, 2, 5, row);
    ResourceRequest request =
        request(
                new FilterExpression.Comparison(
                    "displayName", Operator.EQUAL, List.of("Ada"), false),
                List.of(new Sort("displayName", false)),
                2,
                2)
            .restrict(
                new FilterExpression.Comparison("id", Operator.LESS_THAN, List.of(10L), false));
    Predicate restriction =
        executor.compileConstraint(
            new FilterExpression.Comparison("id", Operator.GREATER_THAN, List.of(1L), false));

    ResourcePage<WidgetRow> result =
        executor.projectedPage(
            factory,
            session,
            request,
            restriction,
            RelationshipReadAccess.none(),
            WidgetRow.class,
            List.of("entityId", "name"));

    assertEquals(List.of(row), result.resources());
    assertEquals(5L, result.total());
    verify(projection)
        .whereExpression(
            "(item.name = :rsql0) AND (item.entityId < :rsql1) AND (item.entityId > :rsqlAccess0)");
    verify(projection).setParameter("rsql0", "Ada");
    verify(projection).setParameter("rsql1", 10L);
    verify(projection).setParameter("rsqlAccess0", 1L);
    verify(projection).orderBy("item.name", false);
    verify(projection).page(2, 2);
    verify(factory, never()).create(session, Widget.class, "item");
  }

  @Test
  void scalarPageUsesTheMappedIdForOverflowCounts() {
    CriteriaBuilder<WidgetRow> projection = scalarQuery();
    stubCount(projection, 7);

    ResourcePage<WidgetRow> result =
        executor.projectedPage(
            factory,
            session,
            request(null, List.of(), Integer.MAX_VALUE, 2),
            null,
            RelationshipReadAccess.none(),
            WidgetRow.class,
            List.of("entityId", "name"));

    assertEquals(List.of(), result.resources());
    assertEquals(7L, result.total());
    verify(projection, never()).page(0, 1);
  }

  @ParameterizedTest
  @CsvSource({
    "1,0,false",
    "1,1,false",
    "1,2,false",
    "1,3,true",
    "2,3,true",
    "3,2,false",
    "3,1,false",
    "4,0,false"
  })
  void projectedSliceUsesOneExtraRowWithoutCounting(int pageNumber, int rowCount, boolean hasNext) {
    CriteriaBuilder<WidgetRow> projection = scalarQuery();
    PaginatedCriteriaBuilder<WidgetRow> paginated = erasedMock(PaginatedCriteriaBuilder.class);
    PagedList<WidgetRow> rows = erasedMock(PagedList.class);
    var values =
        java.util.stream.IntStream.range(0, rowCount)
            .mapToObj(i -> new WidgetRow((long) i, "Row " + i))
            .toList();
    when(projection.page((pageNumber - 1) * 2, 3)).thenReturn(paginated);
    when(paginated.withCountQuery(false)).thenReturn(paginated);
    when(paginated.getResultList()).thenReturn(rows);
    when(rows.size()).thenReturn(rowCount);
    when(rows.subList(0, Math.min(rowCount, 2)))
        .thenReturn(values.subList(0, Math.min(rowCount, 2)));

    var result =
        executor.projectedSlice(
            factory,
            session,
            request(null, List.of(new Sort("displayName", false)), pageNumber, 2),
            null,
            RelationshipReadAccess.none(),
            WidgetRow.class,
            List.of("entityId", "name"));

    assertEquals(values.subList(0, Math.min(rowCount, 2)), result.resources());
    assertEquals(hasNext, result.hasNext());
    verify(projection).orderBy("item.name", false);
    verify(projection, never()).getCountQuery();
    verify(rows, never()).getTotalSize();
  }

  @Test
  void projectedSliceOverflowRunsNeitherPageNorCount() {
    CriteriaBuilder<WidgetRow> projection = scalarQuery();
    var result =
        executor.projectedSlice(
            factory,
            session,
            request(null, List.of(), Integer.MAX_VALUE, 2),
            null,
            RelationshipReadAccess.none(),
            WidgetRow.class,
            List.of("entityId", "name"));
    assertEquals(List.of(), result.resources());
    assertEquals(false, result.hasNext());
    verify(projection, never()).getCountQuery();
    verify(projection, never()).page(0, 1);
  }

  @Test
  void listsAStableIdOrderedPrefix() {
    List<Widget> widgets = List.of(new Widget(1L, "Ada"));
    when(query.getResultList()).thenReturn(widgets);

    assertEquals(
        widgets, executor.listById(factory, session, request(null, List.of(), 1, 20), 1000));
    verify(query).orderByAsc("item.entityId");
    verify(query).setMaxResults(1000);
  }

  @Test
  void rejectsARestrictionThatWouldOverwriteTheCallerFilter() {
    ResourceRequest request =
        request(
            new FilterExpression.Comparison("displayName", Operator.EQUAL, List.of("Ada"), false),
            List.of(),
            1,
            20);
    Predicate restriction = new Predicate("item.name = :rsql0", Map.of("rsql0", "Grace"));

    assertThrows(
        IllegalArgumentException.class,
        () -> executor.count(factory, session, request, restriction));
    verify(query, never()).setParameter("rsql0", "Ada");
    verify(query, never()).setParameter("rsql0", "Grace");
  }

  private static void stubCount(CriteriaBuilder<?> builder, long total) {
    TypedQuery<Long> count = erasedMock(TypedQuery.class);
    when(builder.getCountQuery()).thenReturn(count);
    when(count.getSingleResult()).thenReturn(total);
  }

  private static <T> PaginatedCriteriaBuilder<T> stubPage(
      CriteriaBuilder<T> builder, int first, int max, long total, Object... rows) {
    PaginatedCriteriaBuilder<T> paginated = erasedMock(PaginatedCriteriaBuilder.class);
    PagedList<T> page = erasedMock(PagedList.class);
    when(builder.page(first, max)).thenReturn(paginated);
    when(paginated.withInlineCountQuery(false)).thenReturn(paginated);
    when(paginated.getResultList()).thenReturn(page);
    when(page.getTotalSize()).thenReturn(total);
    when(page.toArray()).thenReturn(rows);
    return paginated;
  }

  private CriteriaBuilder<WidgetRow> scalarQuery() {
    CriteriaBuilder<WidgetRow> projection = erasedMock(CriteriaBuilder.class);
    SelectObjectBuilder<CriteriaBuilder<WidgetRow>> selection =
        erasedMock(SelectObjectBuilder.class);
    when(factory.create(session, WidgetRow.class)).thenReturn(projection);
    when(projection.from(Widget.class, "item")).thenReturn(projection);
    when(projection.selectNew(WidgetRow.class)).thenReturn(selection);
    when(selection.with("item.entityId")).thenReturn(selection);
    when(selection.with("item.name")).thenReturn(selection);
    when(selection.end()).thenReturn(projection);
    return projection;
  }

  @SuppressWarnings("unchecked") // Mockito has no Class token for parameterized third-party APIs.
  private static <T> T erasedMock(Class<?> rawType) {
    return (T) mock(rawType);
  }

  @Test
  void recursivelyBuildsNestedExistsSubqueriesThroughBlaze() {
    SubqueryBuilder<?> outerBuilder = erasedMock(SubqueryBuilder.class);
    SubqueryInitiator<?> outerSubquery = erasedMock(SubqueryInitiator.class);
    MultipleSubqueryInitiator<?> outerInitiator = erasedMock(MultipleSubqueryInitiator.class);
    SubqueryBuilder<?> innerBuilder = erasedMock(SubqueryBuilder.class);
    SubqueryInitiator<?> innerSubquery = erasedMock(SubqueryInitiator.class);
    MultipleSubqueryInitiator<?> innerInitiator = erasedMock(MultipleSubqueryInitiator.class);
    stubCount(query, 1);

    doReturn(outerInitiator).when(query).whereExpressionSubqueries("EXISTS outerSub");
    doReturn(outerSubquery).when(outerInitiator).with("outerSub");
    doReturn(outerBuilder).when(outerSubquery).from(Widget.class, "outer");
    doReturn(outerBuilder).when(outerBuilder).select("1");
    doReturn(innerInitiator).when(outerBuilder).whereExpressionSubqueries("EXISTS innerSub");
    doReturn(innerSubquery).when(innerInitiator).with("innerSub");
    doReturn(innerBuilder).when(innerSubquery).from(Widget.class, "inner");
    doReturn(innerBuilder).when(innerBuilder).select("1");

    Subquery nested = new Subquery(Widget.class, "inner", "inner.entityId = outer.entityId");
    Subquery outer =
        new Subquery(Widget.class, "outer", "EXISTS innerSub", Map.of("innerSub", nested));
    Predicate restriction =
        new Predicate("EXISTS outerSub", Map.of("nestedParam", 7L), Map.of("outerSub", outer));

    assertEquals(
        1L, executor.count(factory, session, request(null, List.of(), 1, 20), restriction));

    verify(query).whereExpressionSubqueries("EXISTS outerSub");
    verify(outerBuilder).whereExpressionSubqueries("EXISTS innerSub");
    verify(outerBuilder, never()).whereExpression("EXISTS innerSub");
    verify(innerBuilder).whereExpression("inner.entityId = outer.entityId");
    verify(query).setParameter("nestedParam", 7L);
    verify(query, never()).page(0, 1);
  }

  private static ResourceRequest request(
      FilterExpression filter, List<Sort> sort, int page, int size) {
    return new ResourceRequest(
        filter,
        sort,
        new ResourceRequest.Page(page, size),
        FieldSelection.all(),
        IncludeTree.empty());
  }

  private record Widget(Long entityId, String name) {}

  private record WidgetRow(Long id, String name) {}
}
