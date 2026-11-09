package com.researchspace.booking.dao;

import com.blazebit.persistence.CriteriaBuilderFactory;
import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.dao.query.CollectionQueryExecutor;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingConfigurationState;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.RelationshipReadAccess;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import jakarta.persistence.LockModeType;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.hibernate.SessionFactory;
import org.hibernate.StaleObjectStateException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

@Repository("bookingConfigurationDao")
public class BookingConfigurationDaoHibernate
    extends GenericDaoHibernate<BookingConfiguration, Long> implements BookingConfigurationDao {

  private final CriteriaBuilderFactory criteriaBuilderFactory;
  private final CollectionQueryExecutor<BookingConfiguration> collectionQuery;
  private final BookingItemQuery itemQuery;
  private final InstrumentDao instruments;
  private final BookingCalendarPermissionGateDao permissionGate;

  public BookingConfigurationDaoHibernate(
      SessionFactory sessionFactory,
      CriteriaBuilderFactory criteriaBuilderFactory,
      BookingItemQuery itemQuery,
      InstrumentDao instruments,
      BookingCalendarPermissionGateDao permissionGate,
      @Qualifier(
              com.researchspace.booking.config.BookingResourceAccessConfiguration
                  .BOOKING_CONFIGURATION_DESCRIPTION)
          CollectionDescription<BookingConfiguration> description) {
    super(BookingConfiguration.class, sessionFactory);
    this.criteriaBuilderFactory = criteriaBuilderFactory;
    this.itemQuery = itemQuery;
    this.instruments = instruments;
    this.permissionGate = permissionGate;
    collectionQuery =
        new CollectionQueryExecutor<>(
            BookingConfiguration.class, description, "bookingConfiguration");
  }

  @Override
  public ResourcePage<BookingConfiguration> getResources(
      ResourceRequest request, RelationshipReadAccess targetAccess) {
    return getCalendarResources(request, targetAccess, null);
  }

  @Override
  public ResourcePage<BookingConfiguration> getCalendarResources(
      ResourceRequest request,
      RelationshipReadAccess targetAccess,
      com.researchspace.dao.query.RsqlCollectionQuery.Predicate restriction) {
    // Fetch actors only after the ordered page has been selected.
    return collectionQuery.pageWithBatchedFetch(
        criteriaBuilderFactory,
        getSession(),
        request,
        restriction,
        targetAccess,
        List.of("createdBy", "updatedBy"));
  }

  @Override
  public long countResources(ResourceRequest request, RelationshipReadAccess targetAccess) {
    return collectionQuery.count(criteriaBuilderFactory, getSession(), request, null, targetAccess);
  }

  @Override
  public List<BookingConfiguration> getResources(
      ResourceRequest request, int limit, RelationshipReadAccess targetAccess) {
    return collectionQuery.listById(
        criteriaBuilderFactory, getSession(), request, limit, targetAccess);
  }

  @Override
  public Optional<BookingConfiguration> findByTarget(BookableTargetReference target) {
    return byTarget(target, false, false);
  }

  @Override
  public Optional<BookingConfiguration> lockActiveByTarget(BookableTargetReference target) {
    permissionGate.lockShared();
    lockCurrentTarget(target);
    return byTarget(target, true, true).map(this::refreshSchedulingState);
  }

  @Override
  public Optional<BookingConfiguration> lockByTarget(BookableTargetReference target) {
    permissionGate.lockShared();
    lockTarget(target);
    return byTarget(target, false, true);
  }

  private Optional<BookingConfiguration> byTarget(
      BookableTargetReference target, boolean activeOnly, boolean lock) {
    String hql = "from BookingConfiguration where target.type = :type and target.id = :id";
    if (activeOnly) {
      hql += " and state = :state";
    }
    if (activeOnly && lock) {
      getSession()
          .createQuery(
              "select c.id from BookingConfiguration c where c.target.type = :type and c.target.id"
                  + " = :id",
              Long.class)
          .setParameter("type", target.type())
          .setParameter("id", target.id())
          .uniqueResultOptional()
          .ifPresent(
              id -> getSession().evict(getSession().getReference(BookingConfiguration.class, id)));
    }
    var query =
        getSession()
            .createQuery(hql, BookingConfiguration.class)
            .setParameter("type", target.type())
            .setParameter("id", target.id());
    if (activeOnly) {
      query.setParameter("state", BookingConfigurationState.ACTIVE);
    }
    if (lock) {
      query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
    }
    return query.uniqueResultOptional();
  }

  @Override
  public Optional<BookingConfiguration> lockById(Long id) {
    permissionGate.lockShared();
    targetById(id).ifPresent(this::lockTarget);
    return getSession()
        .createQuery("from BookingConfiguration where id = :id", BookingConfiguration.class)
        .setParameter("id", id)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .uniqueResultOptional();
  }

  @Override
  public Optional<BookingConfiguration> lockActiveById(Long id) {
    permissionGate.lockShared();
    targetById(id).ifPresent(this::lockCurrentTarget);
    getSession().evict(getSession().getReference(BookingConfiguration.class, id));
    return getSession()
        .createQuery(
            "from BookingConfiguration where id = :id and state = :state",
            BookingConfiguration.class)
        .setParameter("id", id)
        .setParameter("state", BookingConfigurationState.ACTIVE)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .uniqueResultOptional()
        .map(this::refreshSchedulingState);
  }

  private Optional<BookableTargetReference> targetById(Long id) {
    return getSession()
        .createQuery(
            "select c.target from BookingConfiguration c where c.id = :id",
            BookableTargetReference.class)
        .setParameter("id", id)
        .uniqueResultOptional();
  }

  private void lockTarget(BookableTargetReference target) {
    if (target != null && target.type() == BookableTargetType.INSTRUMENT) {
      instruments.lockById(target.id());
    }
  }

  /** Locks the target and rejects permission facts changed after this transaction's snapshot. */
  private void lockCurrentTarget(BookableTargetReference target) {
    lockTarget(target);
    if (target == null || target.type() != BookableTargetType.INSTRUMENT) {
      return;
    }
    // With snapshot isolation disabled, locking reads can return rows changed after the
    // transaction's snapshot, so compare snapshot and current permission columns explicitly.
    String permissionColumns =
        "select owner_id, sharingMode, acl, deleted from InstrumentEntity where id = :id";
    List<?> snapshot =
        getSession()
            .createNativeQuery(permissionColumns, Object[].class)
            .setParameter("id", target.id())
            .getResultList();
    List<?> current =
        getSession()
            .createNativeQuery(permissionColumns + " for update", Object[].class)
            .setParameter("id", target.id())
            .getResultList();
    if (!Arrays.deepEquals(snapshot.toArray(), current.toArray())) {
      throw new StaleObjectStateException("InstrumentEntity", target.id());
    }
  }

  private BookingConfiguration refreshSchedulingState(BookingConfiguration configuration) {
    Long id = configuration.getId();
    getSession().evict(configuration);
    return getSession()
        .createQuery("from BookingConfiguration c where c.id = :id", BookingConfiguration.class)
        .setParameter("id", id)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .getSingleResult();
  }

  @Override
  public List<BookingConfiguration> lockResources(
      ResourceRequest request, int limit, RelationshipReadAccess relationshipAccess) {
    permissionGate.lockShared();
    List<BookingConfiguration> selected =
        collectionQuery.listById(
            criteriaBuilderFactory, getSession(), request, limit, relationshipAccess);
    List<Long> targetIds =
        selected.stream()
            .map(BookingConfiguration::getTarget)
            .filter(java.util.Objects::nonNull)
            .filter(target -> target.type() == BookableTargetType.INSTRUMENT)
            .map(BookableTargetReference::id)
            .distinct()
            .sorted()
            .toList();
    targetIds.forEach(
        id ->
            getSession()
                .createNativeQuery(
                    "select id from InstrumentEntity where id = :id for update", Long.class)
                .setParameter("id", id)
                .getResultList());
    if (!targetIds.isEmpty()) {
      List<Long> ownerIds =
          getSession()
              .createNativeQuery(
                  "select distinct owner_id from InstrumentEntity where id in (:ids) order by"
                      + " owner_id",
                  Long.class)
              .setParameterList("ids", targetIds)
              .getResultList();
      if (!ownerIds.isEmpty())
        getSession()
            .createNativeQuery(
                "select id from User where id in (:ids) order by id for update", Long.class)
            .setParameterList("ids", ownerIds)
            .getResultList();
    }
    // Selection is an unlocked snapshot. Evict it before the locking entity read so a
    // concurrent PATCH is not compared with a stale managed version.
    List<Long> selectedIds = selected.stream().map(BookingConfiguration::getId).toList();
    selected.forEach(getSession()::evict);
    List<BookingConfiguration> locked =
        selectedIds.stream().sorted().map(this::lockById).flatMap(Optional::stream).toList();
    if (locked.isEmpty()) return locked;
    var ids =
        new com.researchspace.model.collection.FilterExpression.Comparison(
            "id",
            com.researchspace.model.collection.Operator.IN,
            new java.util.ArrayList<Object>(
                locked.stream().map(BookingConfiguration::getId).toList()),
            false);
    com.researchspace.model.collection.FilterExpression filter =
        request.filter() == null
            ? ids
            : new com.researchspace.model.collection.FilterExpression.And(
                List.of(request.filter(), ids));
    var currentRequest =
        new ResourceRequest(
            filter,
            request.serverConstraint(),
            request.sort(),
            request.page(),
            request.fieldSelections(),
            request.includes(),
            request.runtime());
    return collectionQuery.listByIdForUpdate(
        criteriaBuilderFactory, getSession(), currentRequest, limit, relationshipAccess);
  }

  @Override
  public BookingConfiguration saveAndFlush(BookingConfiguration configuration) {
    BookingConfiguration saved = save(configuration);
    getSession().flush();
    return saved;
  }

  @Override
  public Set<Long> findBookableInstrumentIds(User caller, Set<String> readableRoleKeys) {
    if (readableRoleKeys.isEmpty()) return Set.of();
    var query =
        criteriaBuilderFactory
            .create(getSession(), Long.class)
            .from(BookingConfiguration.class, "bookingConfiguration")
            .select("bookingConfiguration.target.id")
            .distinct()
            .where("bookingConfiguration.state")
            .eq(BookingConfigurationState.ACTIVE)
            .where("bookingConfiguration.enabled")
            .eq(true);
    var restriction = itemQuery.restriction(caller, false, false, "bookingConfiguration.target");
    if (restriction != null) {
      restriction.applyTo(query);
    }
    return Set.copyOf(query.getResultList());
  }
}
