package com.researchspace.dao.hibernate;

import com.axiope.search.IFileSearcher;
import com.axiope.search.IFullTextSearcher;
import com.axiope.search.InventorySearchConfig;
import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.axiope.search.InventorySearchConfig.InventorySearchType;
import com.axiope.search.SearchConfig;
import com.axiope.search.SearchConstants;
import com.axiope.search.SearchQueryParseException;
import com.axiope.search.SearchUtils;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.dao.DAOUtils;
import com.researchspace.dao.RecordGroupSharingDao;
import com.researchspace.dao.RecordUserFavoritesDao;
import com.researchspace.model.EcatCommentItem;
import com.researchspace.model.IFieldLinkableElement;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.MovableInventoryRecord;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.BaseRecordAdaptable;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.search.impl.LuceneSearchTermListFactory;
import com.researchspace.search.impl.LuceneSrchCfg;
import com.researchspace.search.impl.PostTextSearchResultFilterer;
import com.researchspace.service.impl.CustomFormAppInitialiser;
import com.researchspace.service.inventory.BarcodeApiManager;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import com.researchspace.service.inventory.InventoryRecordRetriever;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.lucene.search.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.search.FullTextQuery;
import org.hibernate.search.FullTextSession;
import org.hibernate.search.Search;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Performs Lucene-based search operations. */
@Component("fullTextSearcher")
public class FullTextSearcherImpl implements IFullTextSearcher {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private final DAOUtils daoUtils;
  private final IPermissionUtils permissionUtils;
  private final InventoryPermissionUtils inventoryPermissionUtils;
  private final SessionFactory sessionFactory;
  private final LuceneSearchTermListFactory termListFactory;
  private final IFileSearcher fileSearcher;
  private final RecordUserFavoritesDao recordUserFavoritesDao;
  private final RecordGroupSharingDao recordGroupSharingDao;

  public FullTextSearcherImpl(
      DAOUtils daoUtils,
      IPermissionUtils permissionUtils,
      InventoryPermissionUtils inventoryPermissionUtils,
      SessionFactory sessionFactory,
      LuceneSearchTermListFactory termListFactory,
      IFileSearcher fileSearcher,
      RecordUserFavoritesDao recordUserFavoritesDao,
      RecordGroupSharingDao recordGroupSharingDao) {
    this.daoUtils = daoUtils;
    this.permissionUtils = permissionUtils;
    this.inventoryPermissionUtils = inventoryPermissionUtils;
    this.sessionFactory = sessionFactory;
    this.termListFactory = termListFactory;
    this.fileSearcher = fileSearcher;
    this.recordUserFavoritesDao = recordUserFavoritesDao;
    this.recordGroupSharingDao = recordGroupSharingDao;
  }

  private final RSQueryBuilder queryBuilder = new RSQueryBuilder();
  private BaseRecordAdaptable baseRecordAdapter;

  @Override
  public void setBaseRecordAdaptable(BaseRecordAdaptable baseRecordAdapter) {
    this.baseRecordAdapter = baseRecordAdapter;
  }

  @SuppressWarnings("unchecked")
  @Override
  public ISearchResults<BaseRecord> getSearchedElnRecords(SearchConfig searchConfig)
      throws SearchQueryParseException {

    LuceneSrchCfg luceneSearchConfig = new LuceneSrchCfg(searchConfig, termListFactory);
    List<BaseRecord> hits;
    try {
      List<IFieldLinkableElement> hibList = getElnHibernateList(luceneSearchConfig);
      hits = filterAndSortQueryList(hibList, luceneSearchConfig);

    } catch (Exception e) {
      log.error("Error in getting the Lucene query list", e);
      throw new SearchQueryParseException(e);
    }
    if (hits.isEmpty()) {
      return SearchResultsImpl.emptyResult(
          (PaginationCriteria<BaseRecord>) luceneSearchConfig.getPaginationCriteria());
    }

    ISearchResults<BaseRecord> recordHits =
        new SearchResultsImpl<>(
            hits,
            luceneSearchConfig.getPaginationCriteria().getPageNumber().intValue(),
            hits.size());
    ISearchResults<BaseRecord> records = repaginateResults(luceneSearchConfig, recordHits);
    // Just unproxy if need be at the end once we have final result.
    records.getResults().replaceAll(daoUtils::initializeAndUnproxy);
    return records;
  }

