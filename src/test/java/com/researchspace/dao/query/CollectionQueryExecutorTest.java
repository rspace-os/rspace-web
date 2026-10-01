package com.researchspace.dao.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.List;
import java.util.Map;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    stubPage(query, 2, 2, 5, third, fourth);

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
  }

  @Test
  void countsThroughAPagedQueryOrderedByTheMappedIdProperty() {
    stubPage(query, 0, 1, 7);

    assertEquals(7L, executor.count(factory, session, request(null, List.of(), 1, 20)));
    verify(query).orderByAsc("item.entityId");
  }

  @Test
  void returnsAnEmptyPageWithAnAccurateTotalWhenTheOffsetExceedsBlazesIntegerLimit() {
    stubPage(query, 0, 1, 7);

    ResourcePage<Widget> result =
        executor.page(factory, session, request(null, List.of(), Integer.MAX_VALUE, 2));

    assertEquals(List.of(), result.resources());
    assertEquals(7L, result.total());
    verify(query).orderByAsc("item.entityId");
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
    verify(projection).whereExpression("item.name = :rsql0");
    verify(projection).setParameter("rsql0", "Ada");
    verify(projection).whereExpression("item.entityId < :rsqlRequestAccess0");
    verify(projection).setParameter("rsqlRequestAccess0", 10L);
    verify(projection).whereExpression("item.entityId > :rsqlAccess0");
    verify(projection).setParameter("rsqlAccess0", 1L);
    verify(projection).orderBy("item.name", false);
    verify(projection).page(2, 2);
    verify(factory, never()).create(session, Widget.class, "item");
  }

  @Test
  void scalarPageUsesTheMappedIdForOverflowCounts() {
    CriteriaBuilder<WidgetRow> projection = scalarQuery();
    stubPage(projection, 0, 1, 7);

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
    verify(projection).orderByAsc("item.entityId");
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

  private static <T> void stubPage(
      CriteriaBuilder<T> builder, int first, int max, long total, Object... rows) {
    PaginatedCriteriaBuilder<T> paginated = erasedMock(PaginatedCriteriaBuilder.class);
    PagedList<T> page = erasedMock(PagedList.class);
    when(builder.page(first, max)).thenReturn(paginated);
    when(paginated.getResultList()).thenReturn(page);
    when(page.getTotalSize()).thenReturn(total);
    when(page.toArray()).thenReturn(rows);
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
    PaginatedCriteriaBuilder<Widget> paginated = erasedMock(PaginatedCriteriaBuilder.class);
    PagedList<Widget> page = erasedMock(PagedList.class);

    doReturn(outerInitiator).when(query).whereExpressionSubqueries("EXISTS outerSub");
    doReturn(outerSubquery).when(outerInitiator).with("outerSub");
    doReturn(outerBuilder).when(outerSubquery).from(Widget.class, "outer");
    doReturn(outerBuilder).when(outerBuilder).select("1");
    doReturn(innerInitiator).when(outerBuilder).whereExpressionSubqueries("EXISTS innerSub");
    doReturn(innerSubquery).when(innerInitiator).with("innerSub");
    doReturn(innerBuilder).when(innerSubquery).from(Widget.class, "inner");
    doReturn(innerBuilder).when(innerBuilder).select("1");
    when(query.page(0, 1)).thenReturn(paginated);
    when(paginated.getResultList()).thenReturn(page);
    when(page.getTotalSize()).thenReturn(1L);

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
