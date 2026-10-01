package com.researchspace.dao.hibernate;

import com.researchspace.booking.service.BookingCatalogueLocation;
import com.researchspace.dao.BookingInstrumentQueryDao;
import com.researchspace.dao.hibernate.InstrumentDaoHibernateImpl.InstrumentScalarRow;
import com.researchspace.dao.query.LikeEscaper;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfigurationState;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.inventory.Container.ContainerType;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InstrumentReadSummary;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.hibernate.query.Query;
import org.springframework.stereotype.Repository;

/** Booking queries run in the calling manager's active transaction. */
@Repository("bookingInstrumentQueryDao")
public class BookingInstrumentQueryDaoHibernate extends InventoryDaoHibernate<Instrument, Long>
    implements BookingInstrumentQueryDao {

  public BookingInstrumentQueryDaoHibernate() {
    super(Instrument.class);
  }

  @Override
  public ResourcePage<BookingCatalogueLocation> getBookingCatalogueLocations(
      String query, int page, int limit, User caller) {
    return getBookingCatalogueLocations(query, null, page, limit, caller);
  }

  @Override
  public ResourcePage<BookingCatalogueLocation> getBookingCatalogueLocations(
      String query, Set<Long> containerIds, int page, int limit, User caller) {
    if (caller == null
        || !caller.isEnabled()
        || caller.isAccountLocked()
        || (containerIds != null && containerIds.isEmpty())) {
      return new ResourcePage<>(List.of(), 0);
    }
    List<String> groupMembers =
        invPermissionUtils.getUsernameOfUserAndAllMembersOfTheirGroups(caller);
    List<String> groupNames = caller.getGroups().stream().map(Group::getUniqueName).toList();
    List<String> visibleOwners = invPermissionUtils.getOwnersVisibleWithUserRole(caller);
    String bookingAccess =
        getInventoryReadPermissionSqlPredicate(
            caller, groupMembers, groupNames, visibleOwners, "instrument.");
    String containerAccess =
        readableContainerPredicate(caller, groupMembers, groupNames, visibleOwners, "parent");
    String nameFilter =
        query == null || query.isBlank()
            ? ""
            : " lower(parent.editInfo.name) like :locationQuery escape '!'";
    String whereClause =
        new StringBuilder("type(instrument)=Instrument and instrument.deleted=false ")
            .append("and configuration.state=:configurationState and configuration.enabled=true ")
            .append(
                "and configuration.target.type=:targetType and"
                    + " configuration.target.id=instrument.id ")
            .append("and location.storedInstrument.id=instrument.id and parent.deleted=false and ")
            .append(bookingAccess)
            .append(" and ")
            .append(containerAccess)
            .append(query == null || query.isBlank() ? "" : " and " + nameFilter)
            .append(containerIds == null ? "" : " and parent.id in (:locationIds)")
            .toString();
    String fromAndWhere =
        new StringBuilder(" from BookingConfiguration configuration, Instrument instrument ")
            .append("join instrument.parentLocation location join location.container parent ")
            .append("where ")
            .append(whereClause)
            .toString();

    Query<Long> countQuery =
        getSession().createQuery("select count(distinct parent.id)" + fromAndWhere, Long.class);
    Query<Object[]> pageQuery =
        getSession()
            .createQuery(
                "select parent.id, parent.editInfo.name, parent.containerType"
                    + fromAndWhere
                    + " group by parent.id, parent.editInfo.name, parent.containerType"
                    + " order by lower(parent.editInfo.name), parent.id",
                Object[].class)
            .setFirstResult((page - 1) * limit)
            .setMaxResults(limit);
    setBookingCatalogueLocationParameters(
        countQuery, caller, groupMembers, groupNames, visibleOwners, query);
    setBookingCatalogueLocationParameters(
        pageQuery, caller, groupMembers, groupNames, visibleOwners, query);
    if (containerIds != null) {
      countQuery.setParameterList("locationIds", containerIds);
      pageQuery.setParameterList("locationIds", containerIds);
    }
    long total = countQuery.getSingleResult();
    List<BookingCatalogueLocation> locations =
        pageQuery
            .getResultStream()
            .map(
                row ->
                    new BookingCatalogueLocation(
                        (Long) row[0], (String) row[1], (ContainerType) row[2]))
            .toList();
    return new ResourcePage<>(locations, total);
  }

  @Override
  public Map<Long, InstrumentReadSummary> getBookingSummaries(Set<Long> instrumentIds) {
    if (instrumentIds.isEmpty()) {
      return Map.of();
    }
    return criteriaBuilderFactory()
        .create(getSession(), InstrumentScalarRow.class)
        .from(Instrument.class, "instrument")
        .selectNew(InstrumentScalarRow.class)
        .with("instrument.id")
        .with("instrument.editInfo.name")
        .with("instrument.deleted")
        .with("instrument.parentLocation.container.id")
        .end()
        .whereExpression("type(instrument) = Instrument")
        .whereExpression("instrument.id in :instrumentIds")
        .setParameter("instrumentIds", instrumentIds)
        .getResultStream()
        .map(
            row ->
                new InstrumentReadSummary(
                    row.instrumentId(), row.name(), row.deleted(), row.parentContainerId()))
        .collect(Collectors.toMap(InstrumentReadSummary::id, summary -> summary));
  }

  @Override
  public Map<Long, BookingItemAccess> getBookingItemAccess(Set<Long> instrumentIds, User user) {
    if (instrumentIds.isEmpty() || user == null || !user.isEnabled() || user.isAccountLocked()) {
      return Map.of();
    }
    InventoryReadQueryContext context = readQueryContext(user);
    String readablePermission = context.permissionPredicate(this, "instrument.");
    String directEditPermission =
        getInventoryReadPermissionSqlPredicate(
            user, context.groupMembers(), context.groupNames(), List.of(), "instrument.");
    String hql =
        "select instrument.id, instrument.owner.username, case when "
            + directEditPermission
            + " then true else false end from Instrument instrument where "
            + "type(instrument) = Instrument and instrument.id in (:instrumentIds) "
            + "and instrument.deleted = false and instrument.owner is not null and "
            + readablePermission;
    Query<Object[]> query =
        getSession()
            .createQuery(hql, Object[].class)
            .setParameterList("instrumentIds", instrumentIds);
    context.bind(query, null);
    return query
        .getResultStream()
        .collect(
            Collectors.toMap(
                row -> (Long) row[0],
                row -> new BookingItemAccess((String) row[1], Boolean.TRUE.equals(row[2]))));
  }

  @Override
  public Map<Long, Instrument> getBookingRelationshipTargets(Set<Long> instrumentIds) {
    if (instrumentIds.isEmpty()) {
      return Map.of();
    }
    return getSession()
        .createQuery(
            "from Instrument instrument where type(instrument) = Instrument "
                + "and instrument.id in (:instrumentIds)",
            Instrument.class)
        .setParameter("instrumentIds", instrumentIds)
        .getResultStream()
        .collect(Collectors.toMap(Instrument::getId, instrument -> instrument));
  }

  @Override
  public List<Instrument> searchEligibleBookingTargets(String query, int limit, User subject) {
    boolean browse = query == null || query.isBlank();
    boolean byGlobalId = !browse && query.matches("(?i)IN[0-9]+");
    String searchPredicate =
        browse
            ? ""
            : byGlobalId
                ? " and concat('IN', cast(instrument.id as string)) = :query"
                : " and lower(instrument.editInfo.name) like :query escape '!'";
    StringBuilder hql =
        new StringBuilder("from Instrument instrument where type(instrument) = Instrument and")
            .append(" instrument.deleted = false")
            .append(searchPredicate)
            .append(" and not exists (select configuration.id from")
            .append(" BookingConfiguration configuration where configuration.target.type =")
            .append(" :targetType and configuration.target.id = instrument.id)");
    if (!subject.hasSysadminRole()) {
      hql.append(" and instrument.owner.id = :subjectId");
    }
    hql.append(" order by lower(instrument.editInfo.name), instrument.id");
    Query<Instrument> targetQuery =
        getSession()
            .createQuery(hql.toString(), Instrument.class)
            .setParameter(
                "targetType", com.researchspace.model.booking.BookableTargetType.INSTRUMENT)
            .setMaxResults(limit);
    if (!browse) {
      targetQuery.setParameter(
          "query",
          byGlobalId
              ? query.toUpperCase(Locale.ROOT)
              : "%" + LikeEscaper.escape(query.toLowerCase(Locale.ROOT)) + "%");
    }
    if (!subject.hasSysadminRole()) {
      targetQuery.setParameter("subjectId", subject.getId());
    }
    return targetQuery.getResultList();
  }

  @Override
  public Set<Long> searchBookingCatalogueTargetIds(String search, User subject) {
    if (search == null || search.isBlank()) {
      return Set.of();
    }
    InventoryReadQueryContext context = readQueryContext(subject);
    // The parent match is a subquery, not outer joins: Hibernate renders the implicit joins
    // behind location.storedInstrument and parent.owner as inner joins, which would drop every
    // instrument without a parent location from the name and description matches too.
    String hql =
        "select distinct instrument.id from Instrument instrument "
            + "where type(instrument) = Instrument and instrument.deleted = false "
            + "and (lower(instrument.editInfo.name) like :search escape '!' "
            + "or lower(instrument.editInfo.description) like :search escape '!' "
            + "or exists (select location.id from ContainerLocation location "
            + "join location.container parent "
            + "where location.storedInstrument.id = instrument.id and parent.deleted = false and "
            + context.readableContainerPredicate(this, "parent")
            + " and lower(parent.editInfo.name) like :search escape '!'))";
    Query<Long> query =
        getSession()
            .createQuery(hql, Long.class)
            .setParameter(
                "search", "%" + LikeEscaper.escape(search.trim().toLowerCase(Locale.ROOT)) + "%");
    context.bind(query, null);
    return Set.copyOf(query.getResultList());
  }

  private <T> void setBookingCatalogueLocationParameters(
      Query<T> query,
      User caller,
      List<String> groupMembers,
      List<String> groupNames,
      List<String> visibleOwners,
      String locationQuery) {
    query.setParameter("targetType", BookableTargetType.INSTRUMENT);
    query.setParameter("configurationState", BookingConfigurationState.ACTIVE);
    addQueryParams(null, caller, query, visibleOwners, groupMembers, groupNames);
    if (locationQuery != null && !locationQuery.isBlank()) {
      query.setParameter(
          "locationQuery",
          "%" + LikeEscaper.escape(locationQuery.trim().toLowerCase(Locale.ROOT)) + "%");
    }
  }

  @Override
  public Map<Long, BookingCatalogueLocation> getReadableParentLocationSummaries(
      Set<Long> instrumentIds, User caller) {
    if (instrumentIds.isEmpty()) {
      return Map.of();
    }
    InventoryReadQueryContext context = readQueryContext(caller);
    String hql =
        "select instrument.id, parent.id, parent.editInfo.name, parent.containerType "
            + "from Instrument instrument "
            + "join instrument.parentLocation location "
            + "join location.container parent "
            + "where instrument.id in (:instrumentIds) "
            + "and location.storedInstrument.id = instrument.id "
            + "and parent.deleted = false and "
            + context.readableContainerPredicate(this, "parent");
    Query<Object[]> query =
        getSession()
            .createQuery(hql, Object[].class)
            .setParameterList("instrumentIds", instrumentIds);
    context.bind(query, null);
    return query.getResultList().stream()
        .collect(
            Collectors.toMap(
                row -> (Long) row[0],
                row ->
                    new BookingCatalogueLocation(
                        (Long) row[1], (String) row[2], (ContainerType) row[3])));
  }

  @Override
  public Set<Long> findConfiguredInstrumentIdsInReadableParents(Set<Long> parentIds, User caller) {
    if (caller == null
        || !caller.isEnabled()
        || caller.isAccountLocked()
        || (parentIds != null && parentIds.isEmpty())) {
      return Set.of();
    }
    InventoryReadQueryContext context = readQueryContext(caller);
    String hql =
        "select distinct instrument.id from BookingConfiguration configuration, Instrument"
            + " instrument join instrument.parentLocation location join location.container parent"
            + " where configuration.target.type = :targetType"
            + " and configuration.target.id = instrument.id"
            + " and type(instrument) = Instrument and instrument.deleted = false"
            + " and location.storedInstrument.id = instrument.id and parent.deleted = false"
            + (parentIds == null ? "" : " and parent.id in (:parentIds)")
            + " and "
            + context.readableContainerPredicate(this, "parent");
    Query<Long> query =
        getSession()
            .createQuery(hql, Long.class)
            .setParameter("targetType", BookableTargetType.INSTRUMENT);
    if (parentIds != null) {
      query.setParameterList("parentIds", parentIds);
    }
    context.bind(query, null);
    return Set.copyOf(query.getResultList());
  }
}
