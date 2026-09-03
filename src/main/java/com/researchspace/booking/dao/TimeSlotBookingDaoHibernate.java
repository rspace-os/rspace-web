package com.researchspace.booking.dao;

import com.blazebit.persistence.CriteriaBuilderFactory;
import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.query.CollectionQueryExecutor;
import com.researchspace.model.booking.BookingConfigurationState;
import com.researchspace.model.booking.BookingEventKind;
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
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

/** Hibernate persistence for one-off time-slot bookings. */
@Repository("timeSlotBookingDao")
public class TimeSlotBookingDaoHibernate extends GenericDaoHibernate<TimeSlotBooking, Long>
    implements TimeSlotBookingDao {

  private static final FilterExpression ACTIVE =
      new FilterExpression.Comparison("deleted", Operator.EQUAL, List.of(false), false);

  private final CriteriaBuilderFactory criteriaBuilderFactory;
  private final CollectionQueryExecutor<TimeSlotBooking> collectionQuery;

  public TimeSlotBookingDaoHibernate(
      SessionFactory sessionFactory,
      CriteriaBuilderFactory criteriaBuilderFactory,
      @Qualifier(
              com.researchspace.booking.config.BookingResourceAccessConfiguration
                  .TIME_SLOT_BOOKING_DESCRIPTION)
          com.researchspace.model.collection.CollectionDescription<TimeSlotBooking> description) {
    super(TimeSlotBooking.class, sessionFactory);
    this.criteriaBuilderFactory = criteriaBuilderFactory;
    collectionQuery = new CollectionQueryExecutor<>(TimeSlotBooking.class, description, "booking");
  }

  @Override
  public ResourcePage<TimeSlotBooking> getReadableResources(
      ResourceRequest request, RelationshipReadAccess targetAccess) {
    return collectionQuery.page(
        criteriaBuilderFactory,
        getSession(),
        request.restrict(ACTIVE),
        null,
        targetAccess,
        List.of("bookingConfiguration"));
  }

  @Override
  public long countReadableResources(ResourceRequest request, RelationshipReadAccess targetAccess) {
    return collectionQuery.count(
        criteriaBuilderFactory, getSession(), request.restrict(ACTIVE), null, targetAccess);
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
  public Optional<TimeSlotBooking> findReadableForAuditById(
      ResourceRequest authorizedRequest, RelationshipReadAccess targetAccess) {
    return collectionQuery
        .page(
            criteriaBuilderFactory,
            getSession(),
            authorizedRequest,
            null,
            targetAccess,
            List.of("bookingConfiguration", "requester"))
        .resources()
        .stream()
        .findFirst();
  }

  @Override
  public boolean overlaps(
      Long configurationId,
      Date start,
      Date end,
      Long excludedBookingId,
      Set<BookingEventKind> includedKinds) {
    String exclusion = excludedBookingId == null ? "" : " and id <> :excludedId";
    var query =
        getSession()
            .createQuery(
                "select count(id) from TimeSlotBooking where bookingConfiguration.id = :configId"
                    + " and deleted = false and state = :state and startTime < :end"
                    + " and endTime > :start"
                    + " and kind in :kinds"
                    + exclusion,
                Long.class)
            .setParameter("configId", configurationId)
            .setParameter("state", BookingState.CONFIRMED)
            .setParameter("kinds", includedKinds)
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
  public List<TimeSlotBooking> findCalendarBookings(
      Long configurationId, Date cutoff, int maximumRows) {
    return getSession()
        .createQuery(
            "select booking from TimeSlotBooking booking"
                + " join fetch booking.bookingConfiguration"
                + " join fetch booking.requester"
                + " left join fetch booking.createdBy"
                + " where booking.bookingConfiguration.id = :configurationId"
                + " and booking.deleted = false and booking.state = :state"
                + " and booking.endTime > :cutoff"
                + " order by booking.startTime, booking.id",
            TimeSlotBooking.class)
        .setParameter("configurationId", configurationId)
        .setParameter("state", BookingState.CONFIRMED)
        .setParameter("cutoff", cutoff)
        .setMaxResults(maximumRows)
        .getResultList();
  }

  @Override
  public List<TimeSlotBooking> findUserCalendarBookings(Long userId, Date cutoff, int maximumRows) {
    return getSession()
        .createQuery(
            "select booking from TimeSlotBooking booking"
                + " join fetch booking.bookingConfiguration"
                + " join fetch booking.requester"
                + " left join fetch booking.createdBy"
                + " where booking.requester.id = :userId"
                + " and booking.bookingConfiguration.state = :configurationState"
                + " and booking.deleted = false and booking.state = :state"
                + " and booking.endTime > :cutoff"
                + " order by booking.startTime, booking.id",
            TimeSlotBooking.class)
        .setParameter("userId", userId)
        .setParameter("configurationState", BookingConfigurationState.ACTIVE)
        .setParameter("state", BookingState.CONFIRMED)
        .setParameter("cutoff", cutoff)
        .setMaxResults(maximumRows)
        .getResultList();
  }

  @Override
  public List<TimeSlotBooking> findFutureConfirmedByConfiguration(Long configurationId, Date now) {
    return getSession()
        .createQuery(
            "select booking from TimeSlotBooking booking"
                + " join fetch booking.requester"
                + " where booking.bookingConfiguration.id = :configurationId"
                + " and booking.deleted = false and booking.state = :state"
                + " and booking.startTime > :now"
                + " order by booking.startTime, booking.id",
            TimeSlotBooking.class)
        .setParameter("configurationId", configurationId)
        .setParameter("state", BookingState.CONFIRMED)
        .setParameter("now", now)
        .getResultList();
  }

  @Override
  public TimeSlotBooking saveAndFlush(TimeSlotBooking booking) {
    TimeSlotBooking saved = save(booking);
    getSession().flush();
    return saved;
  }

  @Override
  public int removeAllByConfigurationId(Long configurationId) {
    Session session = getSession();
    int removed = 0;
    while (true) {
      List<TimeSlotBooking> batch =
          session
              .createQuery(
                  "from TimeSlotBooking where bookingConfiguration.id = :configurationId"
                      + " order by id",
                  TimeSlotBooking.class)
              .setParameter("configurationId", configurationId)
              .setMaxResults(100)
              .getResultList();
      if (batch.isEmpty()) {
        return removed;
      }
      batch.forEach(session::remove);
      removed += batch.size();
      session.flush();
    }
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
