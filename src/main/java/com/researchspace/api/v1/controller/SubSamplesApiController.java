package com.researchspace.api.v1.controller;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.researchspace.api.v1.SubSamplesApi;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventoryRecordRevisionList;
import com.researchspace.api.v1.model.ApiInventoryRecordRevisionList.ApiInventoryRecordRevision;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.api.v1.model.ApiSubSampleNote;
import com.researchspace.api.v1.model.ApiSubSampleSearchResult;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.service.inventory.InventoryAuditApiManager;
import com.researchspace.service.inventory.impl.SubSampleApiPostConfig;
import com.researchspace.service.inventory.impl.SubSampleDuplicateConfig;
import jakarta.validation.Valid;
import jakarta.ws.rs.NotFoundException;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;

@ApiController
public class SubSamplesApiController extends BaseApiInventoryController implements SubSamplesApi {

  @Autowired private InventoryAuditApiManager inventoryAuditMgr;

  @Autowired private SubSampleApiPutValidator subSampleApiPutValidator;

  @Autowired private SubSampleApiPostConfigValidator subSampleApiPostConfigValidator;

  @Override
  public ApiSubSampleSearchResult getSubSamplesForUser(
      @Valid InventoryApiPaginationCriteria apiPgCrit,
      @Valid InventoryApiSearchConfig srchConfig,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {

    log.info("listing subSamples, incoming pagination is {}", apiPgCrit);
    throwBindExceptionIfErrors(errors);

    if (apiPgCrit == null) {
      apiPgCrit = new InventoryApiPaginationCriteria();
    }
    PaginationCriteria<SubSample> pgCrit =
        getPaginationCriteriaForApiSearch(apiPgCrit, SubSample.class);

    String ownedBy = null;
    InventorySearchDeletedOption deletedItemsOption = null;
    if (srchConfig != null) {
      ownedBy = srchConfig.getOwnedBy();
      deletedItemsOption = srchConfig.getDeletedItemsAsEnum();
    }

    ApiSubSampleSearchResult apiSearchResult =
        subSampleApiMgr.getSubSamplesForUser(pgCrit, ownedBy, deletedItemsOption, user);
    setLinksInInventoryRecordInfoList(apiSearchResult.getSubSamples());
    apiSearchResult.addNavigationLinks(getInventoryApiBaseURIBuilder(), apiPgCrit, srchConfig);

    return apiSearchResult;
  }

  @Override
  public List<ApiSubSample> createNewSubSamplesForSample(
      @RequestBody @Valid SubSampleApiPostConfig config,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {

    subSampleApiPostConfigValidator.validate(config, errors);
    throwBindExceptionIfErrors(errors);

    return subSampleApiMgr.createNewSubSamplesForSample(
        config.getSampleId(), config.getNumSubSamples(), config.getSingleSubSampleQuantity(), user);
  }

  @Override
  public ApiSubSample getSubSampleById(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) {

    ApiSubSample subSample = retrieveSubSampleIfExists(id, user);
    buildAndAddInventoryRecordLinks(subSample);
    // The nested parent sample's record links are built by the recursion above; its attachment and
    // barcode links are not, so add them here (the strip has already run and does not touch them).
    if (subSample.getSampleInfo() != null) {
      addFileAndBarcodeLinks(subSample.getSampleInfo());
    }
    return subSample;
  }

  private ApiSubSample retrieveSubSampleIfExists(Long id, User user) {
    boolean exists = subSampleApiMgr.exists(id);
    if (!exists) {
      throw new NotFoundException(createNotFoundMessage("Inventory record", id));
    }
    return subSampleApiMgr.getApiSubSampleById(id, user);
  }

  @Override
  public ApiSubSample updateSubSample(
      @PathVariable Long id,
      @RequestBody @Valid ApiSubSample incomingSubSample,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {
    subSampleApiPutValidator.validate(incomingSubSample, errors);
    throwBindExceptionIfErrors(errors);

    // update incoming object's id which could be omitted by client
    incomingSubSample.setIdIfNotSet(id);

    subSampleApiMgr.assertUserCanEditSubSample(id, user);
    ApiSubSample result = subSampleApiMgr.updateApiSubSample(incomingSubSample, user);
    buildAndAddInventoryRecordLinks(result);
    return result;
  }

  @Override
  public ApiSubSample deleteSubSample(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) {
    ApiSubSample result;
    SubSample subSample = subSampleApiMgr.assertUserCanDeleteSubSample(id, user);
    String sampleLock = subSample.getSample().getGlobalIdentifier().intern();
    String subSampleLock = subSample.getGlobalIdentifier().intern();
    log.info(
        "starting delete subsample "
            + id
            + " while locking with "
            + sampleLock
            + "/"
            + subSampleLock);
    synchronized (sampleLock) {
      synchronized (subSampleLock) {
        result = subSampleApiMgr.markSubSampleAsDeleted(id, user, false);
      }
    }
    return result;
  }

  @Override
  public ApiSubSample restoreDeletedSubSample(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) {
    ApiSubSample result;
    SubSample subSample = subSampleApiMgr.assertUserCanDeleteSubSample(id, user);
    String sampleLock = subSample.getSample().getGlobalIdentifier().intern();
    String subSampleLock = subSample.getGlobalIdentifier().intern();
    log.info(
        "starting restore subsample "
            + id
            + " while locking with "
            + sampleLock
            + "/"
            + subSampleLock);
    synchronized (sampleLock) {
      synchronized (subSampleLock) {
        result = subSampleApiMgr.restoreDeletedSubSample(id, user, false);
      }
    }
    return result;
  }

  @Override
  public ApiSubSample addSubSampleNote(
      @PathVariable Long id,
      @RequestBody @Valid ApiSubSampleNote subSampleNote,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {

    throwBindExceptionIfErrors(errors);

    // check user has permissions to the subsample
    subSampleApiMgr.assertUserCanEditSubSample(id, user);

    // update the subsample
    ApiSubSample updatedSubSample = subSampleApiMgr.addSubSampleNote(id, subSampleNote, user);
    buildAndAddInventoryRecordLinks(updatedSubSample);

    return updatedSubSample;
  }

  @Override
  public ResponseEntity<byte[]> getSubSampleImage(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) throws IOException {
    return doImageResponse(
        user, () -> subSampleApiMgr.assertUserCanReadSubSample(id, user).getImageFileProperty());
  }

  @Override
  public ResponseEntity<byte[]> getSubSampleThumbnail(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) throws IOException {
    return doImageResponse(
        user,
        () -> subSampleApiMgr.assertUserCanReadSubSample(id, user).getThumbnailFileProperty());
  }

  public ApiInventoryRecordInfo duplicate(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) {
    SubSampleDuplicateConfig cfg = SubSampleDuplicateConfig.simpleDuplicate(id);
    ApiSubSample copy = subSampleApiMgr.duplicate(cfg.getSubSampleId(), user);
    return copy;
  }

  @Override
  public List<ApiSubSample> split(
      @PathVariable Long id,
      @Valid @RequestBody SubSampleDuplicateConfig config,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {
    throwBindExceptionIfErrors(errors);
    config.setSubSampleId(id);
    List<ApiSubSample> copy = subSampleApiMgr.split(config, user);
    return copy;
  }

  @Override
  public ApiInventoryRecordRevisionList getSubSampleAllRevisions(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) {

    SubSample dbSubSample = subSampleApiMgr.assertUserCanReadSubSample(id, user);
    ApiInventoryRecordRevisionList revisions =
        inventoryAuditMgr.getInventoryRecordRevisions(dbSubSample);
    for (ApiInventoryRecordRevision subSampleRev : revisions.getRevisions()) {
      buildAndAddInventoryRecordLinks(subSampleRev.getRecord());
    }
    return revisions;
  }

  @Override
  public ApiSubSample getSubSampleRevision(
      @PathVariable Long id,
      @PathVariable Long revisionId,
      @RequestAttribute(name = "user") User user) {

    subSampleApiMgr.assertUserCanReadSubSample(id, user);
    ApiSubSample subSample = inventoryAuditMgr.getApiSubSampleRevision(id, revisionId);
    if (subSample == null) {
      throw new NotFoundException(createNotFoundMessage("SubSample revision", revisionId));
    }
    buildAndAddInventoryRecordLinks(subSample);
    return subSample;
  }

  @Override
  public ApiSubSample getSubSampleVersion(
      @PathVariable Long id,
      @PathVariable Long version,
      @RequestAttribute(name = "user") User user) {

    ApiSubSample subSample = subSampleApiMgr.getApiSubSampleVersion(id, version, user);
    if (subSample == null) {
      throw new NotFoundException(createNotFoundMessage("SubSample version", version));
    }
    buildAndAddInventoryRecordLinks(subSample);
    return subSample;
  }
}
