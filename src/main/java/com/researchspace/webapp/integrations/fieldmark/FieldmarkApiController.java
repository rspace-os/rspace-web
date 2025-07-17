package com.researchspace.webapp.integrations.fieldmark;

import static com.researchspace.service.fieldmark.impl.FieldmarkToRSpaceApiConverter.createContainerRequest;
import static com.researchspace.service.fieldmark.impl.FieldmarkToRSpaceApiConverter.createSampleRequest;
import static com.researchspace.service.fieldmark.impl.FieldmarkToRSpaceApiConverter.createSampleTemplateRequest;

import com.researchspace.api.v1.FieldmarkApi;
import com.researchspace.api.v1.controller.ApiController;
import com.researchspace.api.v1.controller.BaseApiInventoryController;
import com.researchspace.api.v1.controller.ContainersApiController;
import com.researchspace.api.v1.controller.InventoryFilesApiController;
import com.researchspace.api.v1.controller.InventoryFilesApiController.ApiInventoryFilePost;
import com.researchspace.api.v1.controller.SampleTemplatesApiController;
import com.researchspace.api.v1.controller.SamplesApiController;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleField;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSampleTemplatePost;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.fieldmark.model.FieldmarkMultipartFile;
import com.researchspace.fieldmark.model.FieldmarkNotebook;
import com.researchspace.fieldmark.model.exception.FieldmarkImportException;
import com.researchspace.fieldmark.model.utils.FieldmarkDoiIdentifierExtractor;
import com.researchspace.fieldmark.model.utils.FieldmarkFileExtractor;
import com.researchspace.fieldmark.model.utils.FieldmarkTypeExtractor;
import com.researchspace.model.User;
import com.researchspace.model.dtos.fieldmark.FieldmarkNotebookDTO;
import com.researchspace.model.dtos.fieldmark.FieldmarkRecordDTO;
import com.researchspace.service.ApiAvailabilityHandler;
import com.researchspace.service.fieldmark.FieldmarkServiceClientAdapter;
import com.researchspace.service.fieldmark.FieldmarkServiceManager;
import com.researchspace.service.inventory.InventoryIdentifierApiManager;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.List;
import javax.naming.InvalidNameException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

@Slf4j
@ApiController
public class FieldmarkApiController extends BaseApiInventoryController implements FieldmarkApi {

  private @Autowired SampleTemplatesApiController sampleTemplatesApiController;
  private @Autowired InventoryFilesApiController inventoryFilesApiController;
  private @Autowired SamplesApiController samplesApiController;
  private @Autowired ContainersApiController containersApiController;

  private @Autowired FieldmarkServiceClientAdapter fieldmarkServiceClientAdapter;
  private @Autowired InventoryIdentifierApiManager inventoryIdentifierApiManager;
  private @Autowired ApiAvailabilityHandler apiHandler;

  private @Autowired FieldmarkServiceManager fieldmarkManager;

  @Override
  public List<FieldmarkNotebook> getNotebooks(@RequestAttribute(name = "user") User user)
      throws MalformedURLException, URISyntaxException, BindException {
    try {
      return fieldmarkServiceClientAdapter.getFieldmarkNotebookList(user);
    } catch (HttpServerErrorException serverEx) {
      log.error(
          "The list of notebooks cannot be fetched because of an error on the Fieldmark server: "
              + serverEx.getMessage());
      BindingResult errors = new BeanPropertyBindingResult(null, "fieldmarkServer");
      errors.rejectValue(
          "",
          "errors.fieldmark.import",
          new Object[] {},
          "Error fetching notebooks due to the Fieldmark server");
      throwBindExceptionIfErrors(errors);
    }
    return null;
  }

  @Override
  public List<String> getIgsnCandidateFields(
      @PathVariable("notebookId") String notebookId, @RequestAttribute(name = "user") User user)
      throws BindException {
    apiHandler.assertInventoryAndDataciteEnabled(user);
    BindingResult errors = new BeanPropertyBindingResult("notebookId", "notebookId");
    List<String> candidateFields = new LinkedList<>();
    try {
      candidateFields = fieldmarkServiceClientAdapter.getIgsnCandidateFields(user, notebookId);
    } catch (HttpServerErrorException serverEx) {
      log.error(
          "The list of notebooks cannot be fetched because of an error on the Fieldmark server: "
              + serverEx.getMessage());
      errors.rejectValue(
          "",
          "errors.fieldmark.import",
          new Object[] {},
          "Error fetching notebooks due to the Fieldmark server");
      throwBindExceptionIfErrors(errors);
    } catch (IOException ioEx) {
      log.error(
          "The notebookID \""
              + notebookId
              + "\" has not being imported from Fieldmark "
              + "cause to the following error:"
              + ioEx.getMessage());
      errors.rejectValue(
          "",
          "errors.fieldmark.import",
          new Object[] {},
          "Error importing notebook \""
              + notebookId
              + "\" from fieldmark due to the following error: "
              + ioEx.getMessage());
    } finally {
      throwBindExceptionIfErrors(errors);
    }
    return candidateFields;
  }

