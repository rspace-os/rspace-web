package com.researchspace.api.v1.controller;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.researchspace.api.v1.InstrumentsApi;
import com.researchspace.api.v1.model.ApiContainerInfo;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInstrumentSearchResult;
import com.researchspace.api.v1.model.ApiInventoryRecordRevisionList;
import com.researchspace.api.v1.model.ApiInventoryRecordRevisionList.ApiInventoryRecordRevision;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InstrumentTemplate;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.service.inventory.InventoryAuditApiManager;
import java.io.IOException;
import javax.validation.Valid;
import javax.ws.rs.NotFoundException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@ApiController
public class InstrumentsApiController extends BaseApiInventoryController implements InstrumentsApi {

  @Autowired private InstrumentApiPostValidator instrumentApiPostValidator;
  @Autowired private InstrumentApiPostFullValidator instrumentApiPostFullValidator;
  @Autowired private InstrumentApiPutValidator instrumentApiPutValidator;
  @Autowired private InventoryAuditApiManager inventoryAuditMgr;

  @Value("${inventory.instrument.enabled:false}")
  private boolean inventoryInstrumentEnabled;

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class ApiInstrumentFullPost {

    ApiInstrument apiInstrument;
    User user;
    // may be null
    InstrumentTemplate template;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class ApiInstrumentFullPut {

    ApiInstrument apiInstrument;
    User user;
    // may be null
    InstrumentTemplate template;
    // the db instrument matching the api instrument
    Instrument dbInstrument;
  }

  @Override
  public ApiInstrumentSearchResult getInstrumentsForUser(
      @Valid InventoryApiPaginationCriteria apiPgCrit,
      @Valid InventoryApiSearchConfig srchConfig,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {
    assertIsInventoryInstrumentEnabled();
    throwBindExceptionIfErrors(errors);

    if (apiPgCrit == null) {
      apiPgCrit = new InventoryApiPaginationCriteria();
    }
    PaginationCriteria<Instrument> pgCrit =
        getPaginationCriteriaForApiSearch(apiPgCrit, Instrument.class);

    String ownedBy = null;
    InventorySearchDeletedOption deletedItemsOption = null;
    if (srchConfig != null) {
      ownedBy = srchConfig.getOwnedBy();
      deletedItemsOption = srchConfig.getDeletedItemsAsEnum();
    }

    ApiInstrumentSearchResult apiSearchResult =
        instrumentApiMgr.getInstrumentsForUser(pgCrit, ownedBy, deletedItemsOption, user);
    setLinksInInventoryRecordInfoList(apiSearchResult.getInstruments());
    apiSearchResult.addNavigationLinks(getInventoryApiBaseURIBuilder(), apiPgCrit, srchConfig);

    return apiSearchResult;
  }

  @Override
  public ApiInstrument createNewInstrument(
      @RequestBody @Valid ApiInstrument apiInstrument,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {
    assertIsInventoryInstrumentEnabled();
    validateCreateInstrumentInput(apiInstrument, errors, user);

    if (apiInstrument.getNewTargetLocation() != null) {
      ApiContainerInfo parentContainer = new ApiContainerInfo();
      parentContainer.setId(apiInstrument.getNewTargetLocation().getContainerId());
      apiInstrument.setParentContainer(parentContainer);
      apiInstrument.setParentLocation(apiInstrument.getNewTargetLocation().getContainerLocation());
    }

    ApiInstrument result = instrumentApiMgr.createNewApiInstrument(apiInstrument, user);
    buildAndAddInventoryRecordLinks(result);

    return result;
  }

  @Override
  public ApiInstrument getInstrumentById(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) {
    assertIsInventoryInstrumentEnabled();

    ApiInstrument instrument = retrieveApiInstrumentIfExists(id, user);
    buildAndAddInventoryRecordLinks(instrument);
    return instrument;
  }

  @Override
  public String validateNameForNewInstrument(
      @RequestParam(name = "name") String name, @RequestAttribute(name = "user") User user)
      throws BindException {
    assertIsInventoryInstrumentEnabled();

    String errorMsg = "";
    if (StringUtils.isEmpty(name)) {
      errorMsg = "Name cannot be empty";
    } else if (StringUtils.length(name) > BaseRecord.DEFAULT_VARCHAR_LENGTH) {
      errorMsg = "Name is too long (max 255 chars)";
    }

    if (errorMsg.isEmpty() && instrumentApiMgr.nameExistsForUser(name, user)) {
      errorMsg = "There is already an instrument named [" + name + "]";
    }
    return String.format(
        "{ \"valid\": %s, \"message\": \"%s\" }",
        errorMsg.isEmpty(), StringEscapeUtils.escapeJson(errorMsg));
  }

  @Override
  public ApiInstrument updateInstrument(
      @PathVariable Long id,
      @RequestBody @Valid ApiInstrument incomingInstrument,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {
    assertIsInventoryInstrumentEnabled();
    validateUpdateInstrumentInput(incomingInstrument, errors);
    incomingInstrument.setIdIfNotSet(id);
    instrumentApiMgr.assertUserCanEditInstrument(id, user);

    ApiInstrument updated = instrumentApiMgr.updateApiInstrument(incomingInstrument, user);
    buildAndAddInventoryRecordLinks(updated);
    return updated;
  }

  @Override
  public ApiInstrument deleteInstrument(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) {
    assertIsInventoryInstrumentEnabled();
    instrumentApiMgr.assertUserCanDeleteInstrument(id, user);

    return instrumentApiMgr.markInstrumentAsDeleted(id, user);
  }

  @Override
  public ApiInstrument restoreDeletedInstrument(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) {
    assertIsInventoryInstrumentEnabled();
    instrumentApiMgr.assertUserCanDeleteInstrument(id, user);
    return instrumentApiMgr.restoreDeletedInstrument(id, user);
  }

  @Override
  public ResponseEntity<byte[]> getInstrumentImage(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) throws IOException {
    assertIsInventoryInstrumentEnabled();
    return doImageResponse(
        user, () -> instrumentApiMgr.assertUserCanReadInstrument(id, user).getImageFileProperty());
  }

  @Override
  public ResponseEntity<byte[]> getInstrumentThumbnail(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) throws IOException {
    assertIsInventoryInstrumentEnabled();
    return doImageResponse(
        user,
        () -> instrumentApiMgr.assertUserCanReadInstrument(id, user).getThumbnailFileProperty());
  }

  @Override
  public ApiInstrument changeInstrumentOwner(
      @PathVariable Long id,
      @RequestBody @Valid ApiInstrument incomingInstrument,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {
    assertIsInventoryInstrumentEnabled();
    throwBindExceptionIfErrors(errors);
    incomingInstrument.setIdIfNotSet(id);
    instrumentApiMgr.assertUserCanTransferInstrument(id, user);

    ApiInstrument updated = instrumentApiMgr.changeApiInstrumentOwner(incomingInstrument, user);
    buildAndAddInventoryRecordLinks(updated);
    return updated;
  }

  @Override
  public ApiInstrument duplicateInstrument(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) {
    assertIsInventoryInstrumentEnabled();
    instrumentApiMgr.assertUserCanReadInstrument(id, user);

    ApiInstrument copy = instrumentApiMgr.duplicateInstrument(id, user);
    buildAndAddInventoryRecordLinks(copy);
    return copy;
  }

  @Override
  public ApiInventoryRecordRevisionList getInstrumentAllRevisions(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) {
    assertIsInventoryInstrumentEnabled();

    Instrument dbInstrument = instrumentApiMgr.assertUserCanReadInstrument(id, user);
    ApiInventoryRecordRevisionList revisions =
        inventoryAuditMgr.getInventoryRecordRevisions(dbInstrument);
    for (ApiInventoryRecordRevision rev : revisions.getRevisions()) {
      buildAndAddInventoryRecordLinks(rev.getRecord());
    }
    return revisions;
  }

  @Override
  public ApiInstrument getInstrumentRevision(
      @PathVariable Long id,
      @PathVariable Long revisionId,
      @RequestAttribute(name = "user") User user) {
    assertIsInventoryInstrumentEnabled();

    instrumentApiMgr.assertUserCanReadInstrument(id, user);
    ApiInstrument instrument = inventoryAuditMgr.getApiInstrumentRevision(id, revisionId);
    if (instrument != null) {
      buildAndAddInventoryRecordLinks(instrument);
    }
    return instrument;
  }

  private void assertIsInventoryInstrumentEnabled() {
    if (!inventoryInstrumentEnabled) {
      throw new UnsupportedOperationException(
          "The inventory Instrument is not enabled in this RSpace instance");
    }
  }

  /* errors might already be populated with simple validation errors using javax.validation annotations
   * by Spring's automatic validation */
  public void validateCreateInstrumentInput(
      ApiInstrument apiInstrument, BindingResult errors, User user) throws BindException {

    // TODO[nik]: implement this on RSDEV-1059, until that, it will be always null
    // reinstate this: InstrumentTemplate instrumentTemplate =
    //                                            verifyTemplate(apiInstrument, errors, user);
    InstrumentTemplate instrumentTemplate = null;

    // we validate the posted object. We can set errors on individual fields in this validator (
    // doesn't need template)
    inputValidator.validate(apiInstrument, instrumentApiPostValidator, errors);

    // here we validate using other information as well as what's posted. errors are 'global' errors
    ApiInstrumentFullPost allData =
        new ApiInstrumentFullPost(apiInstrument, user, instrumentTemplate);

    inputValidator.validate(allData, instrumentApiPostFullValidator, errors);
    // this will collate all errors together.
    throwBindExceptionIfErrors(errors);
  }

  /* errors might already be populated with simple validation errors using javax.validation annotations
   * by Spring's automatic validation */
  private void validateUpdateInstrumentInput(ApiInstrument apiInstrument, BindingResult errors)
      throws BindException {
    /* we can only validate the basic qualities of the incoming object
    because put is so flexible we can't do as much validation as for post */
    inputValidator.validate(apiInstrument, instrumentApiPutValidator, errors);
    throwBindExceptionIfErrors(errors);
  }

  private ApiInstrument retrieveApiInstrumentIfExists(Long id, User user) {
    boolean exists = instrumentApiMgr.instrumentExists(id);
    if (!exists) {
      throw new NotFoundException(createNotFoundMessage("Inventory Instrument ", id));
    }
    return instrumentApiMgr.getApiInstrumentById(id, user);
  }
}
