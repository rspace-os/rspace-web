package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.apiutils.ApiError;
import com.researchspace.apiutils.BindErrorList;
import com.researchspace.service.FilestoreOperationForbiddenException;
import com.researchspace.service.MessageSourceUtils;
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
    MessageSourceUtils messages = mock(MessageSourceUtils.class);
    ApiControllerAdvice advice = new ApiControllerAdvice();
    advice.messages = messages;
    BeanPropertyBindingResult errors = new BeanPropertyBindingResult(new TestForm(), "fieldmark");
    errors.rejectValue("notebookId", "apps.fieldmark.errors.notebookIdRequired", null, null);
    errors.reject("apps.fieldmark.errors.fetchNotebooks", null, null);
    FieldError fieldError = errors.getFieldError();
    ObjectError globalError = errors.getGlobalError();
    when(messages.getMessage(fieldError)).thenReturn("A Fieldmark notebook ID is required.");
    when(messages.getMessage(globalError)).thenReturn("Unable to fetch Fieldmark notebooks.");

    ApiError apiError = advice.getApiErrorFromBindException(new BindException(errors));

    assertNull(fieldError.getDefaultMessage());
    assertNull(globalError.getDefaultMessage());
    assertEquals(
        List.of(
            "notebookId: A Fieldmark notebook ID is required.",
            "fieldmark: Unable to fetch Fieldmark notebooks."),
        apiError.getErrors());
    BindErrorList errorList = (BindErrorList) apiError.getData();
    assertEquals(
        "A Fieldmark notebook ID is required.",
        errorList.getValidationErrors().get(0).getMessage());
    assertEquals(
        "Unable to fetch Fieldmark notebooks.",
        errorList.getValidationErrors().get(1).getMessage());
    verify(messages).getMessage(fieldError);
    verify(messages).getMessage(globalError);
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
}
