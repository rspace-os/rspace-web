package com.researchspace.dao.query;

import com.blazebit.persistence.CriteriaBuilder;
import com.blazebit.persistence.CriteriaBuilderFactory;
import com.blazebit.persistence.PagedList;
import com.researchspace.dao.query.RsqlCollectionQuery.Predicate;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.QueryConstraint;
import com.researchspace.model.collection.RelationshipReadAccess;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.Sort;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Objects;
import org.hibernate.Session;

/** Executes standard typed collection reads against Blaze-Persistence. */
public final class CollectionQueryExecutor<T> {

  private final Class<T> entityType;
  private final CollectionDescription<T> description;
  private final String alias;
  private final RsqlCollectionQuery filterQuery;
  private final RsqlFieldPredicateCompilerRegistry compilers;

  public CollectionQueryExecutor(
      Class<T> entityType, CollectionDescription<T> description, String alias) {
    this(entityType, description, alias, RsqlFieldPredicateCompilerRegistry.builtIns());
  }

  public CollectionQueryExecutor(
      Class<T> entityType,
      CollectionDescription<T> description,
      String alias,
      RsqlFieldPredicateCompilerRegistry compilers) {
    this.compilers = Objects.requireNonNull(compilers, "Comparison compilers");
    this.entityType = Objects.requireNonNull(entityType, "Entity type");
    this.description = Objects.requireNonNull(description, "Collection description");
    if (alias == null || alias.isBlank()) {
      throw new IllegalArgumentException("Query alias must not be blank");
    }
    this.alias = alias;
    filterQuery = new RsqlCollectionQuery(description, alias, compilers);
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
  public Predicate compileConstraint(QueryConstraint constraint) {
    return new RsqlCollectionQuery(description, alias, "rsqlAccess", compilers)
        .translateTrusted(constraint);
  }

  /** Compiles the target read rule for one relationship against this collection's alias. */
  public Predicate compileReadableRelationship(
      String relationshipName, RelationshipReadAccess targets) {
    return new RsqlCollectionQuery(description, alias, "rsqlRelationshipAccess", compilers)
        .compileReadableRelationship(relationshipName, targets);
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
    return page(factory, session, request, restriction, targets, List.of());
  }

  /** As {@link #page}, fetch-joining entity paths without adding them to the count query. */
  public ResourcePage<T> page(
      CriteriaBuilderFactory factory,
      Session session,
      ResourceRequest request,
      Predicate restriction,
      RelationshipReadAccess targets,
      List<String> fetchPaths) {
    CriteriaBuilder<T> query = query(factory, session, request, restriction, targets);
    if (request.page().offset() > Integer.MAX_VALUE) {
      return new ResourcePage<>(List.of(), totalMatching(query));
    }
    fetchPaths.forEach(path -> query.fetch(alias + "." + path));
    return paged(query, request);
  }

  /**
   * Reads scalar constructor arguments without materializing the collection's entity aggregate.
   *
   * <p>Properties are trusted persistence paths, relative to this collection's alias. Projection
   * shares the entity path's predicates, sorting, pagination, overflow and total-count semantics.
   */
  public <R> ResourcePage<R> projectedPage(
      CriteriaBuilderFactory factory,
      Session session,
      ResourceRequest request,
      Predicate restriction,
      RelationshipReadAccess targets,
      Class<R> resultType,
      List<String> properties) {
    CriteriaBuilder<R> query = factory.create(session, resultType).from(entityType, alias);
    applyRequest(query, request, restriction, targets);
    var selection = query.selectNew(resultType);
    properties.forEach(property -> selection.with(alias + "." + property));
    selection.end();
    return paged(query, request);
  }

  private <R> ResourcePage<R> paged(CriteriaBuilder<R> query, ResourceRequest request) {
    long firstResult = request.page().offset();
    if (firstResult > Integer.MAX_VALUE) {
      return new ResourcePage<>(List.of(), totalMatching(query));
    }
    request.sort().forEach(sort -> applySort(query, sort));
    if (request.sort().isEmpty()) {
      orderById(query);
    }
    PagedList<R> page =
        query
            .page((int) firstResult, request.page().size())
            .withInlineCountQuery(false)
            .getResultList();
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

  /**
   * Returns an ascending-ID prefix capped by limit, ignoring request paging and presentation sort.
   * Bulk mutation callers request their maximum plus one and reject overflow before mutating;
   * applying a UI page here would hide an oversized command.
   */
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
    return byId(factory, session, request, limit, targets, false);
  }

  /** As {@link #listById}, locking every selected row for an atomic server-side command. */
  public List<T> listByIdForUpdate(
      CriteriaBuilderFactory factory,
      Session session,
      ResourceRequest request,
      int limit,
      RelationshipReadAccess targets) {
    return byId(factory, session, request, limit, targets, true);
  }

  private List<T> byId(
      CriteriaBuilderFactory factory,
      Session session,
      ResourceRequest request,
      int limit,
      RelationshipReadAccess targets,
      boolean forUpdate) {
    CriteriaBuilder<T> query = query(factory, session, request, null, targets);
    orderById(query);
    query.setMaxResults(limit);
    return forUpdate
        ? query.getQuery().setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList()
        : query.getResultList();
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
    applyRequest(query, request, restriction, targets);
    return query;
  }

  private void applyRequest(
      CriteriaBuilder<?> query,
      ResourceRequest request,
      Predicate restriction,
      RelationshipReadAccess targets) {
    RsqlCollectionQuery.Compilation compilation = filterQuery.newCompilation();
    Predicate predicate =
        Predicate.and(
            compilation.translate(request.filter(), targets, request.runtime()),
            compilation.translateTrusted(request.serverConstraint(), targets),
            restriction);
    if (predicate != null) {
      predicate.applyTo(query);
    }
  }

  /** Counts on the unpaginated builder without selecting an entity or its graph. */
  private long totalMatching(CriteriaBuilder<?> query) {
    return query.getCountQuery().getSingleResult();
  }

  private void applySort(CriteriaBuilder<?> query, Sort sort) {
    String property = description.requireField(sort.field()).property();
    query.orderBy(alias + "." + property, sort.ascending());
  }

  private void orderById(CriteriaBuilder<?> query) {
    String property = description.requireField(description.idField()).property();
    query.orderByAsc(alias + "." + property);
  }
}
