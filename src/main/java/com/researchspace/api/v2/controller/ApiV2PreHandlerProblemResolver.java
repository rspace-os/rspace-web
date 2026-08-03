package com.researchspace.api.v2.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * Renders RFC 9457 problem bodies for REST API v2 errors raised before a handler is selected.
 *
 * <p>{@link ApiV2ControllerAdvice} is a selected {@code @ControllerAdvice} (it names base packages
 * so it cannot hijack v1 or the MVC controllers). Spring only consults a selected advice when it
 * has a {@code HandlerMethod} to match the selector against: {@code
 * ExceptionHandlerExceptionResolver} passes a null handler type, and {@code
 * HandlerTypePredicate.test(null)} is false once selectors exist. Exceptions raised by the handler
 * mapping itself -- an unsupported method, an unacceptable {@code Accept}, an unmapped path -- have
 * no handler yet, so they skip the advice and reach {@code DefaultHandlerExceptionResolver}, which
 * calls {@code sendError} and emits the container's HTML error page.
 *
 * <p>This resolver closes that gap for {@code /api/v2} only. It runs ahead of Spring's resolvers,
 * ignores anything that already has a {@code HandlerMethod}, and otherwise delegates to the advice
 * so both paths share one status, header and message mapping.
 */
@Slf4j
public class ApiV2PreHandlerProblemResolver implements HandlerExceptionResolver, Ordered {

  private static final String API_V2_PREFIX = "/api/v2/";

  private final ApiV2ControllerAdvice advice;
  private final ObjectMapper objectMapper;

  public ApiV2PreHandlerProblemResolver(ApiV2ControllerAdvice advice, ObjectMapper objectMapper) {
    this.advice = advice;
    this.objectMapper = objectMapper;
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }

  @Override
  public ModelAndView resolveException(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    if (handler instanceof HandlerMethod || !isApiV2Request(request) || response.isCommitted()) {
      return null;
    }
    ResponseEntity<ApiV2Problem> problem = advice.handleUnexpected(ex);
    try {
      response.setStatus(problem.getStatusCode().value());
      problem
          .getHeaders()
          .forEach((name, values) -> values.forEach(value -> response.addHeader(name, value)));
      response.setContentType(ApiV2Problem.PROBLEM_JSON.toString());
      response.setCharacterEncoding("UTF-8");
      objectMapper.writeValue(response.getOutputStream(), problem.getBody());
      response.flushBuffer();
    } catch (IOException ioException) {
      log.warn("Could not write a REST API v2 problem response", ioException);
      return null;
    }
    // An empty ModelAndView marks the exception handled with no view to render.
    return new ModelAndView();
  }

  private static boolean isApiV2Request(HttpServletRequest request) {
    String uri = request.getRequestURI();
    String contextPath = request.getContextPath();
    String path =
        contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)
            ? uri.substring(contextPath.length())
            : uri;
    return path.startsWith(API_V2_PREFIX);
  }
}
