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
import com.researchspace.model.User;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.inventory.Container.ContainerType;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InstrumentParentLocationSummary;
import com.researchspace.model.inventory.InstrumentReadSummary;
import com.researchspace.search.customfield.RuntimeFieldTextSearch;
import java.util.List;
import java.util.Map;
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
  public Map<Long, InstrumentReadSummary> getReadableSummaries(Set<Long> instrumentIds, User user) {
    if (instrumentIds.isEmpty()) {
      return Map.of();
    }
    InventoryReadQueryContext context = readQueryContext(user);
    String permission = context.permissionPredicate(this, "instrument.");
    Query<InstrumentReadSummary> query =
        getSession()
            .createQuery(
                "select new com.researchspace.model.inventory.InstrumentReadSummary("
                    + "instrument.id, instrument.editInfo.name, instrument.deleted, "
                    + "parent.id, parent.editInfo.name, parent.containerType) "
                    + "from Instrument instrument "
                    + "left join instrument.parentLocation location "
                    + "with location.storedInstrument.id = instrument.id "
                    + "left join location.container parent "
                    + "where instrument.id in (:instrumentIds) "
                    + "and instrument.deleted = false and "
                    + permission,
                InstrumentReadSummary.class)
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
    return getSession()
        .createQuery(
            "select instrument.id, instrument.editInfo.name from Instrument instrument"
                + " where instrument.id in (:instrumentIds)",
            Object[].class)
        .setParameter("instrumentIds", instrumentIds)
        .getResultStream()
        .collect(Collectors.toMap(row -> (Long) row[0], row -> (String) row[1]));
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
