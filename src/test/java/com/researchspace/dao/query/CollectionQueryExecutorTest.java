package com.researchspace.dao.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blazebit.persistence.CriteriaBuilder;
import com.blazebit.persistence.CriteriaBuilderFactory;
import com.blazebit.persistence.PagedList;
import com.blazebit.persistence.PaginatedCriteriaBuilder;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionDescription.Field;
import com.researchspace.model.collection.CollectionDescription.Operator;
import com.researchspace.model.collection.CollectionDescription.Sort;
import com.researchspace.model.collection.CollectionFieldTypes;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import java.util.List;
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
          List.of(),
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
    PaginatedCriteriaBuilder<Widget> paginated = erasedMock(PaginatedCriteriaBuilder.class);
    PagedList<Widget> page = erasedMock(PagedList.class);
    Widget third = new Widget(3L, "Grace");
    Widget fourth = new Widget(4L, "Linus");
    when(query.page(2, 2)).thenReturn(paginated);
    when(paginated.getResultList()).thenReturn(page);
    when(page.getTotalSize()).thenReturn(5L);
    when(page.toArray()).thenReturn(new Object[] {third, fourth});

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
    PaginatedCriteriaBuilder<Widget> paginated = erasedMock(PaginatedCriteriaBuilder.class);
    PagedList<Widget> page = erasedMock(PagedList.class);
    when(query.page(0, 1)).thenReturn(paginated);
    when(paginated.getResultList()).thenReturn(page);
    when(page.getTotalSize()).thenReturn(7L);

    assertEquals(7L, executor.count(factory, session, request(null, List.of(), 1, 20)));
    verify(query).orderByAsc("item.entityId");
  }

  @Test
  void returnsAnEmptyPageWithAnAccurateTotalWhenTheOffsetExceedsBlazesIntegerLimit() {
    PaginatedCriteriaBuilder<Widget> paginated = erasedMock(PaginatedCriteriaBuilder.class);
    PagedList<Widget> countPage = erasedMock(PagedList.class);
    when(query.page(0, 1)).thenReturn(paginated);
    when(paginated.getResultList()).thenReturn(countPage);
    when(countPage.getTotalSize()).thenReturn(7L);

    ResourcePage<Widget> result =
        executor.page(factory, session, request(null, List.of(), Integer.MAX_VALUE, 2));

    assertEquals(List.of(), result.resources());
    assertEquals(7L, result.total());
    verify(query).orderByAsc("item.entityId");
  }

  @SuppressWarnings("unchecked") // Mockito has no Class token for parameterized third-party APIs.
  private static <T> T erasedMock(Class<?> rawType) {
    return (T) mock(rawType);
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
}
