package com.researchspace.api.v1.controller;

import static com.researchspace.api.v1.controller.InventoryApiSearchConfig.modifyTagSearch;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.axiope.search.InventorySearchConfig.InventorySearchType;
import com.axiope.search.SearchManager;
import com.researchspace.api.v1.InventorySearchApi;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventorySearchResult;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.Sample;
import java.util.List;
import javax.validation.Valid;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestAttribute;

@ApiController
public class InventorySearchApiController extends BaseApiInventoryController
    implements InventorySearchApi {

  @Autowired private SearchManager searchManager;

  @Override
  public ApiInventorySearchResult searchInventoryRecords(
      @Valid InventoryApiPaginationCriteria apiPgCrit,
      @Valid InventoryApiSearchConfig srchConfig,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {

    log.info(
        "searching inventory, incoming pagination is {}, srchConfig is {}", apiPgCrit, srchConfig);
    throwBindExceptionIfErrors(errors);

    /* convert/check incoming parameters */
    srchConfig.setQuery(modifyTagSearch(srchConfig.getQuery()));
    String searchQuery = "";
    InventorySearchType searchType = InventorySearchType.ALL;
    GlobalIdentifier parentOid = null;
    InventorySearchDeletedOption deletedItemsOption = InventorySearchDeletedOption.EXCLUDE;
    if (srchConfig != null) {
      if (srchConfig.getQuery() != null) {
        searchQuery = srchConfig.getQuery();
      }
      if (srchConfig.getResultType() != null) {
        searchType = srchConfig.getResultTypeAsEnum();
      }
      if (srchConfig.getParentGlobalId() != null) {
        parentOid = new GlobalIdentifier(srchConfig.getParentGlobalId());
      }
      if (srchConfig.getDeletedItems() != null) {
        deletedItemsOption = srchConfig.getDeletedItemsAsEnum();
      }
    }
    if (apiPgCrit == null) {
      apiPgCrit = new InventoryApiPaginationCriteria();
    }

    /* there are some cases where we know immediately there won't be any results */
    if (!isValidSearchConfig(parentOid, searchType)) {
      return ApiInventorySearchResult.emptyResult();
    }

    /* proceed with search */
    ApiInventorySearchResult apiSearchResult;
    if (parentOid != null && StringUtils.isBlank(searchQuery)) {
      apiSearchResult =
          runSearchByParentOidWithNoSearchTerm(
              user, srchConfig.getOwnedBy(), apiPgCrit, searchType, deletedItemsOption, parentOid);
    } else {
      PaginationCriteria<InventoryRecord> pgCrit =
          getPaginationCriteriaForApiSearch(apiPgCrit, InventoryRecord.class);
      apiSearchResult =
          searchManager.searchInventoryWithSimpleQuery(
              searchQuery,
              searchType,
              parentOid,
              srchConfig.getOwnedBy(),
              deletedItemsOption,
              pgCrit,
              user);
    }

    /* process the results */
    setLinksInApiInventoryRecordList(apiSearchResult.getRecords());
    apiSearchResult.addNavigationLinks(getInventoryApiBaseURIBuilder(), apiPgCrit, srchConfig);
    return apiSearchResult;
  }

  private boolean isValidSearchConfig(GlobalIdentifier parentOid, InventorySearchType searchType) {
    if (parentOid != null) {
      if (GlobalIdPrefix.SA.equals(parentOid.getPrefix())
          && !(searchType.equals(InventorySearchType.ALL)
              || searchType.equals(InventorySearchType.SUBSAMPLE))) {
        /* subsamples are only children of the sample, we can reject at this point */
        return false;
      }
      if ((GlobalIdPrefix.IC.equals(parentOid.getPrefix())
              || GlobalIdPrefix.BE.equals(parentOid.getPrefix()))
          && (searchType.equals(InventorySearchType.SAMPLE)
              || searchType.equals(InventorySearchType.TEMPLATE))) {
        /* subsamples and subcontainers are only children of the container, we can reject at this point */
        return false;
      }
    }
    return true;
  }

  private ApiInventorySearchResult runSearchByParentOidWithNoSearchTerm(
      User user,
      String ownedBy,
      InventoryApiPaginationCriteria apiPgCrit,
      InventorySearchType searchType,
      InventorySearchDeletedOption deletedItemsOption,
      GlobalIdentifier parentOid) {

    /* without search term there is no need for running lucene search,
     * we just retrieve objects directly from db */

    PaginationCriteria<InventoryRecord> pgCrit =
        getPaginationCriteriaForApiSearch(apiPgCrit, InventoryRecord.class);
    switch (parentOid.getPrefix()) {
      case IC:
      case BE:
        return findDirectChildrenOfContainer(
            parentOid.getDbId(), ownedBy, searchType, deletedItemsOption, pgCrit, user);
      case IT:
        PaginationCriteria<Sample> samplePgCrit =
            getPaginationCriteriaForApiSearch(apiPgCrit, Sample.class);
        return findSamplesFromTemplate(
            parentOid.getDbId(), ownedBy, searchType, deletedItemsOption, samplePgCrit, user);
      case SA:
        return findSubSamplesForSample(
            parentOid.getDbId(), ownedBy, searchType, deletedItemsOption, pgCrit, user);
      case BA:
        return findBasketItems(
            parentOid.getDbId(), ownedBy, searchType, deletedItemsOption, pgCrit, user);
      default:
        throw new IllegalArgumentException("unknown parent global id: " + parentOid);
    }
  }

  private ApiInventorySearchResult findDirectChildrenOfContainer(
      Long containerId,
      String ownedBy,
      InventorySearchType searchType,
      InventorySearchDeletedOption deletedItemsOption,
      PaginationCriteria<InventoryRecord> pgCrit,
      User user) {

    if (InventorySearchDeletedOption.DELETED_ONLY.equals(deletedItemsOption)) {
      // containers don't hold deleted items
      return ApiInventorySearchResult.emptyResult();
    }

    return containerApiMgr.searchForContentOfContainer(
        containerId, ownedBy, searchType, pgCrit, user);
  }

  private ApiInventorySearchResult findSamplesFromTemplate(
      Long templateId,
      String ownedBy,
      InventorySearchType searchType,
      InventorySearchDeletedOption deletedItemsOption,
      PaginationCriteria<Sample> pgCrit,
      User user) {

    if (searchType != InventorySearchType.ALL && searchType != InventorySearchType.SAMPLE) {
      return ApiInventorySearchResult.emptyResult();
    }
    return sampleApiMgr.getSamplesCreatedFromTemplate(
        templateId, ownedBy, deletedItemsOption, pgCrit, user);
  }

  private ApiInventorySearchResult findSubSamplesForSample(
      Long sampleId,
      String ownedBy,
      InventorySearchType searchType,
      InventorySearchDeletedOption deletedItemsOption,
      PaginationCriteria<InventoryRecord> pgCrit,
      User user) {

    if (searchType != InventorySearchType.ALL && searchType != InventorySearchType.SUBSAMPLE) {
      return ApiInventorySearchResult.emptyResult();
    }
    return sampleApiMgr.searchSubSamplesBySampleId(
        sampleId, ownedBy, deletedItemsOption, pgCrit, user);
  }

  private ApiInventorySearchResult findBasketItems(
      Long basketId,
      String ownedBy,
      InventorySearchType searchType,
      InventorySearchDeletedOption deletedItemsOption,
      PaginationCriteria<InventoryRecord> pgCrit,
      User user) {

    return basketApiMgr.searchForBasketContent(
        basketId, ownedBy, searchType, deletedItemsOption, pgCrit, user);
  }

  private void setLinksInApiInventoryRecordList(List<? extends ApiInventoryRecordInfo> result) {
    for (ApiInventoryRecordInfo invRec : result) {
      buildAndAddInventoryRecordLinks(invRec);
    }
  }
}
