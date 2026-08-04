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

  public ResourcePage<T> page(
      CriteriaBuilderFactory factory, Session session, ResourceRequest request) {
    long firstResult = (long) (request.page().number() - 1) * request.page().size();
    CriteriaBuilder<T> query = query(factory, session, request);
    if (firstResult > Integer.MAX_VALUE) {
      return new ResourcePage<>(List.of(), totalMatching(query));
    }
    request.sort().forEach(sort -> applySort(query, sort));
    PagedList<T> page = query.page((int) firstResult, request.page().size()).getResultList();
    return new ResourcePage<>(page, page.getTotalSize());
  }

  public long count(CriteriaBuilderFactory factory, Session session, ResourceRequest request) {
    return totalMatching(query(factory, session, request));
  }

  public List<T> listById(
      CriteriaBuilderFactory factory, Session session, ResourceRequest request, int limit) {
    CriteriaBuilder<T> query = query(factory, session, request);
    orderById(query);
    query.setMaxResults(limit);
    return query.getResultList();
  }

  private CriteriaBuilder<T> query(
      CriteriaBuilderFactory factory, Session session, ResourceRequest request) {
    CriteriaBuilder<T> query = factory.create(session, entityType, alias);
    Predicate predicate = filterQuery.translate(request.filter());
    if (predicate != null) {
      query.whereExpression(predicate.expression());
      predicate.apply(query);
    }
    return query;
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
