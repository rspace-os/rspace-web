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
import com.researchspace.model.collection.CollectionDescription.Operator;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.RelationshipReadAccess;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.resourceaccess.ResourceAccess;
import com.researchspace.model.resourceaccess.ResourceAudience;
import com.researchspace.search.customfield.RuntimeFieldTextSearch;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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
    try {
      return collectionQuery.page(
          criteriaBuilderFactory, getSession(), narrowed(request), null, targetAccess);
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
    return getSession()
        .createQuery(
            "from BookingConfiguration where target.type = :type and target.id = :id"
                + " and state = :state",
            BookingConfiguration.class)
        .setParameter("type", target.type())
        .setParameter("id", target.id())
        .setParameter("state", BookingConfigurationState.ACTIVE)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .uniqueResultOptional();
  }

  @Override
  public Optional<BookingConfiguration> lockByTarget(BookableTargetReference target) {
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
    return getSession()
        .createQuery("from BookingConfiguration where id = :id", BookingConfiguration.class)
        .setParameter("id", id)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .uniqueResultOptional();
  }

  @Override
  public Optional<BookingConfiguration> lockActiveById(Long id) {
    return getSession()
        .createQuery(
            "from BookingConfiguration where id = :id and state = :state",
            BookingConfiguration.class)
        .setParameter("id", id)
        .setParameter("state", BookingConfigurationState.ACTIVE)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .uniqueResultOptional();
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
    getSession().remove(access);
    getSession().flush();
  }

  @Override
  public Set<Long> findBookableInstrumentIds(User caller, Set<String> readableRoleKeys) {
    if (readableRoleKeys.isEmpty()) {
      return Set.of();
    }
    Set<Long> groupIds =
        caller.getGroups().stream().map(group -> group.getId()).collect(Collectors.toSet());
    if (groupIds.isEmpty()) {
      groupIds = Set.of(-1L);
    }
    return Set.copyOf(
        getSession()
            .createQuery(
                "select distinct configuration.target.id from BookingConfiguration configuration"
                    + " join configuration.resourceAccess.assignments assignment"
                    + " where configuration.state = :state and configuration.enabled = true"
                    + " and configuration.target.type = :targetType"
                    + " and assignment.roleKey in :readableRoleKeys"
                    + " and (assignment.user.id = :userId or assignment.group.id in :groupIds"
                    + " or assignment.audienceKey = :audience)",
                Long.class)
            .setParameter("targetType", BookableTargetType.INSTRUMENT)
            .setParameter("state", BookingConfigurationState.ACTIVE)
            .setParameterList("readableRoleKeys", readableRoleKeys)
            .setParameter("userId", caller.getId())
            .setParameterList("groupIds", groupIds)
            .setParameter("audience", ResourceAudience.ALL_USERS)
            .getResultList());
  }
}
