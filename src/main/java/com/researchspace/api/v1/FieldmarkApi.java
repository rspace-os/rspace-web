package com.researchspace.api.v1;

import com.researchspace.fieldmark.model.FieldmarkNotebook;
import com.researchspace.model.User;
import com.researchspace.webapp.integrations.fieldmark.FieldmarkApiImportRequest;
import com.researchspace.webapp.integrations.fieldmark.FieldmarkApiImportResult;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.List;
import javax.naming.InvalidNameException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

@RequestMapping("/api/inventory/v1")
public interface FieldmarkApi {

  /***
   *
   * End point that gives the list of Fieldmark notebooks owned by the user
   *
   * @param user
   * @return the list of notebookIDs for the given user
   * @throws BindException
   * @throws MalformedURLException
   * @throws URISyntaxException
   */
  @GetMapping(value = "/fieldmark/notebooks")
  @ResponseStatus(HttpStatus.OK)
  List<FieldmarkNotebook> getNotebooks(User user)
      throws BindException, MalformedURLException, URISyntaxException;

  /**
   * End point that gives the list of fields of the notebook that can be imported as identifiers
   *
   * @param notebookId
   * @param user
   * @return a list of fields that can be imported as Igsn
   * @throws BindException
   * @throws MalformedURLException
   * @throws URISyntaxException
   */
  @GetMapping(value = "/fieldmark/notebooks/igsnCandidateFields")
  @ResponseStatus(HttpStatus.OK)
  List<String> getIgsnCandidateFields(
      @RequestParam(name = "notebookId") String notebookId,
      @RequestAttribute(name = "user") User user)
      throws BindException;

  /***
   *
   * Imports the notebook identified by notebookId into RSpace by creating:
   *  - 1 container
   *  - 1 SampleTemplate
   *  - N Samples (one for each record of the notebook)
   *
   * @param importRequest
   * @param user
   * @throws BindException
   */
  @PostMapping(value = "/import/fieldmark/notebook", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  FieldmarkApiImportResult importNotebook(
      FieldmarkApiImportRequest importRequest, BindingResult errors, User user)
      throws BindException, InvalidNameException;
}
