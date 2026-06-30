package com.researchspace.api.v1.controller;

import static com.researchspace.core.util.imageutils.ImageUtils.getBufferedImageFromUploadedFile;
import static org.apache.commons.io.FilenameUtils.getExtension;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.researchspace.api.v1.InstrumentTemplatesApi;
import com.researchspace.api.v1.controller.InventoryBulkOperationsApiController.InventoryBulkOperationConfig;
import com.researchspace.api.v1.model.ApiInstrumentEntityInfo;
import com.researchspace.api.v1.model.ApiInstrumentTemplate;
import com.researchspace.api.v1.model.ApiInstrumentTemplatePost;
import com.researchspace.api.v1.model.ApiInstrumentTemplateSearchResult;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationPost.BulkApiOperationType;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult.InventoryBulkOperationStatus;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.service.impl.SpringMultipartFileAdapter;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.inventory.InstrumentTemplate;
import com.researchspace.model.record.IconEntity;
import com.researchspace.service.IconImageManager;
import com.researchspace.service.inventory.impl.InventoryBulkOperationHandler;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import javax.ws.rs.NotFoundException;
import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;

@ApiController
public class InstrumentTemplatesApiController extends BaseApiInventoryController
    implements InstrumentTemplatesApi {

  private @Autowired IconImageManager iconImageManager;
  private @Autowired InventoryBulkOperationHandler bulkOperationHandler;
  private @Autowired InstrumentTemplatePostValidator postValidator;
  private @Autowired InstrumentTemplatePutValidator putValidator;

  @Override
  public ApiInstrumentTemplateSearchResult getTemplatesForUser(
      @Valid InventoryApiPaginationCriteria apiPgCrit,
      @Valid InventoryApiSearchConfig srchConfig,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {
    throwBindExceptionIfErrors(errors);

    if (apiPgCrit == null) {
      apiPgCrit = new InventoryApiPaginationCriteria();
    }
    PaginationCriteria<InstrumentTemplate> pgCrit =
        getPaginationCriteriaForApiSearch(apiPgCrit, InstrumentTemplate.class);

    String ownedBy = null;
    InventorySearchDeletedOption deletedItemsOption = null;
    if (srchConfig != null) {
      ownedBy = srchConfig.getOwnedBy();
      deletedItemsOption = srchConfig.getDeletedItemsAsEnum();
    }

    ApiInstrumentTemplateSearchResult apiSearchResult =
        instrumentApiMgr.getTemplatesForUser(pgCrit, ownedBy, deletedItemsOption, user);
    setLinksInInventoryRecordInfoList(apiSearchResult.getTemplates());
    apiSearchResult.addNavigationLinks(getInventoryApiBaseURIBuilder(), apiPgCrit, srchConfig);
    return apiSearchResult;
  }

  @Override
  public ApiInstrumentTemplate getInstrumentTemplateById(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) {
    ApiInstrumentTemplate template = retrieveApiInstrumentTemplateIfExists(id, user);
    buildAndAddInventoryRecordLinks(template);
    return template;
  }

  @Override
  public ApiInstrumentTemplate getInstrumentTemplateVersionById(
      @PathVariable Long id,
      @PathVariable Long version,
      @RequestAttribute(name = "user") User user) {
    ApiInstrumentTemplate template =
        instrumentApiMgr.getApiInstrumentTemplateVersion(id, version, user);
    if (template != null) {
      buildAndAddInventoryRecordLinks(template);
    }
    return template;
  }

  @Override
  public ApiInstrumentTemplate createNewInstrumentTemplate(
      @RequestBody @Valid ApiInstrumentTemplatePost templatePost,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {
    throwBindExceptionIfErrors(errors);
    validatePostBody(templatePost, errors);

    ApiInstrumentTemplate created = instrumentApiMgr.createInstrumentTemplate(templatePost, user);
    buildAndAddInventoryRecordLinks(created);
    return created;
  }

  private void validatePostBody(ApiInstrumentTemplatePost templatePost, BindingResult errors)
      throws BindException {
    postValidator.validate(templatePost, errors);
    if (errors.hasErrors()) {
      throw new BindException(errors);
    }
  }

  @Override
  public ApiInstrumentTemplate updateInstrumentTemplate(
      @PathVariable Long id,
      @RequestBody @Valid ApiInstrumentTemplate incomingTemplate,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {
    throwBindExceptionIfErrors(errors);
    validatePutRequest(incomingTemplate, errors);

    incomingTemplate.setIdIfNotSet(id);
    InstrumentTemplate dbTemplate = instrumentApiMgr.assertUserCanEditInstrumentTemplate(id, user);
    assertIsInstrumentTemplate(dbTemplate);

    ApiInstrumentTemplate updated =
        instrumentApiMgr.updateApiInstrumentTemplate(incomingTemplate, user);
    buildAndAddInventoryRecordLinks(updated);
    return updated;
  }

  private void validatePutRequest(ApiInstrumentTemplate templatePut, BindingResult errors)
      throws BindException {
    putValidator.validate(templatePut, errors);
    if (errors.hasErrors()) {
      throw new BindException(errors);
    }
  }

  private void assertIsInstrumentTemplate(InstrumentTemplate template) {
    Validate.isTrue(
        template.isTemplate(),
        messages.getMessage("errors.inventory.instrument.template.required"));
  }

  @Override
  public ApiInstrumentTemplate deleteInstrumentTemplate(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) {
    InstrumentTemplate dbTemplate =
        instrumentApiMgr.assertUserCanDeleteInstrumentTemplate(id, user);
    assertIsInstrumentTemplate(dbTemplate);
    return instrumentApiMgr.markInstrumentTemplateAsDeleted(id, user);
  }

  @Override
  public ApiInstrumentTemplate restoreDeletedInstrumentTemplate(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) {
    InstrumentTemplate dbTemplate =
        instrumentApiMgr.assertUserCanDeleteInstrumentTemplate(id, user);
    assertIsInstrumentTemplate(dbTemplate);
    return instrumentApiMgr.restoreDeletedInstrumentTemplate(id, user);
  }

  @Override
  public ResponseEntity<byte[]> getInstrumentTemplateImage(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) throws IOException {
    return doImageResponse(
        user,
        () ->
            instrumentApiMgr.assertUserCanReadInstrumentTemplate(id, user).getImageFileProperty());
  }

  @Override
  public ResponseEntity<byte[]> getInstrumentTemplateThumbnail(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) throws IOException {
    return doImageResponse(
        user,
        () ->
            instrumentApiMgr
                .assertUserCanReadInstrumentTemplate(id, user)
                .getThumbnailFileProperty());
  }

  @Override
  public ApiInstrumentEntityInfo setIcon(
      @PathVariable Long templateId, MultipartFile file, @RequestAttribute(name = "user") User user)
      throws BindException, IOException {

    InstrumentTemplate template =
        instrumentApiMgr.assertUserCanReadInstrumentTemplate(templateId, user);
    Optional<BufferedImage> img =
        getBufferedImageFromUploadedFile(new SpringMultipartFileAdapter(file));
    if (!img.isPresent()) {
      throw new IllegalArgumentException(
          getMessage(
              "errors.inventory.icon.image.parse.failure",
              new Object[] {file.getOriginalFilename()}));
    }
    String suffix = getExtension(file.getOriginalFilename());
    IconEntity ice = IconEntity.createIconEntityFromImage(templateId, img.get(), suffix);
    IconEntity iet = iconImageManager.saveIconEntity(ice, false);
    template = instrumentApiMgr.saveIconId(template, iet.getId());

    ApiInstrumentEntityInfo result = new ApiInstrumentEntityInfo(template);
    buildAndAddInventoryRecordLinks(result);
    return result;
  }

  @Override
  public void getIcon(
      @PathVariable Long templateId,
      @PathVariable Long iconId,
      @RequestAttribute(name = "user") User user,
      HttpServletResponse response)
      throws IOException {
    iconImageManager.getIconEntity(
        iconId, response.getOutputStream(), this::getDefaultInstrumentTemplateIcon);
  }

  private byte[] getDefaultInstrumentTemplateIcon() {
    return getIconImageBytes("instrumentTemplate.png");
  }

  @Override
  public ApiInstrumentTemplate changeInstrumentTemplateOwner(
      @PathVariable Long id,
      @RequestBody @Valid ApiInstrumentTemplate incomingTemplate,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {
    throwBindExceptionIfErrors(errors);
    incomingTemplate.setIdIfNotSet(id);
    instrumentApiMgr.assertUserCanTransferInstrumentTemplate(id, user);

    ApiInstrumentTemplate updated =
        instrumentApiMgr.changeApiInstrumentTemplateOwner(incomingTemplate, user);
    buildAndAddInventoryRecordLinks(updated);
    return updated;
  }

  @Override
  public ApiInventoryBulkOperationResult updateInstrumentsToLatestTemplateVersion(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) {
    InstrumentTemplate template = instrumentApiMgr.assertUserCanReadInstrumentTemplate(id, user);
    assertIsInstrumentTemplate(template);

    List<ApiInventoryRecordInfo> instrumentsFromNonLatestTemplate =
        instrumentApiMgr.getInstrumentsLinkingOldTemplateVersion(template.getId(), user);

    InventoryBulkOperationConfig bulkOpConfig =
        new InventoryBulkOperationConfig(
            BulkApiOperationType.UPDATE_TO_LATEST_TEMPLATE_VERSION,
            instrumentsFromNonLatestTemplate,
            false,
            user);
    ApiInventoryBulkOperationResult updateResult =
        bulkOperationHandler.runBulkOperation(bulkOpConfig);
    updateResult.setStatus(InventoryBulkOperationStatus.COMPLETED);
    return updateResult;
  }

  /**
   * Method for duplicating the instrument template. Currently not exposed in public api, called
   * only by /bulk endpoint, therefore no REST mappings.
   */
  public ApiInventoryRecordInfo duplicate(Long id, User user) {
    InstrumentTemplate dbTemplate = instrumentApiMgr.assertUserCanReadInstrumentTemplate(id, user);
    assertIsInstrumentTemplate(dbTemplate);

    ApiInstrumentTemplate copy = instrumentApiMgr.duplicateInstrumentTemplate(id, user);
    buildAndAddInventoryRecordLinks(copy);
    return copy;
  }

  private ApiInstrumentTemplate retrieveApiInstrumentTemplateIfExists(Long id, User user) {
    if (!instrumentApiMgr.instrumentTemplateExists(id)) {
      throw new NotFoundException(createNotFoundMessage("Inventory InstrumentTemplate ", id));
    }
    return instrumentApiMgr.getApiInstrumentTemplateById(id, user);
  }
}
