package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.researchspace.api.v1.auth.ApiAuthenticationException;
import com.researchspace.apiutils.ApiError;
import com.researchspace.apiutils.BindErrorList;
import com.researchspace.service.FilestoreOperationForbiddenException;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.chemistry.ChemistryClientException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

class ApiControllerAdviceTest {

  @Test
  void bindErrorsAreResolvedCentrally() {
    MessageSourceUtils messages = new MessageSourceUtils(new JsonMessageSource());
    ApiControllerAdvice advice = new ApiControllerAdvice();
    advice.messages = messages;
    BeanPropertyBindingResult errors = new BeanPropertyBindingResult(new TestForm(), "fieldmark");
    errors.rejectValue("notebookId", "apps.fieldmark.errors.notebookIdRequired", null, null);
    errors.reject("apps.fieldmark.errors.fetchNotebooks", null, null);
    FieldError fieldError = errors.getFieldError();
    ObjectError globalError = errors.getGlobalError();
    ApiError apiError = advice.getApiErrorFromBindException(new BindException(errors));

    assertNull(fieldError.getDefaultMessage());
    assertNull(globalError.getDefaultMessage());
    assertEquals(
        List.of(
            "notebookId: Error importing notebook because the request had an empty \"notebookId\"",
            "fieldmark: Error fetching notebooks due to the Fieldmark server"),
        apiError.getErrors());
    assertEquals("Errors detected : 2", apiError.getMessage());
    BindErrorList errorList = (BindErrorList) apiError.getData();
    assertEquals(
        "Error importing notebook because the request had an empty \"notebookId\"",
        errorList.getValidationErrors().get(0).getMessage());
    assertEquals(
        "Error fetching notebooks due to the Fieldmark server",
        errorList.getValidationErrors().get(1).getMessage());
  }

  private static class TestForm {
    public String getNotebookId() {
      return null;
    }
  }

  /**
   * Locks in that the filestore delete gate's exception maps to HTTP 403 (per RSDEV-1110's
   * acceptance criteria) rather than the 401 that Shiro's AuthorizationException would produce.
   */
  @Test
  void filestoreOperationForbidden_mapsTo403() {
    ApiControllerAdvice advice = new ApiControllerAdvice();

    ResponseEntity<Object> response =
        advice.handleFilestoreOperationForbidden(
            new FilestoreOperationForbiddenException("not your file"), null);

    assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
  }

  @Test
  void chemistryMessageIsResolvedAtApiBoundary() {
    ApiControllerAdvice advice = new ApiControllerAdvice();
    advice.messages = new MessageSourceUtils(new JsonMessageSource());
    ChemistryClientException exception =
        new ChemistryClientException("errors.chemistry.searchRequestFailed", new Object[] {503});

    ResponseEntity<Object> response = advice.handleChemistryClientException(exception, null);

    ApiError error = (ApiError) response.getBody();
    assertEquals(
        "Unsuccessful search request to the chemistry service, status code: 503.",
        error.getMessage());
  }

  @Test
  void authenticationMessageIsResolvedAtApiBoundary() {
    ApiControllerAdvice advice = new ApiControllerAdvice();
    advice.messages = new MessageSourceUtils(new JsonMessageSource());

    ResponseEntity<Object> response =
        advice.handleAuth(new ApiAuthenticationException("oauth.errors.invalidCredentials"), null);

    ApiError error = (ApiError) response.getBody();
    assertEquals("Invalid user credentials.", error.getMessage());
  }
}
