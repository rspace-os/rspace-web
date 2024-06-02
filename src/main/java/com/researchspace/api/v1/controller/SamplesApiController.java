package com.researchspace.api.v1.controller;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.researchspace.api.v1.SamplesApi;
import com.researchspace.api.v1.model.ApiContainerInfo;
import com.researchspace.api.v1.model.ApiInventoryRecordRevisionList;
import com.researchspace.api.v1.model.ApiInventoryRecordRevisionList.ApiInventoryRecordRevision;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleInfo;
import com.researchspace.api.v1.model.ApiSampleSearchResult;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.service.inventory.InventoryAuditApiManager;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.validation.Valid;
import javax.ws.rs.NotFoundException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@ApiController
public class SamplesApiController extends BaseApiInventoryController implements SamplesApi {

  @Autowired private InventoryAuditApiManager inventoryAuditMgr;

  @Autowired private SampleApiPostValidator sampleApiPostValidator;
  @Autowired private SampleApiPutValidator sampleApiPutValidator;
  @Autowired private SampleApiPostFullValidator sampleApiPostFullValidator;

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class ApiSampleFullPost {
    ApiSampleWithFullSubSamples apiSample;
    User user;
    // may be null
    Sample template;
  }

  // this class doesn't seem to be used at all, nor its validator
  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class ApiSampleFullPut {
    ApiSampleWithFullSubSamples apiSample;
    User user;
    Long templateId;
    // may be null
    Sample template;
    // the db sample matching the api sample
    Sample dbSample;
  }

