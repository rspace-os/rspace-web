package com.researchspace.webapp.integrations.fieldmark;

import static com.researchspace.service.fieldmark.impl.FieldmarkToRSpaceApiConverter.createContainerRequest;
import static com.researchspace.service.fieldmark.impl.FieldmarkToRSpaceApiConverter.createSampleRequest;
import static com.researchspace.service.fieldmark.impl.FieldmarkToRSpaceApiConverter.createSampleTemplateRequest;

import com.researchspace.api.v1.FieldmarkApi;
import com.researchspace.api.v1.controller.ApiController;
import com.researchspace.api.v1.controller.BaseApiController;
import com.researchspace.api.v1.controller.ContainersApiController;
import com.researchspace.api.v1.controller.InventoryFilesApiController;
import com.researchspace.api.v1.controller.InventoryFilesApiController.ApiInventoryFilePost;
import com.researchspace.api.v1.controller.SampleTemplatesApiController;
import com.researchspace.api.v1.controller.SamplesApiController;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiSampleField;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSampleTemplatePost;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.fieldmark.model.FieldmarkMultipartFile;
import com.researchspace.fieldmark.model.FieldmarkNotebook;
import com.researchspace.fieldmark.model.utils.FieldmarkFileExtractor;
import com.researchspace.model.User;
import com.researchspace.model.dtos.fieldmark.FieldmarkNotebookDTO;
import com.researchspace.model.dtos.fieldmark.FieldmarkRecordDTO;
import com.researchspace.service.fieldmark.FieldmarkServiceClientAdapter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.List;
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
public class FieldmarkApiController extends BaseApiController implements FieldmarkApi {

  private @Autowired SampleTemplatesApiController sampleTemplatesApiController;
  private @Autowired InventoryFilesApiController inventoryFilesApiController;
  private @Autowired SamplesApiController samplesApiController;
  private @Autowired ContainersApiController containersApiController;

  private @Autowired FieldmarkServiceClientAdapter fieldmarkServiceClientAdapter;

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

    FieldmarkNotebookDTO notebookDTO = getFieldmarkNotebookDTO(importRequest, errors, user);
    // call the controller to create 1 sample template
    ApiSampleTemplatePost sampleTemplatePost = createSampleTemplateRequest(notebookDTO);
    BindingResult bindingResult = new BeanPropertyBindingResult(sampleTemplatePost, "templatePost");
    ApiSampleTemplate sampleTemplateRSpace =
        sampleTemplatesApiController.createNewSampleTemplate(
            sampleTemplatePost, bindingResult, user);

    // call the Controllers to create 1 container
    ApiContainer containerToPost = createContainerRequest(notebookDTO, user);
    bindingResult = new BeanPropertyBindingResult(containerToPost, "containerPost");
    ApiContainer containerRSpace =
        containersApiController.createNewContainer(containerToPost, bindingResult, user);

    FieldmarkApiImportResult result =
        new FieldmarkApiImportResult(
            containerRSpace.getGlobalId(),
            containerRSpace.getName(),
            sampleTemplateRSpace.getGlobalId());
    // call the Controllers to create 1 sample (with 1 subSample) placed into the container
    for (FieldmarkRecordDTO currentRecordDTO : notebookDTO.getRecords().values()) {

      ApiSampleWithFullSubSamples samplePost =
          createSampleRequest(currentRecordDTO, sampleTemplateRSpace, containerRSpace.getId());

      bindingResult = new BeanPropertyBindingResult(samplePost, "samplePost");
      samplePost = samplesApiController.createNewSample(samplePost, bindingResult, user);
      result.addSampleGlobalId(samplePost.getGlobalId());

      // for each ATTACHMENT field get "globalID" and "content" (having file identifier) and
      // then upload the right file
      for (ApiSampleField currentField : samplePost.getFields()) {
        if (ApiFieldType.ATTACHMENT.equals(currentField.getType())) {
          String globalId = currentField.getGlobalId();

          FieldmarkFileExtractor fileExtractor =
              (FieldmarkFileExtractor) currentRecordDTO.getField(currentField.getName());

          inventoryFilesApiController.uploadFile(
              new FieldmarkMultipartFile(
                  fileExtractor.getFieldValue(), fileExtractor.getFileName()),
              new ApiInventoryFilePost(globalId, fileExtractor.getFileName()),
              user);
        }
      }
    }
    return result;
  }

  private FieldmarkNotebookDTO getFieldmarkNotebookDTO(
      FieldmarkApiImportRequest importRequest, BindingResult errors, User user)
      throws BindException {
    if (errors == null) {
      errors = new BeanPropertyBindingResult(importRequest, "importRequest");
    }
    FieldmarkNotebookDTO fieldmarkNotebookDTO = null;
    try {
      fieldmarkNotebookDTO =
          fieldmarkServiceClientAdapter.getFieldmarkNotebook(
              user, importRequest.getNotebookId(), importRequest.getIdentifier());
    } catch (IOException ioEx) {
      log.error(
          "The notebookID \""
              + importRequest.getNotebookId()
              + "\" has not being imported from Fieldmark "
              + "cause to the following error:"
              + ioEx.getMessage());
      errors.rejectValue(
          "",
          "errors.fieldmark.import",
          new Object[] {},
          "Error importing notebook \""
              + importRequest.getNotebookId()
              + "\" from fieldmark due to the following error: "
              + ioEx.getMessage());

    } catch (HttpServerErrorException serverEx) {
      log.error(
          "The notebook cannot be fetched because of an error on the Fieldmark server: "
              + serverEx.getMessage());
      errors.rejectValue(
          "",
          "errors.fieldmark.import",
          new Object[] {},
          "Error importing notebook \""
              + importRequest.getNotebookId()
              + "\" due to Fieldmark server "
              + "unavailable: "
              + serverEx.getMessage());
    } catch (HttpClientErrorException clientEx) {
      log.error(
          "The notebook cannot be fetched because of the following error: "
              + clientEx.getMessage());
      errors.rejectValue(
          "",
          "errors.fieldmark.import",
          new Object[] {},
          "Error importing notebook \""
              + importRequest.getNotebookId()
              + "\" due to the following error: "
              + clientEx.getMessage());
    } finally {
      throwBindExceptionIfErrors(errors);
    }
    return fieldmarkNotebookDTO;
  }

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
