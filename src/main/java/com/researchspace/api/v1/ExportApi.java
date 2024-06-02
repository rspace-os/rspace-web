package com.researchspace.api.v1;

import com.researchspace.api.v1.controller.ExportApiController.ExportApiConfig;
import com.researchspace.api.v1.controller.ExportApiController.ExportRetrievalConfig;
import com.researchspace.api.v1.model.ApiJob;
import com.researchspace.model.User;
import com.researchspace.service.archive.export.ExportFailureException;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Export related actions */
@RequestMapping("/api/v1/export")
public interface ExportApi {

  /**
   * Supports export to HTML or XML for client to export own work or groups own work.
   *
   * @param config export config from request params
   * @param errors
   * @param user the api client
   * @return A ApiJob encapsulating an asynchronous job and its progress.
   * @throws BindException if <code>config</code> is invalid
   * @throws ExportFailureException if export job could not be launched.
   */
  @PostMapping("/{format}/{scope}")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public ApiJob export(ExportApiConfig config, BindingResult errors, User user)
      throws BindException;

  /**
   * Supports export to HTML or XML for given user or group id
   *
   * @param config export config from request params
   * @param errors
   * @param user the api client
   * @return ApiJob encapsulating an asynchronous job and its progress.
   * @throws BindException if <code>config</code> is invalid
   * @throws ExportFailureException if export job could not be launched.
   */
  @PostMapping("/{format}/{scope}/{id}")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public ApiJob exportById(ExportApiConfig config, BindingResult errors, User user)
      throws BindException;

  @GetMapping("/{resource}")
  public void getExport(
      ExportRetrievalConfig params, BindingResult errors, HttpServletResponse response)
      throws BindException;
}
