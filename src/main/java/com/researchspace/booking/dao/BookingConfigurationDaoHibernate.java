package com.researchspace.booking.dao;

import com.blazebit.persistence.CriteriaBuilderFactory;
import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.query.CollectionQueryExecutor;
import com.researchspace.dao.query.IndexedTextNarrowing;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingConfigurationState;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.RelationshipReadAccess;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.resourceaccess.ResourceAccess;
import com.researchspace.search.customfield.RuntimeFieldTextSearch;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

/** Hibernate persistence for booking configurations. */
@Repository("bookingConfigurationDao")
public class BookingConfigurationDaoHibernate
    extends GenericDaoHibernate<BookingConfiguration, Long> implements BookingConfigurationDao {

  private static final FilterExpression ACTIVE =
      new FilterExpression.Comparison(
          "state", Operator.EQUAL, List.of(BookingConfigurationState.ACTIVE), false);
  private final CriteriaBuilderFactory criteriaBuilderFactory;
  private final CollectionDescription<BookingConfiguration> description;
  private final CollectionQueryExecutor<BookingConfiguration> collectionQuery;

  @Autowired(required = false)
  private RuntimeFieldTextSearch textSearch;

  @Autowired private BookingItemQuery itemQuery;
  @Autowired private com.researchspace.dao.InstrumentDao instruments;

  public BookingConfigurationDaoHibernate(
      SessionFactory sessionFactory,
      CriteriaBuilderFactory criteriaBuilderFactory,
      @Qualifier(
              com.researchspace.booking.config.BookingResourceAccessConfiguration
                  .BOOKING_CONFIGURATION_DESCRIPTION)
          CollectionDescription<BookingConfiguration> description) {
    super(BookingConfiguration.class, sessionFactory);
    this.criteriaBuilderFactory = criteriaBuilderFactory;
    this.description = description;
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
    try {
      return collectionQuery.page(
          criteriaBuilderFactory, getSession(), narrowed(request), restriction, targetAccess);
    } catch (IndexedTextNarrowing.NoMatch noMatch) {
      return new ResourcePage<>(List.of(), 0);
    }
  }

  @Override
  public long countResources(ResourceRequest request, RelationshipReadAccess targetAccess) {
    try {
      return collectionQuery.count(
          criteriaBuilderFactory, getSession(), narrowed(request), null, targetAccess);
    } catch (IndexedTextNarrowing.NoMatch noMatch) {
      return 0;
    }
  }

  @Override
  public List<BookingConfiguration> getResources(
      ResourceRequest request, int limit, RelationshipReadAccess targetAccess) {
    try {
      return collectionQuery.listById(
          criteriaBuilderFactory, getSession(), narrowed(request), limit, targetAccess);
    } catch (IndexedTextNarrowing.NoMatch noMatch) {
      return List.of();
    }
  }

  private ResourceRequest narrowed(ResourceRequest request) {
    return IndexedTextNarrowing.apply(request, description, textSearch);
  }

  @Override
  public Optional<BookingConfiguration> findByTarget(BookableTargetReference target) {
    return getSession()
        .createQuery(
            "from BookingConfiguration where target.type = :type and target.id = :id",
            BookingConfiguration.class)
        .setParameter("type", target.type())
        .setParameter("id", target.id())
        .uniqueResultOptional();
  }

  @Override
  public Optional<BookingConfiguration> lockActiveByTarget(BookableTargetReference target) {
    lockTarget(target);
    return getSession()
        .createQuery(
            "from BookingConfiguration where target.type = :type and target.id = :id"
                + " and state = :state",
            BookingConfiguration.class)
        .setParameter("type", target.type())
        .setParameter("id", target.id())
        .setParameter("state", BookingConfigurationState.ACTIVE)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .uniqueResultOptional()
        .map(this::refreshSchedulingState);
  }

  @Override
  public Optional<BookingConfiguration> lockByTarget(BookableTargetReference target) {
    lockTarget(target);
    return getSession()
        .createQuery(
            "from BookingConfiguration where target.type = :type and target.id = :id",
            BookingConfiguration.class)
        .setParameter("type", target.type())
        .setParameter("id", target.id())
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .uniqueResultOptional();
  }

  @Override
  public Optional<BookingConfiguration> lockById(Long id) {
    getSafeNull(id).ifPresent(configuration -> lockTarget(configuration.getTarget()));
    return getSession()
        .createQuery("from BookingConfiguration where id = :id", BookingConfiguration.class)
        .setParameter("id", id)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .uniqueResultOptional();
  }

  @Override
  public Optional<BookingConfiguration> lockActiveById(Long id) {
    getSafeNull(id).ifPresent(configuration -> lockTarget(configuration.getTarget()));
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

  private void lockTarget(BookableTargetReference target) {
    if (target != null && target.type() == BookableTargetType.INSTRUMENT) {
      instruments.lockById(target.id());
    }
  }

  private BookingConfiguration refreshSchedulingState(BookingConfiguration configuration) {
    // A locking query can return entities already cached by the edit's initial readable lookup.
    getSession().refresh(configuration, LockModeType.PESSIMISTIC_WRITE);
    return configuration;
  }

  @Override
  public List<BookingConfiguration> lockResources(
      ResourceRequest request, int limit, RelationshipReadAccess relationshipAccess) {
    try {
      return collectionQuery.listByIdForUpdate(
          criteriaBuilderFactory, getSession(), narrowed(request), limit, relationshipAccess);
    } catch (IndexedTextNarrowing.NoMatch noMatch) {
      return List.of();
    }
  }

  @Override
  public BookingConfiguration saveAndFlush(BookingConfiguration configuration) {
    BookingConfiguration saved = save(configuration);
    getSession().flush();
    return saved;
  }

  @Override
  public void removeConfigurationAndAccess(BookingConfiguration configuration) {
    ResourceAccess access = configuration.getResourceAccess();
    getSession().remove(configuration);
    getSession().flush();
    if (access != null) {
      getSession().remove(access);
      getSession().flush();
    }
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
    itemQuery.restriction(caller, false, false, "bookingConfiguration.target").apply(query);
    return Set.copyOf(query.getResultList());
  }
}
