package com.researchspace.webapp.filter;

import java.util.List;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public interface OriginRefererChecker {

  /**
   * Validates incoming request origin against expected
   *
   * @param request
   * @param response
   * @return An empty optional if OK, otherwise an error message
   */
  Optional<String> checkOriginReferer(HttpServletRequest request, HttpServletResponse response);

  /**
   * Returns a list of accepted domains for CSRF protection.
   *
   * @return List of accepted domains
   */
  List<String> listAcceptedDomains();
}
