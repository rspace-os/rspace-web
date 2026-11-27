package com.researchspace.webapp.filter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

class SessionProblemTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "application/problem+json",
        "application/json, text/javascript, application/problem+json;q=0.5, */*; q=0.01"
      })
  void acceptedWhenAcceptNamesProblemJson(String accept) {
    assertTrue(SessionProblem.isAcceptedBy(requestAccepting(accept)));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"*/*", "application/*", "application/json", "application/problem+json;q=0", "%%"})
  void notAcceptedForWildcardsRefusalOrInvalidAccept(String accept) {
    assertFalse(SessionProblem.isAcceptedBy(requestAccepting(accept)));
  }

  private MockHttpServletRequest requestAccepting(String accept) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Accept", accept);
    return request;
  }
}
