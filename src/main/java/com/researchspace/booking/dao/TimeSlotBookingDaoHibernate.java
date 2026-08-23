package com.researchspace.booking.dao;

import com.blazebit.persistence.CriteriaBuilderFactory;
import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.query.CollectionQueryExecutor;
import com.researchspace.model.booking.ApiV2TimeSlotBookingResource;
import com.researchspace.model.booking.BookingState;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.collection.CollectionDescription.Operator;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.RelationshipReadAccess;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.hibernate.SessionFactory;
import org.springframework.stereotype.Repository;

/** Hibernate persistence for one-off time-slot bookings. */
@Repository("timeSlotBookingDao")
public class TimeSlotBookingDaoHibernate extends GenericDaoHibernate<TimeSlotBooking, Long>
    implements TimeSlotBookingDao {

  private static final CollectionQueryExecutor<TimeSlotBooking> COLLECTION_QUERY =
      new CollectionQueryExecutor<>(
          TimeSlotBooking.class, ApiV2TimeSlotBookingResource.DESCRIPTION, "booking");

  private static final FilterExpression ACTIVE =
      new FilterExpression.Comparison("deleted", Operator.EQUAL, List.of(false), false);

  private final CriteriaBuilderFactory criteriaBuilderFactory;

  public TimeSlotBookingDaoHibernate(
      SessionFactory sessionFactory, CriteriaBuilderFactory criteriaBuilderFactory) {
    super(TimeSlotBooking.class, sessionFactory);
    this.criteriaBuilderFactory = criteriaBuilderFactory;
  }

  @Override
  public ResourcePage<TimeSlotBooking> getReadableResources(
      ResourceRequest request, RelationshipReadAccess targetAccess) {
    return COLLECTION_QUERY.page(
        criteriaBuilderFactory,
        getSession(),
        request.restrict(ACTIVE),
        COLLECTION_QUERY.compileReadableRelationship("target", targetAccess),
        targetAccess);
  }

  @Override
  public long countReadableResources(ResourceRequest request, RelationshipReadAccess targetAccess) {
    return COLLECTION_QUERY.count(
        criteriaBuilderFactory,
        getSession(),
        request.restrict(ACTIVE),
        COLLECTION_QUERY.compileReadableRelationship("target", targetAccess),
        targetAccess);
  }

  @Override
  public Optional<TimeSlotBooking> findReadableById(Long id, RelationshipReadAccess targetAccess) {
    ResourceRequest request =
        new ResourceRequest(
            new FilterExpression.Comparison("id", Operator.EQUAL, List.of(id), false),
            List.of(),
            new ResourceRequest.Page(1, 1),
            FieldSelection.all(),
            IncludeTree.empty());
    return getReadableResources(request, targetAccess).resources().stream().findFirst();
  }

  @Override
  public boolean overlaps(Long configurationId, Date start, Date end, Long excludedBookingId) {
    String exclusion = excludedBookingId == null ? "" : " and id <> :excludedId";
    var query =
        getSession()
            .createQuery(
                "select count(id) from TimeSlotBooking where bookingConfiguration.id = :configId"
                    + " and deleted = false and state = :state and startTime < :end"
                    + " and endTime > :start"
                    + exclusion,
                Long.class)
            .setParameter("configId", configurationId)
            .setParameter("state", BookingState.CONFIRMED)
            .setParameter("start", start)
            .setParameter("end", end);
    if (excludedBookingId != null) {
      query.setParameter("excludedId", excludedBookingId);
    }
    return query.getSingleResult() > 0;
  }

  @Override
  public Set<Long> findOwnedInstrumentIds(Collection<Long> targetIds, Long actorId) {
    if (targetIds.isEmpty()) {
      return Set.of();
    }
    return new HashSet<>(
        getSession()
            .createQuery(
                "select instrument.id from Instrument instrument where instrument.id in :ids"
                    + " and instrument.owner.id = :actorId",
                Long.class)
            .setParameter("ids", targetIds)
            .setParameter("actorId", actorId)
            .getResultList());
  }

  @Override
  public TimeSlotBooking saveAndFlush(TimeSlotBooking booking) {
    TimeSlotBooking saved = save(booking);
    getSession().flush();
    return saved;
  }

  @Override
  public ResourcePage<TimeSlotBooking> getResources(
      ResourceRequest request, RelationshipReadAccess relationshipAccess) {
    return getReadableResources(request, relationshipAccess);
  }

  @Override
  public long countResources(ResourceRequest request, RelationshipReadAccess relationshipAccess) {
    return countReadableResources(request, relationshipAccess);
  }

  @Override
  public List<TimeSlotBooking> getResources(
      ResourceRequest request, int limit, RelationshipReadAccess relationshipAccess) {
    ResourcePage<TimeSlotBooking> page = getReadableResources(request, relationshipAccess);
    return page.resources().stream().limit(limit).toList();
  }
}
