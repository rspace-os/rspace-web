package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.service.FilestoreOperationForbiddenException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ApiControllerAdviceTest {

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
