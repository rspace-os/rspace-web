package com.researchspace.api.v2.controller;

import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * A 405 raised because a resource does not expose the selected operation, carrying the {@code
 * Allow} header RFC 9110 requires.
 *
 * <p>Spring's own {@code HttpRequestMethodNotSupportedException} would supply the header too, but
 * it extends {@code ServletException} and is therefore checked, which would put a {@code throws}
 * clause on every route method in {@link ApiV2CrudController}. This stays unchecked and overrides
 * only {@code getHeaders()}, which {@code ApiV2ControllerAdvice} copies onto the problem response.
 */
public class ApiV2MethodNotAllowedException extends ResponseStatusException {

  private final HttpHeaders headers = new HttpHeaders();

  public ApiV2MethodNotAllowedException(Set<HttpMethod> allowed) {
    super(HttpStatus.METHOD_NOT_ALLOWED);
    headers.setAllow(allowed);
  }

  @Override
  public HttpHeaders getHeaders() {
    return headers;
  }
}
