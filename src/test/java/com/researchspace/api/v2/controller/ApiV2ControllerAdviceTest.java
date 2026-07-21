package com.researchspace.api.v2.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v2.model.ApiV2PaginationCriteria;
import com.researchspace.service.MessageSourceUtils;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.web.method.ControllerAdviceBean;

class ApiV2ControllerAdviceTest {

  private ApiV2ControllerAdvice advice;

  @BeforeEach
  void setUp() {
    StaticMessageSource source = new StaticMessageSource();
    source.addMessage("errors.api.v2.forbidden", Locale.getDefault(), "Forbidden detail");
    source.addMessage(
        "errors.api.pagination.page.min", Locale.getDefault(), "Page must be 1 or greater.");
    source.addMessage(
        "errors.api.v2.authenticationRequired", Locale.getDefault(), "Authentication detail");
    source.addMessage("errors.api.v2.invalidRequest", Locale.getDefault(), "Invalid detail");
    source.addMessage("errors.api.v2.tooManyRequests", Locale.getDefault(), "Throttle detail");
    source.addMessage("errors.api.v2.unexpected", Locale.getDefault(), "Unexpected detail");
    advice = new ApiV2ControllerAdvice(new MessageSourceUtils(source));
  }

  @Test
  void mapsAuthenticationValidationAndTypeErrorsToProblemDetails() {
    assertProblem(advice.handleAuthentication(), HttpStatus.UNAUTHORIZED, "Authentication detail");
    assertProblem(advice.handleTypeMismatch(), HttpStatus.BAD_REQUEST, "Invalid detail");
  }

  @Test
  void localizesFieldAndGlobalValidationErrors() {
    ApiV2PaginationCriteria pagination = new ApiV2PaginationCriteria();
    pagination.setPage(0);
    BeanPropertyBindingResult errors = new BeanPropertyBindingResult(pagination, "pagination");
    new ApiV2PaginationCriteriaValidator().validate(pagination, errors);
    errors.reject("errors.api.v2.invalidRequest");

    ResponseEntity<ApiV2Problem> response = advice.handleBindException(new BindException(errors));

    assertProblem(response, HttpStatus.BAD_REQUEST, "Page must be 1 or greater.; Invalid detail");
  }

  @Test
  void mapsAuthorizationThrottlingAndUnexpectedErrorsToProblemDetails() {
    assertProblem(advice.handleAuthorization(), HttpStatus.FORBIDDEN, "Forbidden detail");
    assertProblem(advice.handleThrottling(), HttpStatus.TOO_MANY_REQUESTS, "Throttle detail");
    assertProblem(
        advice.handleUnexpected(new RuntimeException("sensitive internal detail")),
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Unexpected detail");
  }

  @Test
  void appliesOnlyToV2Controllers() {
    ControllerAdviceBean adviceBean = new ControllerAdviceBean(advice);

    assertTrue(adviceBean.isApplicableToBeanType(UsersV2Controller.class));
  }

  private static void assertProblem(
      ResponseEntity<ApiV2Problem> response, HttpStatus status, String detail) {
    assertEquals(status, response.getStatusCode());
    assertEquals(ApiV2Problem.PROBLEM_JSON, response.getHeaders().getContentType());
    assertEquals(detail, response.getBody().detail());
  }
}