  /** Paginate the results. */
  <T> ISearchResults<T> repaginateResults(LuceneSrchCfg srchConfig, ISearchResults<T> srchResults) {

    PaginationCriteria<?> pgCrit = srchConfig.getPaginationCriteria();
    int pageSize = pgCrit == null ? srchConfig.getPageSize() : pgCrit.getResultsPerPage();
    int startPage = pgCrit == null ? srchConfig.getPageNumber() : pgCrit.getPageNumber().intValue();

    int totalHits = srchResults.getResults().size();
    log.info(
        "Paginating results for pageSize={}, startPage={}, for results of size {}",
        pageSize,
        startPage,
        totalHits);

    List<T> sub = SearchUtils.repaginateResults(srchResults.getResults(), pageSize, startPage);
    return new SearchResultsImpl<>(sub, startPage, totalHits, pageSize);
  }

  /** Does final filtering on results retrieved by Lucene */
  @SuppressWarnings("unchecked")
  private List<BaseRecord> filterAndSortQueryList(
      List<IFieldLinkableElement> hits, LuceneSrchCfg srchConfig) throws IOException {
    List<BaseRecord> hibernateBaseRecordList = new ArrayList<>();
    List<BaseRecord> attachmentBaseRecordList = new ArrayList<>();

    if (!hits.isEmpty()) {
      hibernateBaseRecordList =
          new PostTextSearchResultFilterer(filterResultsByPermission(srchConfig, hits), srchConfig)
              .filterAll();
      if (srchConfig.getFilters().isDocumentsFilter()) {
        hibernateBaseRecordList =
            hibernateBaseRecordList.stream()
                .filter(BaseRecord::isStructuredDocument)
                .collect(Collectors.toList());
      }
      if (srchConfig.getFilters().isOntologiesFilter()) {
        hibernateBaseRecordList =
            hibernateBaseRecordList.stream()
                .filter(
                    br ->
                        br.isStructuredDocument()
                            && br.asStrucDoc()
                                .getFormName()
                                .equals(CustomFormAppInitialiser.ONTOLOGY_FORM_NAME))
                .collect(Collectors.toList());
      }
      if (srchConfig.getFilters().isTemplatesFilter()) {
        hibernateBaseRecordList =
            hibernateBaseRecordList.stream()
                .filter(BaseRecord::isTemplate)
                .collect(Collectors.toList());
      }
    }

    Optional<String> fullTextOption = srchConfig.getFullTextOption();
    if (fullTextOption.isPresent()
        && !srchConfig.isNotebookFilter()
        && !srchConfig.getFilters().isSomeFilterActive()) {
      List<BaseRecord> attachmentList = getAttachmentList(srchConfig, fullTextOption.get());
      if (!attachmentList.isEmpty()) {
        attachmentBaseRecordList =
            new PostTextSearchResultFilterer(
                    filterResultsByPermission(srchConfig, attachmentList), srchConfig)
                .filterAll();
      }
    }

    List<BaseRecord> resultList = new ArrayList<>();
    resultList.addAll(hibernateBaseRecordList);
    resultList.addAll(attachmentBaseRecordList);

    if (srchConfig.getFilters().isSharedFilter()) {
      List<BaseRecord> sharedRecords =
          recordGroupSharingDao.getSharedRecordsWithUser(srchConfig.getAuthenticatedUser());
      resultList.retainAll(sharedRecords);
    }

    if (srchConfig.getFilters().isFavoritesFilter()) {
      List<BaseRecord> userFavourites =
          recordUserFavoritesDao.getFavoriteRecordsByUser(
              srchConfig.getAuthenticatedUser().getId());
      resultList.retainAll(userFavourites);
    }

    // When we retrieve only results from LuceneFTsearchIndices.
    // We don't need order the results.
    if (!hibernateBaseRecordList.isEmpty()) {
      PaginationCriteria<BaseRecord> baseRecPgCrit =
          (PaginationCriteria<BaseRecord>) srchConfig.getPaginationCriteria();
      SearchUtils.sortList(resultList, baseRecPgCrit);
    }

    return resultList;
  }

