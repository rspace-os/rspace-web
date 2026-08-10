package com.researchspace.dao.query;

import com.blazebit.persistence.CriteriaBuilder;
import com.blazebit.persistence.CriteriaBuilderFactory;
import com.blazebit.persistence.PagedList;
import com.researchspace.dao.query.RsqlCollectionQuery.Predicate;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionDescription.Sort;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import java.util.List;
import java.util.Objects;
import org.hibernate.Session;

/** Executes standard typed collection reads against Blaze-Persistence. */
public final class CollectionQueryExecutor<T> {

  private final Class<T> entityType;
  private final CollectionDescription<T> description;
  private final String alias;
  private final RsqlCollectionQuery filterQuery;

  public CollectionQueryExecutor(
      Class<T> entityType, CollectionDescription<T> description, String alias) {
    this.entityType = Objects.requireNonNull(entityType, "Entity type");
    this.description = Objects.requireNonNull(description, "Collection description");
    if (alias == null || alias.isBlank()) {
      throw new IllegalArgumentException("Query alias must not be blank");
    }
    this.alias = alias;
    filterQuery = new RsqlCollectionQuery(description, alias);
  }

  public String alias() {
    return alias;
  }

  public ResourcePage<T> page(
      CriteriaBuilderFactory factory, Session session, ResourceRequest request) {
    return page(factory, session, request, null);
  }

  /**
   * Returns one page, narrowed by a restriction the API does not express.
   *
   * <p>A rule that fits the described fields belongs in the resource access policy, which the REST
   * layer folds into the request. This is for a rule that cannot: an entity permission model over
   * properties the API does not publish. The restriction is ANDed with the caller's filter, so it
   * bounds the rows and the total together.
   */
  public ResourcePage<T> page(
      CriteriaBuilderFactory factory,
      Session session,
      ResourceRequest request,
      Predicate restriction) {
    long firstResult = (long) (request.page().number() - 1) * request.page().size();
    CriteriaBuilder<T> query = query(factory, session, request, restriction);
    if (firstResult > Integer.MAX_VALUE) {
      return new ResourcePage<>(List.of(), totalMatching(query));
    }
    request.sort().forEach(sort -> applySort(query, sort));
    PagedList<T> page = query.page((int) firstResult, request.page().size()).getResultList();
    return new ResourcePage<>(page, page.getTotalSize());
  }

  public long count(CriteriaBuilderFactory factory, Session session, ResourceRequest request) {
    return count(factory, session, request, null);
  }

  /** Counts the rows a restricted {@link #page} would report as its total. */
  public long count(
      CriteriaBuilderFactory factory,
      Session session,
      ResourceRequest request,
      Predicate restriction) {
    return totalMatching(query(factory, session, request, restriction));
  }

  public List<T> listById(
      CriteriaBuilderFactory factory, Session session, ResourceRequest request, int limit) {
    CriteriaBuilder<T> query = query(factory, session, request, null);
    orderById(query);
    query.setMaxResults(limit);
    return query.getResultList();
  }

  /** Returns every matching row in the caller-requested stable sort order. */
  public List<T> list(CriteriaBuilderFactory factory, Session session, ResourceRequest request) {
    CriteriaBuilder<T> query = query(factory, session, request, null);
    request.sort().forEach(sort -> applySort(query, sort));
    return query.getResultList();
  }

  private CriteriaBuilder<T> query(
      CriteriaBuilderFactory factory,
      Session session,
      ResourceRequest request,
      Predicate restriction) {
    CriteriaBuilder<T> query = factory.create(session, entityType, alias);
    // Two conjuncts: Blaze ANDs each where expression, so the restriction cannot widen the filter.
    apply(query, filterQuery.translate(request.filter()));
    apply(query, restriction);
    return query;
  }

  private static void apply(CriteriaBuilder<?> query, Predicate predicate) {
    if (predicate != null) {
      query.whereExpression(predicate.expression());
      predicate.apply(query);
    }
  }

  /** Uses the page count because Blaze's root-count query fails when there are no restrictions. */
  private long totalMatching(CriteriaBuilder<T> query) {
    orderById(query);
    return query.page(0, 1).getResultList().getTotalSize();
  }

  private void applySort(CriteriaBuilder<T> query, Sort sort) {
    String property = description.requireField(sort.field()).property();
    query.orderBy(alias + "." + property, sort.ascending());
  }

  private void orderById(CriteriaBuilder<T> query) {
    String property = description.requireField(description.idField()).property();
    query.orderByAsc(alias + "." + property);
  }
}
