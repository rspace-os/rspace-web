package com.researchspace.dao.query;

import com.blazebit.persistence.CriteriaBuilder;
import com.blazebit.persistence.CriteriaBuilderFactory;
import com.blazebit.persistence.MultipleSubqueryInitiator;
import com.blazebit.persistence.PagedList;
import com.researchspace.dao.query.RsqlCollectionQuery.Predicate;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionDescription.Sort;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.RelationshipReadAccess;
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
  private final RsqlCollectionQuery constraintQuery;
  private final RsqlCollectionQuery requestConstraintQuery;

  public CollectionQueryExecutor(
      Class<T> entityType, CollectionDescription<T> description, String alias) {
    this.entityType = Objects.requireNonNull(entityType, "Entity type");
    this.description = Objects.requireNonNull(description, "Collection description");
    if (alias == null || alias.isBlank()) {
      throw new IllegalArgumentException("Query alias must not be blank");
    }
    this.alias = alias;
    filterQuery = new RsqlCollectionQuery(description, alias);
    constraintQuery = new RsqlCollectionQuery(description, alias, "rsqlAccess");
    requestConstraintQuery = new RsqlCollectionQuery(description, alias, "rsqlRequestAccess");
  }

  public String alias() {
    return alias;
  }

  /**
   * Compiles a server-built access constraint against this collection's alias.
   *
   * <p>Separate from the caller's filter because a constraint may name an internal filter, which
   * {@link CollectionDescription#requirePublicFilterSelector} refuses and so a caller can never
   * reach. Expressing an access rule as a {@link FilterExpression} rather than as query text is
   * what lets the same rule be recompiled at another alias, such as inside a subquery.
   */
  public Predicate compileConstraint(FilterExpression constraint) {
    return constraintQuery.translateTrusted(constraint);
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
    return page(factory, session, request, restriction, RelationshipReadAccess.none());
  }

  /** As {@link #page}, resolving filters on a relationship target's own fields through targets. */
  public ResourcePage<T> page(
      CriteriaBuilderFactory factory,
      Session session,
      ResourceRequest request,
      Predicate restriction,
      RelationshipReadAccess targets) {
    long firstResult = (long) (request.page().number() - 1) * request.page().size();
    CriteriaBuilder<T> query = query(factory, session, request, restriction, targets);
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
    return count(factory, session, request, restriction, RelationshipReadAccess.none());
  }

  /** As {@link #count}, resolving filters on a relationship target's own fields through targets. */
  public long count(
      CriteriaBuilderFactory factory,
      Session session,
      ResourceRequest request,
      Predicate restriction,
      RelationshipReadAccess targets) {
    return totalMatching(query(factory, session, request, restriction, targets));
  }

  public List<T> listById(
      CriteriaBuilderFactory factory, Session session, ResourceRequest request, int limit) {
    return listById(factory, session, request, limit, RelationshipReadAccess.none());
  }

  /** As {@link #listById}, resolving filters on a relationship target's own fields. */
  public List<T> listById(
      CriteriaBuilderFactory factory,
      Session session,
      ResourceRequest request,
      int limit,
      RelationshipReadAccess targets) {
    CriteriaBuilder<T> query = query(factory, session, request, null, targets);
    orderById(query);
    query.setMaxResults(limit);
    return query.getResultList();
  }

  /** Returns every matching row in the caller-requested stable sort order. */
  public List<T> list(CriteriaBuilderFactory factory, Session session, ResourceRequest request) {
    return list(factory, session, request, RelationshipReadAccess.none());
  }

  /** As {@link #list}, resolving filters on a relationship target's own fields. */
  public List<T> list(
      CriteriaBuilderFactory factory,
      Session session,
      ResourceRequest request,
      RelationshipReadAccess targets) {
    CriteriaBuilder<T> query = query(factory, session, request, null, targets);
    request.sort().forEach(sort -> applySort(query, sort));
    return query.getResultList();
  }

  private CriteriaBuilder<T> query(
      CriteriaBuilderFactory factory,
      Session session,
      ResourceRequest request,
      Predicate restriction,
      RelationshipReadAccess targets) {
    CriteriaBuilder<T> query = factory.create(session, entityType, alias);
    // Blaze ANDs each where expression, so the access restriction cannot widen the caller's filter.
    apply(query, filterQuery.translate(request.filter(), targets));
    apply(query, requestConstraintQuery.translateTrusted(request.serverConstraint(), targets));
    apply(query, restriction);
    return query;
  }

  private static void apply(CriteriaBuilder<?> query, Predicate predicate) {
    if (predicate == null) {
      return;
    }
    if (predicate.subqueries().isEmpty()) {
      query.whereExpression(predicate.expression());
    } else {
      // Blaze parses no subquery inside an expression, so each one is built through the initiator
      // and referenced from the expression by name.
      MultipleSubqueryInitiator<?> initiator =
          query.whereExpressionSubqueries(predicate.expression());
      predicate
          .subqueries()
          .forEach(
              (name, subquery) ->
                  initiator
                      .with(name)
                      .from(subquery.entityType(), subquery.alias())
                      .select("1")
                      .whereExpression(subquery.whereExpression())
                      .end());
      initiator.end();
    }
    predicate.apply(query);
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
