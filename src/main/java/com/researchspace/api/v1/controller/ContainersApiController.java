package com.researchspace.api.v1.controller;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.researchspace.api.v1.ContainersApi;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiContainerInfo;
import com.researchspace.api.v1.model.ApiContainerSearchResult;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Container;
import java.io.IOException;
import java.util.List;
import javax.validation.Valid;
import javax.ws.rs.NotFoundException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@ApiController
public class ContainersApiController extends BaseApiInventoryController implements ContainersApi {

  @Autowired private ContainerApiPostValidator apiContainerPostValidator;
  @Autowired private ContainerApiPutValidator apiContainerPutValidator;

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class ApiContainerPut {
    ApiContainer incomingContainer;
    ApiContainer dbContainer;
  }

  @Override
  public ApiContainerSearchResult getTopContainersForUser(
      @Valid InventoryApiPaginationCriteria apiPgCrit,
      @Valid InventoryApiSearchConfig srchConfig,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {

    throwBindExceptionIfErrors(errors);

    if (apiPgCrit == null) {
      apiPgCrit = new InventoryApiPaginationCriteria();
    }
    PaginationCriteria<Container> pgCrit =
        getPaginationCriteriaForApiSearch(apiPgCrit, Container.class);

    String ownedBy = null;
    InventorySearchDeletedOption deletedItemsOption = null;
    if (srchConfig != null) {
      ownedBy = srchConfig.getOwnedBy();
      deletedItemsOption = srchConfig.getDeletedItemsAsEnum();
    }

    ISearchResults<ApiContainerInfo> dbSearchResult =
        containerApiMgr.getTopContainersForUser(pgCrit, ownedBy, deletedItemsOption, user);
    List<ApiContainerInfo> containerInfos = dbSearchResult.getResults();
    setLinksInApiContainerList(containerInfos);

    ApiContainerSearchResult apiSearchResult = new ApiContainerSearchResult();
    apiSearchResult.setTotalHits(dbSearchResult.getTotalHits());
    apiSearchResult.setPageNumber(dbSearchResult.getPageNumber());
    apiSearchResult.setItems(containerInfos);
    apiSearchResult.addNavigationLinks(getInventoryApiBaseURIBuilder(), apiPgCrit, srchConfig);
    return apiSearchResult;
  }

  private void setLinksInApiContainerList(List<? extends ApiContainerInfo> result) {
    for (ApiContainerInfo createdApiContainer : result) {
      buildAndAddInventoryRecordLinks(createdApiContainer);
    }
  }

  @Override
  public ApiContainer getContainerById(
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
    return containerApiMgr.getApiContainerById(id, includeContent, user);
  }

  @Override
  public ApiContainer createNewContainer(
      @RequestBody @Valid ApiContainer container,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {

    validateCreateContainerInput(container, errors);
    throwBindExceptionIfErrors(errors);

    ApiContainer newContainer = containerApiMgr.createNewApiContainer(container, user);
    buildAndAddInventoryRecordLinks(newContainer);
    return newContainer;
  }

  public void validateCreateContainerInput(ApiContainer container, BindingResult errors) {
    apiContainerPostValidator.validate(container, errors);
  }

  @Override
  public ApiContainer updateContainer(
      @PathVariable Long id,
      @RequestBody @Valid ApiContainer containerUpdate,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {
    // validate update request alone
    ApiContainerPut validationData = new ApiContainerPut(containerUpdate, null);
    apiContainerPutValidator.validate(validationData, errors);
    throwBindExceptionIfErrors(errors);

    // check user has permissions to the container
    containerApiMgr.assertUserCanEditContainer(id, user);

    // update incoming object's id which could be omitted
    containerUpdate.setIdIfNotSet(id);

    // validate update request considering pre-existing container data
    ApiContainer dbContainer = containerApiMgr.getApiContainerIfExists(id, user);
    ApiContainerPut validationDataWithDbContainer =
        new ApiContainerPut(containerUpdate, dbContainer);
    apiContainerPutValidator.validateAgainstDbContainer(validationDataWithDbContainer, errors);
    throwBindExceptionIfErrors(errors);

    // update the container
    ApiContainer updatedContainer = containerApiMgr.updateApiContainer(containerUpdate, user);
    buildAndAddInventoryRecordLinks(updatedContainer);
    return updatedContainer;
  }

  @Override
  public ResponseEntity<byte[]> getContainerImage(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) throws IOException {
    return doImageResponse(
        user, () -> containerApiMgr.assertUserCanReadContainer(id, user).getImageFileProperty());
  }

  @Override
  public ResponseEntity<byte[]> getContainerThumbnail(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) throws IOException {
    return doImageResponse(
        user,
        () -> containerApiMgr.assertUserCanReadContainer(id, user).getThumbnailFileProperty());
  }

  @Override
  public ResponseEntity<byte[]> getContainerLocationsImage(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) throws IOException {
    return doImageResponse(
        user,
        () -> containerApiMgr.assertUserCanReadContainer(id, user).getLocationsImageFileProperty());
  }

  @Override
  public ApiContainer deleteContainer(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) {
    containerApiMgr.assertUserCanDeleteContainer(id, user);
    return containerApiMgr.markContainerAsDeleted(id, user);
  }

  @Override
  public ApiContainer restoreDeletedContainer(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) {
    containerApiMgr.assertUserCanDeleteContainer(id, user);
    return containerApiMgr.restoreDeletedContainer(id, user);
  }

  @Override
  public ApiContainer changeContainerOwner(
      @PathVariable Long id,
      @RequestBody @Valid ApiContainer incomingContainer,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {

    // update incoming object's id which could be omitted
    incomingContainer.setIdIfNotSet(id);
    containerApiMgr.assertUserCanTransferContainer(id, user);

    // update container
    ApiContainer updatedContainer =
        containerApiMgr.changeApiContainerOwner(incomingContainer, user);
    buildAndAddInventoryRecordLinks(updatedContainer);

    return updatedContainer;
  }

  @Override
  public ApiContainer duplicate(@PathVariable Long id, @RequestAttribute(name = "user") User user) {
    ApiContainer copy = containerApiMgr.duplicate(id, user);
    buildAndAddInventoryRecordLinks(copy);
    return copy;
  }
}