  @Override
  public FieldmarkApiImportResult importNotebook(
      @RequestBody FieldmarkApiImportRequest importRequest,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {
    validateInput(importRequest, errors);
    throwBindExceptionIfErrors(errors);
    FieldmarkApiImportResult importResult = null;
    try {
      importResult = fieldmarkManager.importNotebook(importRequest, user);

      // build links for Template
      ApiSampleTemplate sampleTemplate =
          sampleApiMgr.getApiSampleTemplateById(importResult.getSampleTemplateId(), user);
      buildAndAddInventoryRecordLinks(sampleTemplate);

      // build links for Container
      ApiContainer container =
          containerApiMgr.getApiContainerIfExists(importResult.getContainerId(), user);
      buildAndAddInventoryRecordLinks(container);

      // build links for Samples
      for (Long sampleId : importResult.getSampleIds()) {
        ApiSample sample = sampleApiMgr.getApiSampleById(sampleId, user);
        buildAndAddInventoryRecordLinks(sample);
      }
    } catch (FieldmarkImportException e) {
      log.error("ERROR: " + e.getMessage());
      errors.rejectValue(
          "", "errors.fieldmark.import", new Object[] {}, "ERROR: " + e.getMessage());
    } finally {
      throwBindExceptionIfErrors(errors);
    }
    return importResult;
  }

//  /// TODO[nik]: remove this
//  public FieldmarkApiImportResult importNotebookOld(
//      @RequestBody FieldmarkApiImportRequest importRequest,
//      BindingResult errors,
//      @RequestAttribute(name = "user") User user)
//      throws BindException, InvalidNameException {
//    validateInput(importRequest, errors);
//    throwBindExceptionIfErrors(errors);
//
//    FieldmarkNotebookDTO notebookDTO = getFieldmarkNotebookDTO(importRequest, errors, user);
//    // call the controller to create 1 sample template
//    ApiSampleTemplatePost sampleTemplatePost = createSampleTemplateRequest(notebookDTO);
//    BindingResult bindingResult = new BeanPropertyBindingResult(sampleTemplatePost, "templatePost");
//    ApiSampleTemplate sampleTemplateRSpace =
//        sampleTemplatesApiController.createNewSampleTemplate(
//            sampleTemplatePost, bindingResult, user);
//
//    // call the Controllers to create 1 container
//    ApiContainer containerToPost = createContainerRequest(notebookDTO, user);
//    bindingResult = new BeanPropertyBindingResult(containerToPost, "containerPost");
//    ApiContainer containerRSpace =
//        containersApiController.createNewContainer(containerToPost, bindingResult, user);
//
//    FieldmarkApiImportResult importResult =
//        new FieldmarkApiImportResult(containerRSpace, sampleTemplateRSpace);
//    // call the Controllers to create 1 sample (with 1 subSample) placed into the container
//    for (FieldmarkRecordDTO currentRecordDTO : notebookDTO.getRecords().values()) {
//
//      ApiSampleWithFullSubSamples samplePost =
//          createSampleRequest(currentRecordDTO, sampleTemplateRSpace, containerRSpace.getId());
//
//      bindingResult = new BeanPropertyBindingResult(samplePost, "samplePost");
//      samplePost = samplesApiController.createNewSample(samplePost, bindingResult, user);
//      importResult.addSample(samplePost);
//
//      if (StringUtils.isNotBlank(notebookDTO.getDoiIdentifierFieldName())) {
//        apiHandler.assertInventoryAndDataciteEnabled(user);
//        associateIdentifierToSample(user, currentRecordDTO, samplePost);
//      }
//      // for each ATTACHMENT field get "globalID" and "content" (having file identifier) and
//      // then upload the right file
//      for (ApiSampleField currentField : samplePost.getFields()) {
//        if (ApiFieldType.ATTACHMENT.equals(currentField.getType())) {
//          String globalId = currentField.getGlobalId();
//          FieldmarkFileExtractor fileExtractor =
//              (FieldmarkFileExtractor) currentRecordDTO.getField(currentField.getName());
//
//          if (StringUtils.isNotBlank(fileExtractor.getFileName())) {
//            inventoryFilesApiController.uploadFile(
//                new FieldmarkMultipartFile(
//                    fileExtractor.getFieldValue(), fileExtractor.getFileName()),
//                new ApiInventoryFilePost(globalId, fileExtractor.getFileName()),
//                user);
//          }
//        }
//      }
//    }
//    return importResult;
//  }
//
//  private void associateIdentifierToSample(
//      User user, FieldmarkRecordDTO currentRecordDTO, ApiSampleWithFullSubSamples samplePost)
//      throws InvalidNameException {
//    FieldmarkDoiIdentifierExtractor doiExtractor =
//        (FieldmarkDoiIdentifierExtractor)
//            currentRecordDTO.getFields().values().stream()
//                .filter(FieldmarkTypeExtractor::isDoiIdentifier)
//                .findFirst()
//                .get();
//    String identifierValue = doiExtractor.getFieldValue().getDoiIdentifier();
//    if (StringUtils.isNotBlank(identifierValue)) {
//      List<ApiInventoryDOI> identifierList =
//          inventoryIdentifierApiManager.findIdentifiers(
//              "draft", false, identifierValue, false, user);
//      if (identifierList.isEmpty()) {
//        throw new IllegalArgumentException(
//            "Unable to find an existing assignable identifier: " + identifierValue);
//      } else if (identifierList.size() > 1) {
//        throw new IllegalArgumentException(
//            "Found more than one identifier to assign for the value: " + identifierValue);
//      } else {
//        inventoryIdentifierApiManager.assignIdentifier(
//            samplePost.getOid(), identifierList.get(0).getId(), user);
//      }
//    }
//  }
//
//  private FieldmarkNotebookDTO getFieldmarkNotebookDTO(
//      FieldmarkApiImportRequest importRequest, BindingResult errors, User user)
//      throws BindException {
//    if (errors == null) {
//      errors = new BeanPropertyBindingResult(importRequest, "importRequest");
//    }
//    FieldmarkNotebookDTO fieldmarkNotebookDTO = null;
//    try {
//      fieldmarkNotebookDTO =
//          fieldmarkServiceClientAdapter.getFieldmarkNotebook(
//              user, importRequest.getNotebookId(), importRequest.getIdentifier());
//    } catch (IOException ioEx) {
//      log.error(
//          "The notebookID \""
//              + importRequest.getNotebookId()
//              + "\" has not being imported from Fieldmark "
//              + "cause to the following error:"
//              + ioEx.getMessage());
//      errors.rejectValue(
//          "",
//          "errors.fieldmark.import",
//          new Object[] {},
//          "Error importing notebook \""
//              + importRequest.getNotebookId()
//              + "\" from fieldmark due to the following error: "
//              + ioEx.getMessage());
//
//    } catch (HttpServerErrorException serverEx) {
//      log.error(
//          "The notebook cannot be fetched because of an error on the Fieldmark server: "
//              + serverEx.getMessage());
//      errors.rejectValue(
//          "",
//          "errors.fieldmark.import",
//          new Object[] {},
//          "Error importing notebook \""
//              + importRequest.getNotebookId()
//              + "\" due to Fieldmark server "
//              + "unavailable: "
//              + serverEx.getMessage());
//    } catch (HttpClientErrorException clientEx) {
//      log.error(
//          "The notebook cannot be fetched because of the following error: "
//              + clientEx.getMessage());
//      errors.rejectValue(
//          "",
//          "errors.fieldmark.import",
//          new Object[] {},
//          "Error importing notebook \""
//              + importRequest.getNotebookId()
//              + "\" due to the following error: "
//              + clientEx.getMessage());
//    } finally {
//      throwBindExceptionIfErrors(errors);
//    }
//    return fieldmarkNotebookDTO;
//  }

  private void validateInput(FieldmarkApiImportRequest importRequest, BindingResult errors) {
    if (importRequest == null || StringUtils.isBlank(importRequest.getNotebookId())) {
      log.error("Cannot import from Fieldmark cause the \"notebookId\" is empty");
      errors.rejectValue(
          "notebookId",
          "errors.fieldmark.import",
          new Object[] {},
          "Error importing notebook casue the request had an empty \"notebookId\"");
    }
  }
}
