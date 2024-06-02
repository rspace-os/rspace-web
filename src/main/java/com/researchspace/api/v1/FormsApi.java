package com.researchspace.api.v1;

import com.researchspace.api.v1.controller.ApiFormSearchConfig;
import com.researchspace.api.v1.controller.DocumentApiPaginationCriteria;
import com.researchspace.api.v1.controller.FormTemplatesCommon.FormPost;
import com.researchspace.api.v1.model.ApiForm;
import com.researchspace.api.v1.model.ApiFormSearchResult;
import com.researchspace.model.User;
import com.researchspace.model.field.FieldForm;
import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import javax.ws.rs.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;

@RequestMapping("/api/v1/forms")
public interface FormsApi {

  /**
   * Lists forms visible to the user making the request, searching for '%name%' or '%tag%'
   *
   * @param pgCrit
   * @param srchConfig
   * @param errors
   * @param user
   * @returnAn {@link ApiFormSearchResult}
   * @throws BindException
   */
  @GetMapping
  ApiFormSearchResult getForms(
      @Valid DocumentApiPaginationCriteria pgCrit,
      @Valid ApiFormSearchConfig srchConfig,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException;

  @GetMapping(path = "/{id}")
  ApiForm getFormById(@PathVariable Long id, @RequestAttribute(name = "user") User user)
      throws BindException;

  @PutMapping(path = "/{id}/publish")
  ApiForm publish(@PathVariable Long id, @RequestAttribute(name = "user") User user)
      throws BindException;

  @PutMapping(path = "/{id}/unpublish")
  ApiForm unpublish(@PathVariable Long id, @RequestAttribute(name = "user") User user)
      throws BindException;

  @PutMapping(path = "/{id}/share")
  ApiForm share(@PathVariable Long id, @RequestAttribute(name = "user") User user)
      throws BindException;

  /**
   * ONLY SYSADMINS ARE AUTHORISED TO USE THIS ENDPOINT
   *
   * @param id
   * @param user
   * @return
   * @throws BindException
   */
  @PutMapping(path = "/{id}/shareglobal")
  ApiForm globalShare(@PathVariable Long id, @RequestAttribute(name = "user") User user)
      throws BindException;

  @PutMapping(path = "/{id}/unshare")
  ApiForm unshare(@PathVariable Long id, @RequestAttribute(name = "user") User user)
      throws BindException;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  <T extends FieldForm> ApiForm createForm(
      @Valid FormPost formPost, BindingResult errors, @RequestAttribute(name = "user") User user)
      throws BindException;

  @PutMapping(path = "/{id}")
  <T extends FieldForm> ApiForm editForm(
      @PathVariable Long id,
      @Valid FormPost formPost,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException;

  /**
   * Set form icon
   *
   * @param id
   * @param user
   * @return
   * @throws BindException
   * @throws IOException
   */
  @PostMapping(path = "/{id}/icon")
  @ResponseStatus(HttpStatus.CREATED)
  ApiForm setIcon(
      @PathVariable Long id, MultipartFile iconFile, @RequestAttribute(name = "user") User user)
      throws BindException, IOException;

  @GetMapping(path = "/{formId}/icon/{iconId}")
  void getIcon(
      @PathVariable Long formId,
      @PathVariable Long iconId,
      @RequestAttribute(name = "user") User user,
      HttpServletResponse response)
      throws IOException;

  /**
   * Deletes the form if possible
   *
   * @param id
   * @param user
   * @throws NotFoundException if doesn't exist
   */
  @DeleteMapping(path = "/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void deleteForm(Long id, @RequestAttribute(name = "user") User user);
}
