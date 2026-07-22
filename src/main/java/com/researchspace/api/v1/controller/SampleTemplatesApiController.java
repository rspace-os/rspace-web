package com.researchspace.api.v1.controller;

import static com.researchspace.core.util.imageutils.ImageUtils.getBufferedImageFromUploadedFile;
import static org.apache.commons.io.FilenameUtils.getExtension;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.researchspace.api.v1.SampleTemplatesApi;
import com.researchspace.api.v1.controller.InventoryBulkOperationsApiController.InventoryBulkOperationConfig;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationPost.BulkApiOperationType;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult.InventoryBulkOperationStatus;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventoryRecordRevisionList;
import com.researchspace.api.v1.model.ApiSampleInfo;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSampleTemplatePost;
import com.researchspace.api.v1.model.ApiSampleTemplateSearchResult;
import com.researchspace.api.v1.service.impl.SpringMultipartFileAdapter;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.inventory.SampleEntity;
import com.researchspace.model.inventory.SampleTemplate;
import com.researchspace.model.record.IconEntity;
import com.researchspace.service.IconImageManager;
import com.researchspace.service.inventory.InventoryAuditApiManager;
import com.researchspace.service.inventory.impl.InventoryBulkOperationHandler;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import javax.ws.rs.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;

@ApiController
public class SampleTemplatesApiController extends BaseApiInventoryController
    implements SampleTemplatesApi {

  private @Autowired IconImageManager iconImageManager;
  private @Autowired InventoryBulkOperationHandler bulkOperationHandler;
  private @Autowired InventoryAuditApiManager inventoryAuditMgr;

  private @Autowired SampleTemplatePostValidator postValidator;
  private @Autowired SampleTemplatePutValidator putValidator;

  @Override
  public ApiSampleTemplateSearchResult getTemplatesForUser(
      @Valid InventoryApiPaginationCriteria apiPgCrit,
      @Valid InventoryApiSearchConfig srchConfig,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {

    log.info("listing sample templates, incoming pagination is {}", apiPgCrit);
    throwBindExceptionIfErrors(errors);

    if (apiPgCrit == null) {
      apiPgCrit = new InventoryApiPaginationCriteria();
    }
    PaginationCriteria<SampleTemplate> pgCrit =
        getPaginationCriteriaForApiSearch(apiPgCrit, SampleTemplate.class);

    String ownedBy = null;
    InventorySearchDeletedOption deletedItemsOption = null;
    if (srchConfig != null) {
      ownedBy = srchConfig.getOwnedBy();
      deletedItemsOption = srchConfig.getDeletedItemsAsEnum();
    }

    ApiSampleTemplateSearchResult apiSearchResult =
        sampleApiMgr.getTemplatesForUser(pgCrit, ownedBy, deletedItemsOption, user);
    setLinksInInventoryRecordInfoList(apiSearchResult.getTemplates());
    apiSearchResult.addNavigationLinks(getInventoryApiBaseURIBuilder(), apiPgCrit, srchConfig);
    return apiSearchResult;
  }

  @Override
  public ApiSampleTemplate getSampleTemplateById(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) {
    ApiSampleTemplate template = sampleApiMgr.getApiSampleTemplateById(id, user);
    buildAndAddInventoryRecordLinks(template);
    return template;
  }

  @Override
  public ApiSampleTemplate getSampleTemplateVersionById(
      @PathVariable Long id,
      @PathVariable Long version,
      @RequestAttribute(name = "user") User user) {
    ApiSampleTemplate template = sampleApiMgr.getApiSampleTemplateVersion(id, version, user);
    if (template == null) {
      throw new NotFoundException(createNotFoundMessage("Sample template version", version));
    }
    buildAndAddInventoryRecordLinks(template);
    return template;
  }

  @Override
  public ApiInventoryRecordRevisionList getSampleTemplateAllRevisions(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) {
    SampleEntity dbTemplate = sampleApiMgr.assertUserCanReadSampleTemplate(id, user);
    ApiInventoryRecordRevisionList revisions =
        inventoryAuditMgr.getInventoryRecordRevisions(dbTemplate);
    for (ApiInventoryRecordRevisionList.ApiInventoryRecordRevision templateRev :
        revisions.getRevisions()) {
      buildAndAddInventoryRecordLinks(templateRev.getRecord());
    }
    return revisions;
  }

  @Override
  public ApiSampleTemplate createNewSampleTemplate(
      @RequestBody @Valid ApiSampleTemplatePost templatePost,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {
    throwBindExceptionIfErrors(errors);
    validatePostBody(templatePost, errors);

    ApiSampleTemplate created = sampleApiMgr.createSampleTemplate(templatePost, user);
    buildAndAddInventoryRecordLinks(created);
    return created;
  }

  private void validatePostBody(ApiSampleTemplatePost templatePost, BindingResult errors)
      throws BindException {
    postValidator.validate(templatePost, errors);
    if (errors.hasErrors()) {
      throw new BindException(errors);
    }
  }

  @Override
  public ApiSampleTemplate updateSampleTemplate(
      @PathVariable Long id,
      @RequestBody @Valid ApiSampleTemplate incomingTemplate,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {

    throwBindExceptionIfErrors(errors);
    validatePutRequest(incomingTemplate, errors);

    // update incoming object's id which could be omitted
    incomingTemplate.setIdIfNotSet(id);
    sampleApiMgr.assertUserCanEditSampleTemplate(id, user);

    // update the template
    ApiSampleTemplate updatedTemplate =
        sampleApiMgr.updateApiSampleTemplate(incomingTemplate, user);
    buildAndAddInventoryRecordLinks(updatedTemplate);

    return updatedTemplate;
  }

  private void validatePutRequest(ApiSampleTemplate templatePut, BindingResult errors)
      throws BindException {
    putValidator.validate(templatePut, errors);
    if (errors.hasErrors()) {
      throw new BindException(errors);
    }
  }

  @Override
  public ApiSampleTemplate deleteSampleTemplate(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) {

    sampleApiMgr.assertUserCanDeleteSampleTemplate(id, user);
    return (ApiSampleTemplate) sampleApiMgr.markSampleAsDeleted(id, false, user);
  }

  @Override
  public ApiSampleTemplate restoreDeletedSampleTemplate(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) {

    sampleApiMgr.assertUserCanDeleteSampleTemplate(id, user);
    return (ApiSampleTemplate) sampleApiMgr.restoreDeletedSample(id, user, true);
  }

  @Override
  public ResponseEntity<byte[]> getSampleTemplateImage(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) throws IOException {
    return doImageResponse(
        user, () -> sampleApiMgr.assertUserCanReadSampleTemplate(id, user).getImageFileProperty());
  }

  @Override
  public ResponseEntity<byte[]> getSampleTemplateThumbnail(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) throws IOException {
    return doImageResponse(
        user,
        () -> sampleApiMgr.assertUserCanReadSampleTemplate(id, user).getThumbnailFileProperty());
  }

  @Override
  public ApiSampleInfo setIcon(
      @PathVariable Long templateId, MultipartFile file, @RequestAttribute(name = "user") User user)
      throws BindException, IOException {

    SampleEntity template = sampleApiMgr.assertUserCanReadSampleTemplate(templateId, user);
    Optional<BufferedImage> img =
        getBufferedImageFromUploadedFile(new SpringMultipartFileAdapter(file));
    if (!img.isPresent()) {
      throw new IllegalArgumentException(
          getMessage(
              "errors.inventory.icon.imageParseFailure",
              new Object[] {file.getOriginalFilename()}));
    }
    String suffix = getExtension(file.getOriginalFilename());
    IconEntity ice = IconEntity.createIconEntityFromImage(templateId, img.get(), suffix);
    IconEntity iet = iconImageManager.saveIconEntity(ice, false);
    template.setIconId(iet.getId());
    template = sampleApiMgr.saveIconId(template, iet.getId());
    ApiSampleInfo result = convertToApiSampleTemplateInfo(user, template);
    buildAndAddInventoryRecordLinks(result);
    return result;
  }

  // Intentionally returns the ApiSampleInfo shape (not ApiSampleTemplateInfo): this endpoint's
  // JSON payload has always been the info shape, and changing the class changes the payload.
  private ApiSampleInfo convertToApiSampleTemplateInfo(User user, SampleEntity newTemplate) {
    ApiSampleInfo rc = new ApiSampleInfo(newTemplate);
    return rc;
  }

  @Override
  public void getIcon(
      @PathVariable Long templateId,
      @PathVariable Long iconId,
      @RequestAttribute(name = "user") User user,
      HttpServletResponse response)
      throws IOException {
    iconImageManager.getIconEntity(
        iconId, response.getOutputStream(), this::getDefaultSampleTemplateIcon);
  }

  private byte[] getDefaultSampleTemplateIcon() {
    return getIconImageBytes("sampleTemplate.png");
  }

  @Override
  public ApiSampleTemplate changeSampleTemplateOwner(
      @PathVariable Long id,
      @RequestBody @Valid ApiSampleTemplate incomingTemplate,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {

    // update incoming object's id which could be omitted
    incomingTemplate.setIdIfNotSet(id);
    sampleApiMgr.assertUserCanEditSampleTemplate(id, user);

    // updated sample
    ApiSampleTemplate updatedTemplate =
        (ApiSampleTemplate) sampleApiMgr.changeApiSampleOwner(incomingTemplate, user);
    buildAndAddInventoryRecordLinks(updatedTemplate);

    return updatedTemplate;
  }

  @Override
  public ApiInventoryBulkOperationResult updateSamplesToLatestTemplateVersion(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) {

    // find the template
    SampleEntity template = sampleApiMgr.assertUserCanReadSampleTemplate(id, user);

    // find connected user's samples taken from non-latest template
    List<ApiInventoryRecordInfo> samplesFromNonLatestTemplate =
        sampleApiMgr.getSamplesLinkingOldTemplateVersion(template.getId(), user);

    // update samples with a bulk action
    InventoryBulkOperationConfig bulkOpConfig =
        new InventoryBulkOperationConfig(
            BulkApiOperationType.UPDATE_TO_LATEST_TEMPLATE_VERSION,
            samplesFromNonLatestTemplate,
            false,
            user);
    ApiInventoryBulkOperationResult updateResult =
        bulkOperationHandler.runBulkOperation(bulkOpConfig);
    updateResult.setStatus(InventoryBulkOperationStatus.COMPLETED);

    return updateResult;
  }

  /**
   * Method for duplicating the sample template.
   *
   * <p>Currently not exposed in public api, called only by /bulk endpoint, therefore no REST
   * mappings.
   */
  public ApiInventoryRecordInfo duplicate(Long id, User user) {
    /* there are default templates for which user only has read permissions,
     * but should still be be able to copy and reuse */
    sampleApiMgr.assertUserCanReadSampleTemplate(id, user);

    ApiSampleTemplate copy = sampleApiMgr.duplicateTemplate(id, user);
    buildAndAddInventoryRecordLinks(copy);
    return copy;
  }
}