  /** Filter textFieldHits list checking if authenticated user has "READ" permission. */
  private List<BaseRecord> filterResultsByPermission(
      LuceneSrchCfg srchConfig, List<? extends IFieldLinkableElement> textFieldHits) {

    List<BaseRecord> filtered = new LinkedList<>();
    Set<Long> sids = new HashSet<>(); // reduce duplicate

    for (IFieldLinkableElement field : textFieldHits) {
      Optional<BaseRecord> owningRecordOpt = baseRecordAdapter.getAsBaseRecord(field);
      if (owningRecordOpt.isEmpty()) {
        log.warn("Couldn't adapt null result to BaseRecord");
        continue;
      }

      BaseRecord owningRecord = owningRecordOpt.get();
      Long baseRecordId = owningRecord.getId();
      if (!sids.contains(baseRecordId)
          && permissionUtils.isPermitted(
              owningRecord, PermissionType.READ, srchConfig.getAuthenticatedUser())) {
        filtered.add(owningRecord);
        sids.add(owningRecord.getId());
      }
    }

    return filtered;
  }

  /**
   * Search through ELN records.
   *
   * @return List<IFieldLinkableElement> found elements
   */
  @SuppressWarnings("unchecked")
  List<IFieldLinkableElement> getElnHibernateList(LuceneSrchCfg srchConfig) {
    FullTextSession fssn = getFullTextSession();
    Query query = queryBuilder.getLuceneQuery(fssn, srchConfig, StructuredDocument.class);
    if (query == null) {
      log.info("Field is NULL on search");
      return new ArrayList<>();
    }
    log.debug(query.toString());

    Class<?>[] resultClasses =
        new Class[] {BaseRecord.class, StructuredDocument.class, EcatCommentItem.class};
    FullTextQuery hibQuery = fssn.createFullTextQuery(query, resultClasses);
    hibQuery.setMaxResults(srchConfig.getMaxResults());

    return (List<IFieldLinkableElement>) hibQuery.list();
  }

  /** Returns Full Text Session */
  private FullTextSession getFullTextSession() {
    Session ssnx = sessionFactory.getCurrentSession();
    return Search.getFullTextSession(ssnx);
  }

  /**
   * Searching content in Ecat Documents File (pdf, docs,...). The result is included in the full
   * text search.
   */
  private List<BaseRecord> getAttachmentList(LuceneSrchCfg config, String term) throws IOException {
    return getAttachmentList(config, term, false);
  }

  @SuppressWarnings("unchecked")
  private List<BaseRecord> getAttachmentList(LuceneSrchCfg config, String term, boolean getAbsolute)
      throws IOException {
    if (term.startsWith(SearchConstants.NATIVE_LUCENE_PREFIX)) {
      term = term.substring(2);
    }

    PaginationCriteria<BaseRecord> workspacePgCrit =
        (PaginationCriteria<BaseRecord>) config.getPaginationCriteria();
    return fileSearcher
        .searchContents(term, workspacePgCrit, config.getAuthenticatedUser())
        .getResults();
  }

