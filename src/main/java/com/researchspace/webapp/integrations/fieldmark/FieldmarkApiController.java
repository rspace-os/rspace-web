package com.researchspace.webapp.integrations.fieldmark;

import com.researchspace.api.v1.FieldmarkApi;
import com.researchspace.api.v1.controller.ApiController;
import com.researchspace.api.v1.controller.BaseApiInventoryController;
import com.researchspace.fieldmark.model.FieldmarkNotebook;
import com.researchspace.fieldmark.model.exception.FieldmarkImportException;
import com.researchspace.model.User;
import com.researchspace.service.ApiAvailabilityHandler;
import com.researchspace.service.fieldmark.FieldmarkServiceManager;
import java.util.LinkedList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@ApiController
public class FieldmarkApiController extends BaseApiInventoryController implements FieldmarkApi {

  private @Autowired ApiAvailabilityHandler apiHandler;
  private @Autowired FieldmarkServiceManager fieldmarkServiceManagerImpl;

  @Override
  public List<FieldmarkNotebook> getNotebooks(@RequestAttribute(name = "user") User user)
      throws BindException {
    try {
      return fieldmarkServiceManagerImpl.getFieldmarkNotebookList(user);
    } catch (FieldmarkImportException serverEx) {
      log.error(
          "The list of notebooks cannot be fetched because of the following error: "
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
      @RequestParam(name = "notebookId") String notebookId,
      @RequestAttribute(name = "user") User user)
      throws BindException {
    BindingResult errors = new BeanPropertyBindingResult("notebookId", "notebookId");
    List<String> candidateFields = new LinkedList<>();
    try {
      apiHandler.assertInventoryAndDataciteEnabled(user);
      candidateFields = fieldmarkServiceManagerImpl.getIgsnCandidateFields(user, notebookId);
    } catch (FieldmarkImportException serverEx) {
      log.error("Error creating IGSN candidate fields: " + serverEx.getMessage());
      errors.rejectValue(
          "",
          "errors.fieldmark.import",
          new Object[] {},
          "Error creating IGSN candidate fields for notebook \"" + notebookId + "\"");
    } catch (UnsupportedOperationException dataciteEx) {
      log.error("Error creating IGSN candidate fields: " + dataciteEx.getMessage());
      errors.rejectValue(
          "",
          "errors.fieldmark.import",
          new Object[] {},
          "Not possible to create IGSN candidate fields for notebook \""
              + notebookId
              + "\" because "
              + dataciteEx.getMessage());

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
      importResult = fieldmarkServiceManagerImpl.importNotebook(importRequest, user);
    } catch (FieldmarkImportException e) {
      log.error("Error importing notebook from Fieldmark: " + e.getMessage());
      errors.rejectValue(
          "",
          "errors.fieldmark.import",
          new Object[] {},
          "Error importing notebook \""
              + importRequest.getNotebookId()
              + "\" from Fieldmark: "
              + e.getMessage());
    } finally {
      throwBindExceptionIfErrors(errors);
    }
    return importResult;
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
