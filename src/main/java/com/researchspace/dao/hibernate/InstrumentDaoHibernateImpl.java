package com.researchspace.dao.hibernate;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.blazebit.persistence.CriteriaBuilder;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.dao.query.CollectionQueryExecutor;
import com.researchspace.dao.query.IndexedTextNarrowing;
import com.researchspace.inventory.model.ApiV2InstrumentResource;
import com.researchspace.model.FileProperty;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.UserGroup;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfigurationState;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.inventory.Container.ContainerType;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InstrumentParentLocationSummary;
import com.researchspace.model.inventory.InstrumentReadSummary;
import com.researchspace.model.resourceaccess.ResourceAudience;
import com.researchspace.search.customfield.RuntimeFieldTextSearch;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.query.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository(value = "instrumentDao")
public class InstrumentDaoHibernateImpl extends InventoryDaoHibernate<Instrument, Long>
    implements InstrumentDao {

  private static final CollectionQueryExecutor<Instrument> COLLECTION_QUERY =
      new CollectionQueryExecutor<>(
          Instrument.class, ApiV2InstrumentResource.DESCRIPTION, "collectionInstrument");

  private record ParentLocationRow(
      Long instrumentId, Long containerId, String containerName, ContainerType containerType) {}

  private record InstrumentNameRow(Long instrumentId, String name) {}

  private String defaultTemplateOwner;

  @Autowired(required = false)
  private RuntimeFieldTextSearch textSearch;

  public InstrumentDaoHibernateImpl(Class<Instrument> persistentClass) {
    super(persistentClass);
  }

  public InstrumentDaoHibernateImpl() {
    super(Instrument.class);
  }

  @Override
  public Optional<Instrument> lockById(Long id) {
    return getSession()
        .createQuery(
            "from Instrument instrument where instrument.id = :id and type(instrument) ="
                + " Instrument",
            Instrument.class)
        .setParameter("id", id)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .uniqueResultOptional();
  }

  @Override
  public boolean hasLockedTransferAuthority(User subject, User owner) {
    List<UserGroup> subjectMemberships =
        getSession()
            .createQuery(
                "from UserGroup membership "
                    + "where membership.user.id = :subjectId "
                    + "and membership.roleInGroup in :transferRoles",
                UserGroup.class)
            .setParameter("subjectId", subject.getId())
            .setParameterList("transferRoles", List.of(RoleInGroup.PI, RoleInGroup.RS_LAB_ADMIN))
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .getResultList();
    if (subjectMemberships.isEmpty()) {
      return false;
    }
    Set<Long> subjectGroupIds =
        subjectMemberships.stream()
            .map(membership -> membership.getGroup().getId())
            .collect(Collectors.toSet());
    List<UserGroup> ownerMemberships =
        getSession()
            .createQuery(
                "from UserGroup membership "
                    + "where membership.user.id = :ownerId and membership.group.id in :groupIds",
                UserGroup.class)
            .setParameter("ownerId", owner.getId())
            .setParameterList("groupIds", subjectGroupIds)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .getResultList();
    return !ownerMemberships.isEmpty();
  }

  @Override
  public ResourcePage<Instrument> getReadableResources(
      ResourceRequest request, AccessResult access) {
    try {
      return readableResourcePage(COLLECTION_QUERY, narrowed(request), access);
    } catch (IndexedTextNarrowing.NoMatch noMatch) {
      return new ResourcePage<>(List.of(), 0);
    }
  }

  @Override
  public long countReadableResources(ResourceRequest request, AccessResult access) {
    try {
      return countReadableResources(COLLECTION_QUERY, narrowed(request), access);
    } catch (IndexedTextNarrowing.NoMatch noMatch) {
      return 0;
    }
  }

  @Override
  public Map<Long, InstrumentParentLocationSummary> getParentLocationSummaries(
      Set<Long> instrumentIds) {
    if (instrumentIds.isEmpty()) {
      return Map.of();
    }
    CriteriaBuilder<ParentLocationRow> query = parentLocationQuery();
    query.whereExpression("instrument.id IN :instrumentIds");
    query.whereExpression("location.storedInstrument.id = instrument.id");
    query.setParameter("instrumentIds", instrumentIds);
    return query.getResultList().stream()
        .collect(
            Collectors.toMap(
                ParentLocationRow::instrumentId,
                row ->
                    new InstrumentParentLocationSummary(
                        row.containerId(), row.containerName(), row.containerType())));
  }

  @Override
  public Map<Long, InstrumentParentLocationSummary> getReadableParentLocationSummaries(
      Set<Long> instrumentIds, User caller) {
    if (instrumentIds.isEmpty()) {
      return Map.of();
    }
    InventoryReadQueryContext context = readQueryContext(caller);
    CriteriaBuilder<ParentLocationRow> query = parentLocationQuery();
    query.whereExpression("instrument.id IN :instrumentIds");
    query.whereExpression("location.storedInstrument.id = instrument.id");
    query.whereExpression("parent.deleted = false");
    query.whereExpression(context.readableContainerPredicate(this, "parent"));
    query.setParameter("instrumentIds", instrumentIds);
    context.bind(query, null);
    return query.getResultList().stream()
        .collect(
            Collectors.toMap(
                ParentLocationRow::instrumentId,
                row ->
                    new InstrumentParentLocationSummary(
                        row.containerId(), row.containerName(), row.containerType())));
  }

  private CriteriaBuilder<ParentLocationRow> parentLocationQuery() {
    return criteriaBuilderFactory()
        .create(getSession(), ParentLocationRow.class)
        .from(Instrument.class, "instrument")
        .innerJoin("instrument.parentLocation", "location")
        .innerJoin("location.container", "parent")
        .selectNew(ParentLocationRow.class)
        .with("instrument.id")
        .with("parent.id")
        .with("parent.editInfo.name")
        .with("parent.containerType")
        .end();
  }

  @Override
  public ResourcePage<InstrumentParentLocationSummary> getBookingCatalogueLocations(
      String query, int page, int limit, User caller, Set<String> readableRoleKeys) {
    if (!caller.hasSysadminRole() && readableRoleKeys.isEmpty()) {
      return new ResourcePage<>(List.of(), 0);
    }
    List<String> groupMembers =
        invPermissionUtils.getUsernameOfUserAndAllMembersOfTheirGroups(caller);
    List<String> groupNames = caller.getGroups().stream().map(Group::getUniqueName).toList();
    List<String> visibleOwners = invPermissionUtils.getOwnersVisibleWithUserRole(caller);
    Set<Long> bookingGroupIds =
        caller.getGroups().stream().map(Group::getId).collect(Collectors.toSet());
    if (bookingGroupIds.isEmpty()) bookingGroupIds = Set.of(-1L);

    String bookingAccess;
    if (caller.hasSysadminRole()) {
      bookingAccess = "1=1";
    } else {
      bookingAccess =
          new StringBuilder(
                  "exists (select assignment.id from ResourceRoleAssignment assignment where")
              .append(
                  " assignment.resourceAccess=configuration.resourceAccess and assignment.roleKey")
              .append(" in (:readableRoleKeys) and (assignment.user.id=:bookingUserId or")
              .append(" assignment.group.id in (:bookingGroupIds) or")
              .append(" assignment.audienceKey=:bookingAudience))")
              .toString();
    }
    String containerAccess =
        readableContainerPredicate(caller, groupMembers, groupNames, visibleOwners, "parent");
    String nameFilter =
        query == null || query.isBlank()
            ? ""
            : " and lower(parent.editInfo.name) like :locationQuery escape '\\'";
    String fromAndWhere =
        new StringBuilder(" from BookingConfiguration configuration, Instrument instrument ")
            .append("join instrument.parentLocation location join location.container parent ")
            .append("where type(instrument)=Instrument and instrument.deleted=false ")
            .append("and configuration.state=:configurationState and configuration.enabled=true ")
            .append(
                "and configuration.target.type=:targetType and"
                    + " configuration.target.id=instrument.id ")
            .append("and location.storedInstrument.id=instrument.id and parent.deleted=false and ")
            .append(bookingAccess)
            .append(" and ")
            .append(containerAccess)
            .append(nameFilter)
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
        countQuery,
        caller,
        readableRoleKeys,
        bookingGroupIds,
        groupMembers,
        groupNames,
        visibleOwners,
        query);
    setBookingCatalogueLocationParameters(
        pageQuery,
        caller,
        readableRoleKeys,
        bookingGroupIds,
        groupMembers,
        groupNames,
        visibleOwners,
        query);
    long total = countQuery.getSingleResult();
    List<InstrumentParentLocationSummary> locations =
        pageQuery
            .getResultStream()
            .map(
                row ->
                    new InstrumentParentLocationSummary(
                        (Long) row[0], (String) row[1], (ContainerType) row[2]))
            .toList();
    return new ResourcePage<>(locations, total);
  }

  private String readableContainerPredicate(
      User caller,
      List<String> groupMembers,
      List<String> groupNames,
      List<String> visibleOwners,
      String alias) {
    String direct =
        getInventoryReadPermissionSqlPredicate(
            caller, groupMembers, groupNames, visibleOwners, alias + ".");
    String childContainer =
        getInventoryReadPermissionSqlPredicate(
            caller, groupMembers, groupNames, List.of(), "childContainer.");
    String childInstrument =
        getInventoryReadPermissionSqlPredicate(
            caller, groupMembers, groupNames, List.of(), "childInstrument.");
    String childSubSample =
        getInventoryReadPermissionSqlPredicate(
            caller, groupMembers, groupNames, List.of(), "childSubSample.sample.");
    return "("
        + direct
        + " or exists (select childLocation.id from ContainerLocation childLocation "
        + "join childLocation.storedContainer childContainer where childLocation.container="
        + alias
        + " and childContainer.deleted=false and "
        + childContainer
        + ") or exists (select childLocation.id from ContainerLocation childLocation "
        + "join childLocation.storedInstrument childInstrument where childLocation.container="
        + alias
        + " and childInstrument.deleted=false and "
        + childInstrument
        + ") or exists (select childLocation.id from ContainerLocation childLocation "
        + "join childLocation.storedSubSample childSubSample where childLocation.container="
        + alias
        + " and childSubSample.deleted=false and "
        + childSubSample
        + "))";
  }

  private <T> void setBookingCatalogueLocationParameters(
      Query<T> query,
      User caller,
      Set<String> readableRoleKeys,
      Set<Long> bookingGroupIds,
      List<String> groupMembers,
      List<String> groupNames,
      List<String> visibleOwners,
      String locationQuery) {
    query.setParameter("targetType", BookableTargetType.INSTRUMENT);
    query.setParameter("configurationState", BookingConfigurationState.ACTIVE);
    if (!caller.hasSysadminRole()) {
      query
          .setParameterList("readableRoleKeys", readableRoleKeys)
          .setParameter("bookingUserId", caller.getId())
          .setParameterList("bookingGroupIds", bookingGroupIds)
          .setParameter("bookingAudience", ResourceAudience.ALL_USERS);
    }
    addQueryParams(null, caller, query, visibleOwners, groupMembers, groupNames);
    if (locationQuery != null && !locationQuery.isBlank()) {
      query.setParameter(
          "locationQuery", "%" + escapeLike(locationQuery.trim().toLowerCase(Locale.ROOT)) + "%");
    }
  }

  @Override
  public Map<Long, InstrumentReadSummary> getReadableSummaries(Set<Long> instrumentIds, User user) {
    if (instrumentIds.isEmpty()) {
      return Map.of();
    }
    InventoryReadQueryContext context = readQueryContext(user);
    String permission = context.permissionPredicate(this, "instrument.");
    CriteriaBuilder<InstrumentReadSummary> query =
        criteriaBuilderFactory()
            .create(getSession(), InstrumentReadSummary.class)
            .from(Instrument.class, "instrument")
            .leftJoinOn("instrument.parentLocation", "location")
            .setOnExpression("location.storedInstrument.id = instrument.id")
            .leftJoin("location.container", "parent")
            .selectNew(InstrumentReadSummary.class)
            .with("instrument.id")
            .with("instrument.editInfo.name")
            .with("instrument.deleted")
            .with("parent.id")
            .with("parent.editInfo.name")
            .with("parent.containerType")
            .end()
            .whereExpression("instrument.id in :instrumentIds")
            .whereExpression("instrument.deleted = false")
            .whereExpression(permission)
            .setParameter("instrumentIds", instrumentIds);
    context.bind(query, null);
    return query
        .getResultStream()
        .collect(Collectors.toMap(InstrumentReadSummary::id, summary -> summary));
  }

  @Override
  public Map<Long, String> getNamesByIds(Set<Long> instrumentIds) {
    if (instrumentIds.isEmpty()) {
      return Map.of();
    }
    return criteriaBuilderFactory()
        .create(getSession(), InstrumentNameRow.class)
        .from(Instrument.class, "instrument")
        .selectNew(InstrumentNameRow.class)
        .with("instrument.id")
        .with("instrument.editInfo.name")
        .end()
        .whereExpression("instrument.id IN :instrumentIds")
        .setParameter("instrumentIds", instrumentIds)
        .getResultStream()
        .collect(Collectors.toMap(InstrumentNameRow::instrumentId, InstrumentNameRow::name));
  }

  @Override
  public Map<Long, InstrumentReadSummary> getBookingSummaries(Set<Long> instrumentIds) {
    if (instrumentIds.isEmpty()) {
      return Map.of();
    }
    return getSession()
        .createQuery(
            "select instrument.id, instrument.editInfo.name, instrument.deleted "
                + "from Instrument instrument where type(instrument) = Instrument "
                + "and instrument.id in (:instrumentIds)",
            Object[].class)
        .setParameter("instrumentIds", instrumentIds)
        .getResultStream()
        .map(
            row ->
                new InstrumentReadSummary(
                    (Long) row[0], (String) row[1], (Boolean) row[2], null, null, null))
        .collect(Collectors.toMap(InstrumentReadSummary::id, summary -> summary));
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
    boolean byGlobalId = query.matches("(?i)IN[0-9]+");
    String searchPredicate =
        byGlobalId
            ? "concat('IN', cast(instrument.id as string)) = :query"
            : "lower(instrument.editInfo.name) like :query escape '\\'";
    StringBuilder hql =
        new StringBuilder("from Instrument instrument where type(instrument) = Instrument and")
            .append(" instrument.deleted = false and ")
            .append(searchPredicate)
            .append(" and not exists (select configuration.id from")
            .append(" BookingConfiguration configuration where configuration.target.type =")
            .append(" :targetType and configuration.target.id = instrument.id");
    if (!subject.hasSysadminRole()) {
      hql.append(" and instrument.owner.id = :subjectId");
    }
    hql.append(" order by lower(instrument.editInfo.name), instrument.id");
    Query<Instrument> targetQuery =
        getSession()
            .createQuery(hql.toString(), Instrument.class)
            .setParameter(
                "query",
                byGlobalId
                    ? query.toUpperCase(Locale.ROOT)
                    : "%" + escapeLike(query.toLowerCase(Locale.ROOT)) + "%")
            .setParameter(
                "targetType", com.researchspace.model.booking.BookableTargetType.INSTRUMENT)
            .setMaxResults(limit);
    if (!subject.hasSysadminRole()) {
      targetQuery.setParameter("subjectId", subject.getId());
    }
    return targetQuery.getResultList();
  }

  @Override
  public Set<Long> findByReadableImmediateParentIds(
      Set<Long> containerIds, Set<Long> workbenchIds, User caller) {
    if (containerIds.isEmpty() && workbenchIds.isEmpty()) {
      return Set.of();
    }
    InventoryReadQueryContext context = readQueryContext(caller);
    String parentType =
        "((parent.id in (:containerIds) and parent.containerType <> :workbenchType)"
            + " or (parent.id in (:workbenchIds) and parent.containerType = :workbenchType))";
    String hql =
        "select distinct instrument.id from Instrument instrument"
            + " join instrument.parentLocation location"
            + " join location.container parent"
            + " where type(instrument) = Instrument and instrument.deleted = false"
            + " and location.storedInstrument.id = instrument.id and parent.deleted = false"
            + " and "
            + parentType
            + " and "
            + context.readableContainerPredicate(this, "parent");
    Query<Long> query = getSession().createQuery(hql, Long.class);
    query
        .setParameterList("containerIds", containerIds.isEmpty() ? Set.of(-1L) : containerIds)
        .setParameterList("workbenchIds", workbenchIds.isEmpty() ? Set.of(-1L) : workbenchIds)
        .setParameter("workbenchType", ContainerType.WORKBENCH);
    context.bind(query, null);
    return Set.copyOf(query.getResultList());
  }

  private ResourceRequest narrowed(ResourceRequest request) {
    return IndexedTextNarrowing.apply(request, ApiV2InstrumentResource.DESCRIPTION, textSearch);
  }

  @Override
  public ISearchResults<Instrument> getInstrumentsForUser(
      PaginationCriteria<Instrument> pgCrit,
      String ownedBy,
      InventorySearchDeletedOption deletedOption,
      String searchTerm,
      User user) {

    InventoryReadQueryContext context = readQueryContext(user);
    String permittedFragment = context.ownedByAndPermitted(this, ownedBy);

    if (pgCrit == null) {
      pgCrit = PaginationCriteria.createDefaultForClass(Instrument.class);
    }
    String orderByFragment = getOrderBySqlFragmentForInventoryRecord(pgCrit);
    String deletedFragment = getDeletedSqlFragmentForInventoryRecord(deletedOption);
    String nameFragment =
        StringUtils.isNotBlank(searchTerm) ? " lower(name) like lower(:searchTerm) " : "";
    int startPosition = pgCrit.getFirstResultIndex();
    int maxResult = pgCrit.getResultsPerPage();

    Query<Long> countQuery =
        sessionFactory
            .getCurrentSession()
            .createQuery(
                "select count(i) from Instrument i where "
                    + connectSqlConditionsWithAnd(
                        deletedFragment, " type(i) = Instrument ", nameFragment)
                    + permittedFragment,
                Long.class);
    Query<Long> countQueryWithParams = context.bind(countQuery, ownedBy);
    if (StringUtils.isNotBlank(searchTerm)) {
      countQueryWithParams.setParameter("searchTerm", "%" + searchTerm + "%");
    }
    long totalCount = countQueryWithParams.getSingleResult();
    if (totalCount == 0) {
      return new SearchResultsImpl<>(List.of(), pgCrit, 0);
    }

    Query<Instrument> pageQuery =
        sessionFactory
            .getCurrentSession()
            .createQuery(
                "from Instrument i where "
                    + connectSqlConditionsWithAnd(
                        deletedFragment, " type(i) = Instrument ", nameFragment)
                    + permittedFragment
                    + orderByFragment,
                Instrument.class)
            .setFirstResult(startPosition)
            .setMaxResults(maxResult);
    Query<Instrument> pageQueryWithParams = context.bind(pageQuery, ownedBy);
    if (StringUtils.isNotBlank(searchTerm)) {
      pageQueryWithParams.setParameter("searchTerm", "%" + searchTerm + "%");
    }
    List<Instrument> page = pageQueryWithParams.list();
    return new SearchResultsImpl<>(page, pgCrit, totalCount);
  }

  @Override
  public List<Instrument> getAllUsingImage(FileProperty fileProperty) {
    return sessionFactory
        .getCurrentSession()
        .createQuery(
            "from Instrument where imageFileProperty=:fileProperty"
                + " OR thumbnailFileProperty=:fileProperty",
            Instrument.class)
        .setParameter("fileProperty", fileProperty)
        .list();
  }

  @Override
  public List<Instrument> findInstrumentsByName(String name, User user) {
    return sessionFactory
        .getCurrentSession()
        .createQuery("from Instrument where editInfo.name=:name and owner=:owner", Instrument.class)
        .setParameter("name", name)
        .setParameter("owner", user)
        .list();
  }

  @Override
  public ISearchResults<Instrument> getInstrumentsForTemplate(
      PaginationCriteria<Instrument> pgCrit,
      Long templateId,
      String ownedBy,
      InventorySearchDeletedOption deletedOption,
      User user) {

    InventoryReadQueryContext context = readQueryContext(user);
    String permittedFragment = context.ownedByAndPermitted(this, ownedBy);

    if (pgCrit == null) {
      pgCrit = PaginationCriteria.createDefaultForClass(Instrument.class);
    }
    String orderByFragment = getOrderBySqlFragmentForInventoryRecord(pgCrit);
    String deletedFragment = getDeletedSqlFragmentForInventoryRecord(deletedOption);
    int startPosition = pgCrit.getFirstResultIndex();
    int maxResult = pgCrit.getResultsPerPage();

    Query<Long> countQuery =
        sessionFactory
            .getCurrentSession()
            .createQuery(
                "select count(i) from Instrument i where "
                    + connectSqlConditionsWithAnd(
                        deletedFragment,
                        " type(i) = Instrument ",
                        " instrumentTemplate.id=:templateId ")
                    + permittedFragment,
                Long.class)
            .setParameter("templateId", templateId);
    Query<Long> countQueryWithParams = context.bind(countQuery, ownedBy);
    long totalCount = countQueryWithParams.getSingleResult();
    if (totalCount == 0) {
      return new SearchResultsImpl<>(List.of(), pgCrit, 0);
    }

    Query<Instrument> pageQuery =
        sessionFactory
            .getCurrentSession()
            .createQuery(
                "from Instrument i where "
                    + connectSqlConditionsWithAnd(
                        deletedFragment,
                        " type(i) = Instrument ",
                        " instrumentTemplate.id=:templateId ")
                    + permittedFragment
                    + orderByFragment,
                Instrument.class)
            .setParameter("templateId", templateId)
            .setFirstResult(startPosition)
            .setMaxResults(maxResult);
    Query<Instrument> pageQueryWithParams = context.bind(pageQuery, ownedBy);
    List<Instrument> page = pageQueryWithParams.list();
    return new SearchResultsImpl<>(page, pgCrit, totalCount);
  }

  @Override
  public List<Instrument> getInstrumentsLinkingOlderTemplateVersionForUser(
      Long templateId, Long version, User user) {
    return sessionFactory
        .getCurrentSession()
        .createQuery(
            "from Instrument where owner=:owner and deleted=false"
                + " and instrumentTemplate.id=:parentTemplateId"
                + " and templateLinkedVersion < :parentTemplateMaxVersion",
            Instrument.class)
        .setParameter("owner", user)
        .setParameter("parentTemplateId", templateId)
        .setParameter("parentTemplateMaxVersion", version)
        .list();
  }

  /*
   * ============
   *  for tests
   * ============
   */
  @Override
  public void resetDefaultTemplateOwner() {
    defaultTemplateOwner = null;
  }
}
