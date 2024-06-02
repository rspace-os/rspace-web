package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.WorkbenchApi;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiContainerInfo;
import com.researchspace.api.v1.model.ApiContainerSearchResult;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Container;
import java.util.List;
import javax.validation.Valid;
import javax.ws.rs.NotFoundException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;

@ApiController
public class WorkbenchApiController extends BaseApiInventoryController implements WorkbenchApi {

  @Override
  public ApiContainerSearchResult getWorkbenchesForUser(
      @Valid InventoryApiPaginationCriteria apiPgCrit,
      @RequestParam(name = "ownedBy", required = false) String ownedBy,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {

    throwBindExceptionIfErrors(errors);

    if (apiPgCrit == null) {
      apiPgCrit = new InventoryApiPaginationCriteria();
    }

    PaginationCriteria<Container> pgCrit =
        getPaginationCriteriaForApiSearch(apiPgCrit, Container.class);
    ISearchResults<ApiContainerInfo> dbSearchResult =
        containerApiMgr.getWorkbenchesForUser(pgCrit, ownedBy, user);
    List<ApiContainerInfo> containerInfos = dbSearchResult.getResults();
    setLinksInApiContainerList(containerInfos);

    ApiContainerSearchResult apiSearchResult = new ApiContainerSearchResult();
    apiSearchResult.setTotalHits(dbSearchResult.getTotalHits());
    apiSearchResult.setPageNumber(dbSearchResult.getPageNumber());
    apiSearchResult.setItems(containerInfos);
    apiSearchResult.addNavigationLinks(getInventoryApiBaseURIBuilder(), apiPgCrit, null);
    return apiSearchResult;
  }

  private void setLinksInApiContainerList(List<? extends ApiContainerInfo> result) {
    for (ApiContainerInfo createdApiContainer : result) {
      buildAndAddInventoryRecordLinks(createdApiContainer);
    }
  }

  @Override
  public ApiContainer getWorkbenchById(
      @PathVariable Long id,
      @RequestParam(name = "includeContent", defaultValue = "false", required = false)
          Boolean includeContent,
      @RequestAttribute(name = "user") User user) {

    ApiContainer container = retrieveApiContainerIfExists(id, includeContent, user);
    buildAndAddInventoryRecordLinks(container);
    return container;
  }

  private ApiContainer retrieveApiContainerIfExists(Long id, boolean includeContent, User user) {
    boolean exists = containerApiMgr.exists(id);
    if (!exists) {
      throw new NotFoundException(createNotFoundMessage("Inventory record", id));
    }
    return containerApiMgr.getApiWorkbenchById(id, includeContent, user);
  }
}