  @Override
  public ApiSampleSearchResult getSamplesForUser(
      @Valid InventoryApiPaginationCriteria apiPgCrit,
      @Valid InventoryApiSearchConfig srchConfig,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {

    log.info("listing samples, incoming pagination is {}, search config {}", apiPgCrit, srchConfig);
    throwBindExceptionIfErrors(errors);

    if (apiPgCrit == null) {
      apiPgCrit = new InventoryApiPaginationCriteria();
    }
    PaginationCriteria<Sample> pgCrit = getPaginationCriteriaForApiSearch(apiPgCrit, Sample.class);

    String ownedBy = null;
    InventorySearchDeletedOption deletedItemsOption = null;
    if (srchConfig != null) {
      ownedBy = srchConfig.getOwnedBy();
      deletedItemsOption = srchConfig.getDeletedItemsAsEnum();
    }

    ApiSampleSearchResult apiSearchResult =
        sampleApiMgr.getSamplesForUser(pgCrit, ownedBy, deletedItemsOption, user);
    setLinksInInventoryRecordInfoList(apiSearchResult.getSamples());
    apiSearchResult.addNavigationLinks(getInventoryApiBaseURIBuilder(), apiPgCrit, srchConfig);

    return apiSearchResult;
  }

  @Override
  public ApiSampleWithFullSubSamples createNewSample(
      @RequestBody @Valid ApiSampleWithFullSubSamples apiSample,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {

    assertNotSampleTemplate(apiSample.isTemplate());
    validateCreateSampleInput(apiSample, errors, user);

    Integer subSamplesCount = apiSample.getNewSampleSubSamplesCount();
    if (subSamplesCount != null) {
      // update prototype sample with a requested number of subsamples
      List<ApiSubSample> newSubSamples =
          Stream.generate(ApiSubSample::new).limit(subSamplesCount).collect(Collectors.toList());
      if (apiSample.getQuantity() != null) {
        // split provided total quantity into subsamples
        BigDecimal newQuantityValue =
            apiSample
                .getQuantity()
                .getNumericValue()
                .divide(BigDecimal.valueOf(subSamplesCount), MathContext.DECIMAL32);
        ApiQuantityInfo subSampleQuantity =
            new ApiQuantityInfo(newQuantityValue, apiSample.getQuantity().getUnitId());
        newSubSamples.stream().forEach(ss -> ss.setQuantity(subSampleQuantity));
      }
      if (apiSample.getNewSampleSubSampleTargetLocations() != null) {
        placeNewSubSamplesInRequestedContainersAndLocations(
            newSubSamples, apiSample.getNewSampleSubSampleTargetLocations());
      }
      apiSample.setSubSamples(newSubSamples);
    }

    ApiSampleWithFullSubSamples result = sampleApiMgr.createNewApiSample(apiSample, user);
    buildAndAddInventoryRecordLinks(result);

    return result;
  }

  private void assertNotSampleTemplate(Sample dbSample) {
    assertNotSampleTemplate(dbSample.isTemplate());
  }

  private void assertNotSampleTemplate(boolean sampleTemplateFlag) {
    if (sampleTemplateFlag) {
      throw new IllegalArgumentException(
          "Please use /sampleTemplates endpoint for template actions");
    }
  }

  /* errors might already be populated with simple validation errors using javax.validation annotations
   * by Spring's automatic validation */
  public void validateCreateSampleInput(
      ApiSampleWithFullSubSamples apiSample, BindingResult errors, User user) throws BindException {

    Sample template = verifyTemplate(apiSample, errors, user);
    // we validate the posted object. We can set errors on individual fields in this validator (
    // doesn't need template)
    inputValidator.validate(apiSample, sampleApiPostValidator, errors);

    // here we validate using other information as well as what's posted. errors are 'global' errors
    ApiSampleFullPost allData = new ApiSampleFullPost(apiSample, user, template);

    inputValidator.validate(allData, sampleApiPostFullValidator, errors);
    // this will collate all errors together.
    throwBindExceptionIfErrors(errors);
  }

  private void placeNewSubSamplesInRequestedContainersAndLocations(
      List<ApiSubSample> subSamples,
      List<ApiSampleWithFullSubSamples.ApiSampleSubSampleTargetLocation> targetLocations) {

    for (int i = 0; i < subSamples.size(); i++) {
      ApiContainerInfo parentContainer = new ApiContainerInfo();
      parentContainer.setId(targetLocations.get(i).getContainerId());
      subSamples.get(i).setParentContainer(parentContainer);
      subSamples.get(i).setParentLocation(targetLocations.get(i).getContainerLocation());
    }
  }

  /* errors might already be populated with simple validation errors using javax.validation annotations by Spring's
  automatic validation */
  private void validateUpdateSampleInput(
      ApiSampleWithFullSubSamples apiSample, BindingResult errors) throws BindException {

    /* we can only validate the basic qualities of the incoming object
    because put is so flexible we can't do as much validation as for post */
    inputValidator.validate(apiSample, sampleApiPutValidator, errors);

    throwBindExceptionIfErrors(errors);
  }

  // ok for template id to be null
  private Sample verifyTemplate(ApiSampleInfo apiSample, BindingResult errors, User user)
      throws BindException {
    if (apiSample.getTemplateId() == null) {
      return null;
    }
    try {
      return sampleApiMgr.getSampleTemplateByIdWithPopulatedFields(apiSample.getTemplateId(), user);
    } catch (NotFoundException e) {
      // form ID is set, but is not a readable form.
      errors.rejectValue("templateId", "", e.getMessage());
      // if the form is invalid we can't proceed as there will be downstream exceptions thrown, so
      // fail here
      throwBindExceptionIfErrors(errors);
    }
    return null;
  }

  @Override
  public ApiSample getSampleById(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) {

    ApiSample sample = retrieveApiSampleIfExists(id, user);
    buildAndAddInventoryRecordLinks(sample);
    return sample;
  }

  private ApiSample retrieveApiSampleIfExists(Long id, User user) {
    boolean exists = sampleApiMgr.exists(id);
    if (!exists) {
      throw new NotFoundException(createNotFoundMessage("Inventory record", id));
    }
    return sampleApiMgr.getApiSampleById(id, user);
  }

  @Override
  public String validateNameForNewSample(
      @RequestParam(name = "name") String sampleName, @RequestAttribute(name = "user") User user)
      throws BindException {

    String errorMsg = "";
    if (StringUtils.isEmpty(sampleName)) {
      errorMsg = "Name cannot be empty";
    } else if (StringUtils.length(sampleName) > BaseRecord.DEFAULT_VARCHAR_LENGTH) {
      errorMsg = "Name is too long (max 255 chars)";
    }

    if (errorMsg.isEmpty()) {
      boolean exists = sampleApiMgr.nameExistsForUser(sampleName, user);
      if (exists) {
        errorMsg = "There is already a sample named [" + sampleName + "]";
      }
    }
    return String.format("{ \"valid\": %s, \"message\": \"%s\" }", errorMsg.isEmpty(), errorMsg);
  }

  @Override
  public ApiSample updateSample(
      @PathVariable Long id,
      @RequestBody @Valid ApiSampleWithFullSubSamples incomingSample,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {
    validateUpdateSampleInput(incomingSample, errors);
    // update incoming object's id which could be omitted
    incomingSample.setIdIfNotSet(id);
    Sample dbSample = sampleApiMgr.assertUserCanEditSample(id, user);
    assertNotSampleTemplate(dbSample);

    // update the sample
    ApiSample updatedSample = sampleApiMgr.updateApiSample(incomingSample, user);
    buildAndAddInventoryRecordLinks(updatedSample);

    return updatedSample;
  }

  @Override
  public ApiSample deleteSample(
      @PathVariable Long id,
      @RequestParam(name = "forceDelete", required = false) boolean forceDelete,
      @RequestAttribute(name = "user") User user) {

    Sample dbSample = sampleApiMgr.assertUserCanDeleteSample(id, user);
    assertNotSampleTemplate(dbSample);

    ApiSample result;
    String sampleLock = dbSample.getGlobalIdentifier().intern();
    log.info("starting delete sample " + id + " while locking with " + sampleLock);
    synchronized (sampleLock) {
      result = sampleApiMgr.markSampleAsDeleted(id, forceDelete, user);
    }
    return result;
  }

  @Override
  public ApiSample restoreDeletedSample(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) {
    Sample dbSample = sampleApiMgr.assertUserCanDeleteSample(id, user);
    assertNotSampleTemplate(dbSample);
    return sampleApiMgr.restoreDeletedSample(id, user, true);
  }

  @Override
  public ResponseEntity<byte[]> getSampleImage(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) throws IOException {
    return doImageResponse(
        user, () -> sampleApiMgr.assertUserCanReadSample(id, user).getImageFileProperty());
  }

  @Override
  public ResponseEntity<byte[]> getSampleThumbnail(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) throws IOException {
    return doImageResponse(
        user, () -> sampleApiMgr.assertUserCanReadSample(id, user).getThumbnailFileProperty());
  }

  @Override
  public ApiSample changeSampleOwner(
      @PathVariable Long id,
      @RequestBody @Valid ApiSampleWithFullSubSamples incomingSample,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {

    // update incoming object's id which could be omitted
    incomingSample.setIdIfNotSet(id);
    sampleApiMgr.assertUserCanTransferSample(id, user);

    // update sample's owner
    ApiSample updatedSample = sampleApiMgr.changeApiSampleOwner(incomingSample, user);
    buildAndAddInventoryRecordLinks(updatedSample);

    return updatedSample;
  }

  @Override
  public ApiSampleWithFullSubSamples duplicate(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) {
    Sample dbSample = sampleApiMgr.assertUserCanReadSample(id, user);
    assertNotSampleTemplate(dbSample);

    ApiSampleWithFullSubSamples copy = sampleApiMgr.duplicate(id, user);
    buildAndAddInventoryRecordLinks(copy);
    return copy;
  }

  @Override
  public ApiSample updateToLatestTemplateVersion(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) {
    ApiSample updatedSample = sampleApiMgr.updateSampleToLatestTemplateVersion(id, user);
    buildAndAddInventoryRecordLinks(updatedSample);
    return updatedSample;
  }

  @Override
  public ApiInventoryRecordRevisionList getSampleAllRevisions(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) {

    Sample dbSample = sampleApiMgr.assertUserCanReadSample(id, user);
    ApiInventoryRecordRevisionList revisions =
        inventoryAuditMgr.getInventoryRecordRevisions(dbSample);
    for (ApiInventoryRecordRevision sampleRev : revisions.getRevisions()) {
      buildAndAddInventoryRecordLinks(sampleRev.getRecord());
    }
    return revisions;
  }

  @Override
  public ApiSample getSampleRevision(
      @PathVariable Long id,
      @PathVariable Long revisionId,
      @RequestAttribute(name = "user") User user) {

    sampleApiMgr.assertUserCanReadSample(id, user);
    ApiSample sample = inventoryAuditMgr.getApiSampleRevision(id, revisionId);
    if (sample != null) {
      buildAndAddInventoryRecordLinks(sample);
    }
    return sample;
  }
}
