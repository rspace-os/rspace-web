/**
 * RSpace API Access your RSpace documents programmatically. All requests require authentication
 * using an API key set as the value of the header `RSpace-API-Key`.
 */
package com.researchspace.api.v1;

import com.researchspace.api.v1.controller.ApiDocSearchConfig;
import com.researchspace.api.v1.controller.DocumentApiPaginationCriteria;
import com.researchspace.api.v1.model.ApiDocument;
import com.researchspace.api.v1.model.ApiDocumentSearchResult;
import com.researchspace.model.User;
import com.researchspace.service.DocumentAlreadyEditedException;
import javassist.NotFoundException;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

@RequestMapping("/api/v1/documents")
public interface DocumentsApi {

  @GetMapping
  /**
   * Min search term length is 2 chars (but 5 for Sysadmins). Sysadmin cannot do wildcard searches
   */
  ApiDocumentSearchResult getDocuments(
      DocumentApiPaginationCriteria pgCriteria,
      ApiDocSearchConfig srchConfig,
      BindingResult errors,
      User user)
      throws BindException;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  ApiDocument createNewDocument(
      @RequestBody @Valid ApiDocument document, BindingResult errors, User user)
      throws BindException;

  /**
   * Gets complete document with form and fields populated.
   *
   * @param id
   * @param user
   * @return
   * @throws NotFoundException if resource could not be found
   */
  @GetMapping(
      value = "/{id}",
      produces = {"application/json", "text/csv"})
  ApiDocument getDocumentById(@PathVariable Long id, User user);

  /**
   * Marks document as deleted.
   *
   * @param id
   * @param user
   * @return
   * @throws DocumentAlreadyEditedException
   * @throws NotFoundException if resource could not be found
   */
  @DeleteMapping(value = "/{id}")
  void deleteDocumentById(@PathVariable Long id, User user, HttpServletResponse response)
      throws DocumentAlreadyEditedException;

  /**
   * @throws DocumentAlreadyEditedException when someone is editing the document at the time of API
   *     request
   */
  @PutMapping(value = "/{id}")
  ApiDocument createNewRevision(
      @PathVariable Long id,
      @RequestBody @Valid ApiDocument document,
      BindingResult errors,
      User user)
      throws BindException, DocumentAlreadyEditedException;
}
