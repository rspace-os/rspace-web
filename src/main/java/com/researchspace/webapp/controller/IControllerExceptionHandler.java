package com.researchspace.webapp.controller;

import com.researchspace.model.field.ErrorList;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.ModelAndView;

/**
 * Handles unexpected Exceptions that rise up to the Controller layer. Implementations should handle
 * exceptions arising from both regular and Ajax requests.
 */
public interface IControllerExceptionHandler {

  /**
   * Default handler for logging exceptions in RSpace controllers.<br>
   * Controllers handling ajax responses can optionally add a request attribute "ajax.errors" that
   * contains an {@link ErrorList} of error messages.
   *
   * @param request The {@link HttpServletRequest}
   * @param response The {@link HttpServletResponse} object
   * @param e The <code>Exception</code> thrown.
   * @return A {@link ModelAndView} with a view name of a suitable error page.
   */
  ModelAndView handleExceptions(
      HttpServletRequest request, HttpServletResponse response, Exception e);
}
