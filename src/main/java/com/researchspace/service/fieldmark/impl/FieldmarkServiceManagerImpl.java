package com.researchspace.service.fieldmark.impl;

import static com.researchspace.service.fieldmark.impl.FieldmarkToRSpaceApiConverter.createContainerRequest;
import static com.researchspace.service.fieldmark.impl.FieldmarkToRSpaceApiConverter.createSampleRequest;
import static com.researchspace.service.fieldmark.impl.FieldmarkToRSpaceApiConverter.createSampleTemplateRequest;

import com.researchspace.api.v1.controller.ContainerApiPostValidator;
import com.researchspace.api.v1.controller.InventoryFilePostValidator;
import com.researchspace.api.v1.controller.InventoryFilesApiController.ApiInventoryFilePost;
import com.researchspace.api.v1.controller.SampleApiPostFullValidator;
import com.researchspace.api.v1.controller.SampleApiPostValidator;
import com.researchspace.api.v1.controller.SampleTemplatePostValidator;
import com.researchspace.api.v1.controller.SamplesApiController.ApiSampleFullPost;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiContainerInfo;
import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSampleField;
import com.researchspace.api.v1.model.ApiSampleInfo;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSampleTemplatePost;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.fieldmark.model.FieldmarkMultipartFile;
import com.researchspace.fieldmark.model.FieldmarkNotebook;
import com.researchspace.fieldmark.model.exception.FieldmarkImportException;
import com.researchspace.fieldmark.model.utils.FieldmarkDoiIdentifierExtractor;
import com.researchspace.fieldmark.model.utils.FieldmarkFileExtractor;
import com.researchspace.fieldmark.model.utils.FieldmarkTypeExtractor;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.dtos.IControllerInputValidator;
import com.researchspace.model.dtos.fieldmark.FieldmarkNotebookDTO;
import com.researchspace.model.dtos.fieldmark.FieldmarkRecordDTO;
import com.researchspace.model.inventory.Sample;
import com.researchspace.service.ApiAvailabilityHandler;
import com.researchspace.service.fieldmark.FieldmarkServiceClientAdapter;
import com.researchspace.service.fieldmark.FieldmarkServiceManager;
import com.researchspace.service.inventory.ContainerApiManager;
import com.researchspace.service.inventory.InventoryFileApiManager;
import com.researchspace.service.inventory.InventoryIdentifierApiManager;
import com.researchspace.service.inventory.SampleApiManager;
import com.researchspace.webapp.integrations.fieldmark.FieldmarkApiImportRequest;
import com.researchspace.webapp.integrations.fieldmark.FieldmarkApiImportResult;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.naming.InvalidNameException;
import javax.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class FieldmarkServiceManagerImpl implements FieldmarkServiceManager {

  private @Autowired FieldmarkServiceClientAdapter fieldmarkServiceClientAdapter;

  private @Autowired ApiAvailabilityHandler apiHandler;

  private @Autowired SampleTemplatePostValidator sampleTemplatePostValidator;
  private @Autowired ContainerApiPostValidator apiContainerPostValidator;
  private @Autowired IControllerInputValidator inputValidator;
  private @Autowired SampleApiPostValidator sampleApiPostValidator;
  private @Autowired SampleApiPostFullValidator sampleApiPostFullValidator;
  private @Autowired InventoryFilePostValidator invFilePostValidator;

  private @Autowired InventoryIdentifierApiManager inventoryIdentifierApiManager;
  private @Autowired InventoryFileApiManager inventoryFileManager;
  private @Autowired ContainerApiManager containerApiMgr;
  private @Autowired SampleApiManager sampleApiMgr;

  @Override
  public List<FieldmarkNotebook> getFieldmarkNotebookList(User user) {
    try {
      return fieldmarkServiceClientAdapter.getFieldmarkNotebookList(user);
    } catch (Exception ex) {
      throw new FieldmarkImportException(ex);
    }
  }

  @Override
  public List<String> getIgsnCandidateFields(User user, String notebookId) {
    try {
      return fieldmarkServiceClientAdapter.getIgsnCandidateFields(user, notebookId);
    } catch (Exception ex) {
      throw new FieldmarkImportException(ex);
    }
  }

  @Override
  public FieldmarkApiImportResult importNotebook(
      FieldmarkApiImportRequest importRequest, User user) {
    FieldmarkApiImportResult importResult = null;
    try {
      FieldmarkNotebookDTO notebookDTO = getFieldmarkNotebookDTO(importRequest, user);

      // create sample template
      ApiSampleTemplatePost sampleTemplatePost = createSampleTemplateRequest(notebookDTO);
      BindingResult bindingResult =
          new BeanPropertyBindingResult(sampleTemplatePost, "templatePost");
      validatePostBody(sampleTemplatePost, bindingResult);
      ApiSampleTemplate createdSampleTemplate =
          sampleApiMgr.createSampleTemplate(sampleTemplatePost, user);

      // create container
      ApiContainer containerPost = createContainerRequest(notebookDTO, user);
      bindingResult = new BeanPropertyBindingResult(containerPost, "containerPost");
      validateCreateContainerInput(containerPost, bindingResult);
      ApiContainer createdContainer = containerApiMgr.createNewApiContainer(containerPost, user);

      // create samples and associate identifiers (if it is the case)
      importResult = new FieldmarkApiImportResult(createdContainer, createdSampleTemplate);
      for (FieldmarkRecordDTO currentRecordDTO : notebookDTO.getRecords().values()) {

        ApiSampleWithFullSubSamples samplePost =
            createSampleRequest(currentRecordDTO, createdSampleTemplate, createdContainer.getId());

        bindingResult = new BeanPropertyBindingResult(samplePost, "apiSample");
        validateCreateSampleInput(samplePost, bindingResult, user);
        associateSubSamplesAndContainer(samplePost);
        ApiSampleWithFullSubSamples createdSample =
            sampleApiMgr.createNewApiSample(samplePost, user);
        importResult.addSample(createdSample);

        if (StringUtils.isNotBlank(notebookDTO.getDoiIdentifierFieldName())) {
          apiHandler.assertInventoryAndDataciteEnabled(user);
          assignIdentifierToSample(user, currentRecordDTO, createdSample);
        }
        // for each ATTACHMENT field get "globalID" and "content" (having file identifier) and
        // then upload the right file
        for (ApiSampleField currentField : createdSample.getFields()) {
          if (ApiFieldType.ATTACHMENT.equals(currentField.getType())) {
            String sampleFieldGlobalId = currentField.getGlobalId();

            FieldmarkFileExtractor fileExtractor =
                (FieldmarkFileExtractor) currentRecordDTO.getField(currentField.getName());

            if (StringUtils.isNotBlank(fileExtractor.getFileName())) {
              uploadAttachmentToSample(user, sampleFieldGlobalId, fileExtractor, createdSample);
            } else {
              log.warn(
                  "The Fieldmark attachment has not been uploaded since the filename is empty");
            }
          }
        }
      }
    } catch (FieldmarkImportException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new FieldmarkImportException(ex);
    }
    return importResult;
  }

  private static void associateSubSamplesAndContainer(ApiSampleWithFullSubSamples samplePost) {
    List<ApiSubSample> newSubSamples =
        Stream.generate(ApiSubSample::new)
            .limit(samplePost.getNewSampleSubSamplesCount())
            .collect(Collectors.toList());
    BigDecimal newQuantityValue =
        samplePost
            .getQuantity()
            .getNumericValue()
            .divide(
                BigDecimal.valueOf(samplePost.getNewSampleSubSamplesCount()),
                MathContext.DECIMAL32);
    ApiQuantityInfo subSampleQuantity =
        new ApiQuantityInfo(newQuantityValue, samplePost.getQuantity().getUnitId());
    newSubSamples.stream().forEach(ss -> ss.setQuantity(subSampleQuantity));
    for (int i = 0; i < newSubSamples.size(); i++) {
      ApiContainerInfo parentContainer = new ApiContainerInfo();
      parentContainer.setId(
          samplePost.getNewSampleSubSampleTargetLocations().get(i).getContainerId());
      newSubSamples.get(i).setParentContainer(parentContainer);
      newSubSamples
          .get(i)
          .setParentLocation(
              samplePost.getNewSampleSubSampleTargetLocations().get(i).getContainerLocation());
    }
    samplePost.setSubSamples(newSubSamples);
  }

  private void uploadAttachmentToSample(
      User user,
      String globalId,
      FieldmarkFileExtractor fileExtractor,
      ApiSampleWithFullSubSamples samplePost)
      throws BindException, FieldmarkImportException {
    BindingResult bindingResult;
    ApiInventoryFilePost uploadPost =
        new ApiInventoryFilePost(globalId, fileExtractor.getFileName());

    bindingResult = new BeanPropertyBindingResult(samplePost, "fileSettings");
    inputValidator.validate(uploadPost, invFilePostValidator, bindingResult);
    throwBindExceptionIfErrors(bindingResult);
    String fileName = uploadPost.getFileName();
    GlobalIdentifier parentFieldGlobalId = new GlobalIdentifier(uploadPost.getParentGlobalId());
    sampleApiMgr.assertUserCanEditSampleField(parentFieldGlobalId.getDbId(), user);

    MultipartFile file = new FieldmarkMultipartFile(fileExtractor.getFieldValue(), fileName);
    try (InputStream is = file.getInputStream()) {
      inventoryFileManager.attachNewInventoryFileToInventoryRecord(
          parentFieldGlobalId, fileName, is, user);
    } catch (IOException ioe) {
      throw new FieldmarkImportException("Impossible to upload attachment to RSpace: " + ioe);
    }
  }

  private void assignIdentifierToSample(
      User user, FieldmarkRecordDTO currentRecordDTO, ApiSampleWithFullSubSamples samplePost)
      throws InvalidNameException {
    FieldmarkDoiIdentifierExtractor doiExtractor =
        (FieldmarkDoiIdentifierExtractor)
            currentRecordDTO.getFields().values().stream()
                .filter(FieldmarkTypeExtractor::isDoiIdentifier)
                .findFirst()
                .get();
    String identifierValue = doiExtractor.getFieldValue().getDoiIdentifier();
    if (StringUtils.isNotBlank(identifierValue)) {
      List<ApiInventoryDOI> identifierList =
          inventoryIdentifierApiManager.findIdentifiers(
              "draft", false, identifierValue, false, user);
      if (identifierList.isEmpty()) {
        throw new IllegalArgumentException(
            "Unable to find an existing assignable identifier: " + identifierValue);
      } else if (identifierList.size() > 1) {
        throw new IllegalArgumentException(
            "Found more than one identifier to assign for the value: " + identifierValue);
      } else {
        inventoryIdentifierApiManager.assignIdentifier(
            samplePost.getOid(), identifierList.get(0).getId(), user);
      }
    }
  }

  public void validateCreateSampleInput(
      ApiSampleWithFullSubSamples apiSample, BindingResult errors, User user) throws BindException {

    Sample template = verifyTemplateIsCompatible(apiSample, errors, user);
    inputValidator.validate(apiSample, sampleApiPostValidator, errors);
    ApiSampleFullPost allData = new ApiSampleFullPost(apiSample, user, template);
    inputValidator.validate(allData, sampleApiPostFullValidator, errors);
    throwBindExceptionIfErrors(errors);
  }

  private Sample verifyTemplateIsCompatible(
      ApiSampleInfo apiSample, BindingResult errors, User user) throws BindException {
    if (apiSample.getTemplateId() != null) {
      try {
        return sampleApiMgr.getSampleTemplateByIdWithPopulatedFields(
            apiSample.getTemplateId(), user);
      } catch (NotFoundException e) {
        errors.rejectValue("templateId", "", e.getMessage());
        throwBindExceptionIfErrors(errors);
      }
    }
    return null;
  }

  private void validateCreateContainerInput(ApiContainer container, BindingResult errors)
      throws BindException {
    apiContainerPostValidator.validate(container, errors);
    throwBindExceptionIfErrors(errors);
  }

  private void validatePostBody(ApiSampleTemplatePost templatePost, BindingResult errors)
      throws BindException {
    sampleTemplatePostValidator.validate(templatePost, errors);
    throwBindExceptionIfErrors(errors);
  }

  private void throwBindExceptionIfErrors(BindingResult errors) throws BindException {
    if (errors != null && errors.hasErrors()) {
      throw new BindException(errors);
    }
  }

  private FieldmarkNotebookDTO getFieldmarkNotebookDTO(
      FieldmarkApiImportRequest importRequest, User user) throws FieldmarkImportException {
    FieldmarkNotebookDTO fieldmarkNotebookDTO;
    try {
      fieldmarkNotebookDTO =
          fieldmarkServiceClientAdapter.getFieldmarkNotebook(
              user, importRequest.getNotebookId(), importRequest.getIdentifier());
    } catch (IOException ioEx) {
      log.error(
          "The notebookID \""
              + importRequest.getNotebookId()
              + "\" has not being imported from Fieldmark "
              + "cause to the following error: "
              + ioEx.getMessage());
      throw new FieldmarkImportException(
          "The notebookID \""
              + importRequest.getNotebookId()
              + "\" has not being imported from Fieldmark "
              + "cause to the following error: "
              + ioEx.getMessage());
    } catch (HttpServerErrorException serverEx) {
      log.error(
          "The notebook cannot be fetched because of an error on the Fieldmark server: "
              + serverEx.getMessage());
      throw new FieldmarkImportException(
          "The notebook cannot be fetched because of an error on the Fieldmark server: "
              + serverEx.getMessage());
    } catch (HttpClientErrorException clientEx) {
      log.error(
          "The notebook cannot be fetched because of the following error: "
              + clientEx.getMessage());
      throw new FieldmarkImportException(
          "The notebook cannot be fetched because of the following error: "
              + clientEx.getMessage());
    }
    return fieldmarkNotebookDTO;
  }
}
