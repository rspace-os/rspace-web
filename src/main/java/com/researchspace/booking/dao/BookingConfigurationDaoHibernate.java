package com.researchspace.booking.dao;

import com.blazebit.persistence.CriteriaBuilderFactory;
import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.query.CollectionQueryExecutor;
import com.researchspace.dao.query.IndexedTextNarrowing;
import com.researchspace.model.booking.ApiV2BookingConfigurationResource;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.collection.CollectionDescription.Operator;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.RelationshipReadAccess;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.search.customfield.RuntimeFieldTextSearch;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/** Hibernate persistence for booking configurations. */
@Repository("bookingConfigurationDao")
public class BookingConfigurationDaoHibernate
    extends GenericDaoHibernate<BookingConfiguration, Long> implements BookingConfigurationDao {

  private static final CollectionQueryExecutor<BookingConfiguration> COLLECTION_QUERY =
      new CollectionQueryExecutor<>(
          BookingConfiguration.class,
          ApiV2BookingConfigurationResource.DESCRIPTION,
          "bookingConfiguration");

  private static final FilterExpression ACTIVE =
      new FilterExpression.Comparison("deleted", Operator.EQUAL, List.of(false), false);

  private final CriteriaBuilderFactory criteriaBuilderFactory;

  @Autowired(required = false)
  private RuntimeFieldTextSearch textSearch;

  public BookingConfigurationDaoHibernate(
      SessionFactory sessionFactory, CriteriaBuilderFactory criteriaBuilderFactory) {
    super(BookingConfiguration.class, sessionFactory);
    this.criteriaBuilderFactory = criteriaBuilderFactory;
  }

  @Override
  public ResourcePage<BookingConfiguration> getResources(
      ResourceRequest request, RelationshipReadAccess targetAccess) {
    try {
      return COLLECTION_QUERY.page(
          criteriaBuilderFactory,
          getSession(),
          narrowed(request.restrict(ACTIVE)),
          null,
          targetAccess);
    } catch (IndexedTextNarrowing.NoMatch noMatch) {
      return new ResourcePage<>(List.of(), 0);
    }
  }

  @Override
  public long countResources(ResourceRequest request, RelationshipReadAccess targetAccess) {
    try {
      return COLLECTION_QUERY.count(
          criteriaBuilderFactory,
          getSession(),
          narrowed(request.restrict(ACTIVE)),
          null,
          targetAccess);
    } catch (IndexedTextNarrowing.NoMatch noMatch) {
      return 0;
    }
  }

  @Override
  public List<BookingConfiguration> getResources(
      ResourceRequest request, int limit, RelationshipReadAccess targetAccess) {
    try {
      return COLLECTION_QUERY.listById(
          criteriaBuilderFactory,
          getSession(),
          narrowed(request.restrict(ACTIVE)),
          limit,
          targetAccess);
    } catch (IndexedTextNarrowing.NoMatch noMatch) {
      return List.of();
    }
  }

  private ResourceRequest narrowed(ResourceRequest request) {
    return IndexedTextNarrowing.apply(
        request, ApiV2BookingConfigurationResource.DESCRIPTION, textSearch);
  }

  @Override
  public Optional<BookingConfiguration> findByTarget(BookableTargetReference target) {
    return getSession()
        .createQuery(
            "from BookingConfiguration where target.type = :type and target.id = :id"
                + " and deleted = false",
            BookingConfiguration.class)
        .setParameter("type", target.type())
        .setParameter("id", target.id())
        .uniqueResultOptional();
  }

  @Override
  public Optional<BookingConfiguration> lockByTarget(BookableTargetReference target) {
    return getSession()
        .createQuery(
            "from BookingConfiguration where target.type = :type and target.id = :id"
                + " and deleted = false",
            BookingConfiguration.class)
        .setParameter("type", target.type())
        .setParameter("id", target.id())
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .uniqueResultOptional();
  }

  @Override
  public Optional<BookingConfiguration> lockById(Long id) {
    return getSession()
        .createQuery(
            "from BookingConfiguration where id = :id and deleted = false",
            BookingConfiguration.class)
        .setParameter("id", id)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .uniqueResultOptional();
  }

  @Override
  public BookingConfiguration saveAndFlush(BookingConfiguration configuration) {
    BookingConfiguration saved = save(configuration);
    getSession().flush();
    return saved;
  }
}