  @SuppressWarnings("unchecked")
  List<InventoryRecord> getLuceneInventoryQueryList(LuceneSrchCfg srchConfig) {
    FullTextSession fssn = getFullTextSession();
    Query query = queryBuilder.getLuceneQuery(fssn, srchConfig, Container.class);
    if (query == null) {
      log.info("Query is null on search, returning empty result list");
      return new ArrayList<>();
    }
    log.debug(query.toString());

    Class<?>[] resultClasses;
    InventorySearchType searchType = srchConfig.getSearchType();
    switch (searchType) {
      case SAMPLE:
      case TEMPLATE:
        resultClasses = new Class[] {Sample.class};
        break;
      case SUBSAMPLE:
        resultClasses = new Class[] {SubSample.class};
        break;
      case CONTAINER:
        resultClasses = new Class[] {Container.class};
        break;
      case ALL:
        resultClasses = new Class[] {Sample.class, SubSample.class, Container.class};
        break;
      default:
        throw new IllegalArgumentException("unknown requested search type: " + searchType);
    }
    FullTextQuery hibQuery = fssn.createFullTextQuery(query, resultClasses);
    hibQuery.setMaxResults(srchConfig.getMaxResults());
    List<InventoryRecord> result = hibQuery.list();

    if (!result.isEmpty()) {
      PaginationCriteria<InventoryRecord> baseRecPgCrit =
          (PaginationCriteria<InventoryRecord>) srchConfig.getPaginationCriteria();
      SearchUtils.sortInventoryList(result, baseRecPgCrit);
    }

    return result;
  }

  @Override
  public ISearchResults<InventoryRecord> getSearchedInventoryRecords(
      InventorySearchConfig srchConfigInput) {
    LuceneSrchCfg srchConfig = new LuceneSrchCfg(srchConfigInput, termListFactory);
    List<InventoryRecord> luceneHits =
        getLuceneInventoryQueryList(srchConfig).stream()
            .filter(
                rec ->
                    isNotOwnedByDefaultTemplatesOwnerOrTemplate(
                        rec, srchConfigInput.getDefaultTemplatesOwner()))
            .filter(rec -> canCurrentUserReadInvRec(rec, srchConfigInput.getAuthenticatedUser()))
            .collect(Collectors.toList());
    List<InventoryRecord> dbHits =
        findInvRecordsWithGlobalIdOrBarcodeMatchingSearchQuery(srchConfigInput);
    List<InventoryRecord> finalHits =
        Stream.concat(luceneHits.stream(), dbHits.stream())
            .distinct()
            .filter(rec -> isMatchingDeletedItemsOption(rec, srchConfigInput.getDeletedOption()))
            .filter(this::isNotSubSampleOfTemplate)
            .filter(this::isNotWorkbench)
            .filter(rec -> isMatchingTemplateOption(rec, srchConfigInput.getSearchType()))
            .collect(Collectors.toList());
    finalHits = limitToRecordsWithGlobalId(finalHits, srchConfigInput.getLimitResultsToGlobalIds());

    ISearchResults<InventoryRecord> searchResults =
        new SearchResultsImpl<>(
            finalHits,
            srchConfig.getPaginationCriteria().getPageNumber().intValue(),
            finalHits.size());
    return repaginateResults(srchConfig, searchResults);
  }

  @Autowired private InventoryRecordRetriever invRecRetriever;

  @Autowired private BarcodeApiManager barcodeApiMgr;

  private List<InventoryRecord> findInvRecordsWithGlobalIdOrBarcodeMatchingSearchQuery(
      InventorySearchConfig srchConfigInput) {
    if (ArrayUtils.isEmpty(srchConfigInput.getOptions())
        || !srchConfigInput.getOptions()[0].equals(SearchConstants.INVENTORY_SEARCH_OPTION)) {
      return null;
    }
    String query = srchConfigInput.getOriginalSearchQuery();

    List<InventoryRecord> hits = new ArrayList<>();
    // add global id match
    if (GlobalIdentifier.isValid(query)) {
      InventoryRecord foundRec =
          invRecRetriever.getInvRecordByGlobalId(new GlobalIdentifier(query));
      addSearchByIdResultIfMatchingSearchConfig(foundRec, hits, srchConfigInput);
    }
    // add barcode match
    List<InventoryRecord> itemsWithMatchingBarcode = barcodeApiMgr.findItemsByBarcodeData(query);
    for (InventoryRecord foundRec : itemsWithMatchingBarcode) {
      addSearchByIdResultIfMatchingSearchConfig(foundRec, hits, srchConfigInput);
    }
    return hits;
  }

