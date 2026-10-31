package com.researchspace.dao.hibernate;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.blazebit.persistence.CriteriaBuilder;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.dao.query.CollectionQueryExecutor;
import com.researchspace.inventory.model.ApiV2InstrumentResource;
import com.researchspace.model.FileProperty;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.UserGroup;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.RelationshipReadAccess;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.ResourceSlice;
import com.researchspace.model.inventory.Container.ContainerType;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InstrumentReadSummary;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.query.Query;
import org.springframework.stereotype.Repository;

@Repository(value = "instrumentDao")
public class InstrumentDaoHibernateImpl extends InventoryDaoHibernate<Instrument, Long>
    implements InstrumentDao {

  private static final CollectionQueryExecutor<Instrument> COLLECTION_QUERY =
      new CollectionQueryExecutor<>(
          Instrument.class, ApiV2InstrumentResource.DESCRIPTION, "collectionInstrument");

  public record InstrumentNameRow(Long instrumentId, String name) {}

  public record InstrumentReadSummaryRow(
      Long instrumentId, String name, Boolean deleted, Long parentContainerId) {}

  public record InstrumentScalarRow(
      Long instrumentId, String name, Boolean deleted, Long parentContainerId) {}

  private String defaultTemplateOwner;

  public InstrumentDaoHibernateImpl(Class<Instrument> persistentClass) {
    super(persistentClass);
  }

  public InstrumentDaoHibernateImpl() {
    super(Instrument.class);
  }

  @Override
  public Optional<Instrument> lockById(Long id) {
    // Refresh cascades into instrument children, whose tables are outside this query's auto-flush
    // space. Flush them explicitly so refresh cannot replace pending child changes.
    getSession().flush();
    return getSession()
        .createQuery(
            "from Instrument instrument where instrument.id = :id and type(instrument) ="
                + " Instrument",
            Instrument.class)
        .setParameter("id", id)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .uniqueResultOptional()
        .map(
            instrument -> {
              // The entity may already be in the session from a readable precheck. Refresh it so
              // permission rechecks under the lock observe the current sharing state.
              getSession().refresh(instrument, LockModeType.PESSIMISTIC_WRITE);
              return instrument;
            });
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
    return readableResourcePage(COLLECTION_QUERY, request, access);
  }

  @Override
  public ResourcePage<InstrumentReadSummary> getReadableResourceSummaries(
      ResourceRequest request, AccessResult access) {
    if (access.isDenied()) {
      return new ResourcePage<>(List.of(), 0);
    }
    ResourcePage<InstrumentScalarRow> rows =
        COLLECTION_QUERY.projectedPage(
            criteriaBuilderFactory(),
            getSession(),
            request,
            access.constraintOrEmpty().map(COLLECTION_QUERY::compileConstraint).orElse(null),
            RelationshipReadAccess.none(),
            InstrumentScalarRow.class,
            List.of("id", "editInfo.name", "deleted", "parentLocation.container.id"));
    return new ResourcePage<>(
        rows.resources().stream()
            .map(
                row ->
                    new InstrumentReadSummary(
                        row.instrumentId(), row.name(), row.deleted(), row.parentContainerId()))
            .toList(),
        rows.total());
  }

  @Override
  public ResourceSlice<InstrumentReadSummary> getReadableResourceSummarySlice(
      ResourceRequest request, AccessResult access) {
    if (access.isDenied()) {
      return new ResourceSlice<>(List.of(), false);
    }
    ResourceSlice<InstrumentScalarRow> rows =
        COLLECTION_QUERY.projectedSlice(
            criteriaBuilderFactory(),
            getSession(),
            request,
            access.constraintOrEmpty().map(COLLECTION_QUERY::compileConstraint).orElse(null),
            RelationshipReadAccess.none(),
            InstrumentScalarRow.class,
            List.of("id", "editInfo.name", "deleted", "parentLocation.container.id"));
    return new ResourceSlice<>(
        rows.resources().stream()
            .map(
                row ->
                    new InstrumentReadSummary(
                        row.instrumentId(), row.name(), row.deleted(), row.parentContainerId()))
            .toList(),
        rows.hasNext());
  }

  @Override
  public long countReadableResources(ResourceRequest request, AccessResult access) {
    return countReadableResources(COLLECTION_QUERY, request, access);
  }

  @Override
  public Map<Long, InstrumentReadSummary> getReadableSummaries(Set<Long> instrumentIds, User user) {
    if (instrumentIds.isEmpty()) {
      return Map.of();
    }
    InventoryReadQueryContext context = readQueryContext(user);
    String permission = context.permissionPredicate(this, "instrument.");
    CriteriaBuilder<InstrumentReadSummaryRow> query =
        criteriaBuilderFactory()
            .create(getSession(), InstrumentReadSummaryRow.class)
            .from(Instrument.class, "instrument")
            .selectNew(InstrumentReadSummaryRow.class)
            .with("instrument.id")
            .with("instrument.editInfo.name")
            .with("instrument.deleted")
            .with("instrument.parentLocation.container.id")
            .end()
            .whereExpression("instrument.id in :instrumentIds")
            .whereExpression("instrument.deleted = false")
            .whereExpression(permission)
            .setParameter("instrumentIds", instrumentIds);
    context.bind(query, null);
    return query
        .getResultStream()
        .map(
            row ->
                new InstrumentReadSummary(
                    row.instrumentId(), row.name(), row.deleted(), row.parentContainerId()))
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
