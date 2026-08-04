package com.researchspace.booking.dao;

import com.blazebit.persistence.CriteriaBuilderFactory;
import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.query.CollectionQueryExecutor;
import com.researchspace.model.booking.ApiV2BookingConfigurationResource;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import java.util.List;
import java.util.Optional;
import org.hibernate.SessionFactory;
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

  private final CriteriaBuilderFactory criteriaBuilderFactory;

  public BookingConfigurationDaoHibernate(
      SessionFactory sessionFactory, CriteriaBuilderFactory criteriaBuilderFactory) {
    super(BookingConfiguration.class, sessionFactory);
    this.criteriaBuilderFactory = criteriaBuilderFactory;
  }

  @Override
  public ResourcePage<BookingConfiguration> getResources(ResourceRequest request) {
    return COLLECTION_QUERY.page(criteriaBuilderFactory, getSession(), request);
  }

  @Override
  public long countResources(ResourceRequest request) {
    return COLLECTION_QUERY.count(criteriaBuilderFactory, getSession(), request);
  }

  @Override
  public List<BookingConfiguration> getResources(ResourceRequest request, int limit) {
    return COLLECTION_QUERY.listById(criteriaBuilderFactory, getSession(), request, limit);
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
  public BookingConfiguration saveAndFlush(BookingConfiguration configuration) {
    BookingConfiguration saved = save(configuration);
    getSession().flush();
    return saved;
  }
}