  private void addSearchByIdResultIfMatchingSearchConfig(
      InventoryRecord foundRec, List<InventoryRecord> hits, InventorySearchConfig srchConfigInput) {

    if (foundRec == null
        || hits.contains(foundRec)
        || !isMatchingSearchType(foundRec, srchConfigInput.getSearchType())
        || !isMatchingParentOid(foundRec, srchConfigInput.getParentOid())) {
      return;
    }

    hits.add(foundRec);
  }

  private boolean isMatchingSearchType(InventoryRecord foundRec, InventorySearchType searchType) {
    return searchType == null
        || searchType.equals(InventorySearchType.ALL)
        || searchType.toString().equals(foundRec.getType().toString())
        || (searchType.equals(InventorySearchType.TEMPLATE)
            && foundRec.isSample()
            && ((Sample) foundRec).isTemplate());
  }

  private boolean isMatchingDeletedItemsOption(
      InventoryRecord record, InventorySearchDeletedOption deletedOption) {
    switch (deletedOption) {
      case DELETED_ONLY:
        return record.isDeleted();
      case EXCLUDE:
        return !record.isDeleted();
      case INCLUDE:
      default:
        return true;
    }
  }

  private boolean isMatchingParentOid(InventoryRecord foundRec, GlobalIdentifier parentOid) {
    if (parentOid == null) {
      return true;
    }
    if (GlobalIdPrefix.IC.equals(parentOid.getPrefix())
        || GlobalIdPrefix.BE.equals(parentOid.getPrefix())) {
      if (foundRec instanceof MovableInventoryRecord) {
        Container foundRecParent = ((MovableInventoryRecord) foundRec).getParentContainer();
        return foundRecParent != null && foundRecParent.getOid().equals(parentOid);
      }
    } else if (GlobalIdPrefix.IT.equals(parentOid.getPrefix())) {
      return foundRec.isSample()
          && parentOid.getDbId().equals(((Sample) foundRec).getParentTemplateId());
    } else if (GlobalIdPrefix.SA.equals(parentOid.getPrefix())) {
      return foundRec.isSubSample()
          && parentOid.getDbId().equals(((SubSample) foundRec).getParentSampleId());
    }
    return false;
  }

  private boolean isNotSubSampleOfTemplate(InventoryRecord record) {
    return !(record.isSubSample() && ((SubSample) record).getSample().isTemplate());
  }

  private boolean isNotWorkbench(InventoryRecord record) {
    return !(record.isContainer() && ((Container) record).isWorkbench());
  }

  private boolean isMatchingTemplateOption(InventoryRecord record, InventorySearchType searchType) {
    boolean isTemplate = record.isSample() && ((Sample) record).isTemplate();
    if (isTemplate
        && !(searchType == InventorySearchType.ALL || searchType == InventorySearchType.TEMPLATE)) {
      return false;
    }
    return isTemplate || searchType != InventorySearchType.TEMPLATE;
  }

  private boolean isNotOwnedByDefaultTemplatesOwnerOrTemplate(
      InventoryRecord rec, String defaultTemplatesOwner) {
    if (defaultTemplatesOwner == null) {
      return true; // we were not adding default templates owner to search filter
    }
    boolean notOwnedByDefaultTemplateOwner =
        !defaultTemplatesOwner.equals(rec.getOwner().getUsername());
    boolean isSampleTemplate = rec.isSample() && ((Sample) rec).isTemplate();
    return notOwnedByDefaultTemplateOwner || isSampleTemplate;
  }

  private boolean canCurrentUserReadInvRec(InventoryRecord rec, User authenticatedUser) {
    return inventoryPermissionUtils.canUserReadInventoryRecord(rec, authenticatedUser);
  }

  private List<InventoryRecord> limitToRecordsWithGlobalId(
      List<InventoryRecord> hits, List<String> limitResultsToGlobalIds) {
    if (limitResultsToGlobalIds == null || CollectionUtils.isEmpty(hits)) {
      return hits;
    }
    return hits.stream()
        .filter(hit -> limitResultsToGlobalIds.contains((hit.getGlobalIdentifier())))
        .collect(Collectors.toList());
  }
}
