package com.researchspace.api.v2.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import com.researchspace.featureflags.FeatureFlagNotFoundException;
import com.researchspace.featureflags.FeatureFlagPermissionException;
import com.researchspace.featureflags.FeatureFlagReadOnlyException;
import com.researchspace.service.MessageSourceUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
public class FeatureFlagsV2ControllerErrorShapeTest {

  private final FeatureFlagsV2Controller controller = new FeatureFlagsV2Controller();

  @Mock private MessageSourceUtils messages;

  @BeforeEach
  public void setUp() {
    ReflectionTestUtils.setField(controller, "messages", messages);
  }

  @Test
  public void errorResponsesAreRfc9457ProblemDetails() {
    when(messages.getMessage("api.v2.featureFlags.errors.unknown", new Object[] {"someFlag"}))
        .thenReturn("Unknown feature flag: someFlag");
    when(messages.getMessage("api.v2.featureFlags.errors.notPermitted"))
        .thenReturn("Feature flag operation is not permitted");
    when(messages.getMessage("api.v2.featureFlags.errors.readOnly", new Object[] {"someFlag"}))
        .thenReturn("Feature flag is controlled by properties file: someFlag");

    assertProblem(
        controller.handleNotFound(new FeatureFlagNotFoundException("someFlag")),
        HttpStatus.NOT_FOUND,
        "Unknown feature flag: someFlag");
    assertProblem(
        controller.handleForbidden(new FeatureFlagPermissionException()),
        HttpStatus.FORBIDDEN,
        "Feature flag operation is not permitted");
    assertProblem(
        controller.handleReadOnly(new FeatureFlagReadOnlyException("someFlag")),
        HttpStatus.CONFLICT,
        "Feature flag is controlled by properties file: someFlag");
    assertProblem(
        controller.handleResponseStatus(
            new ResponseStatusException(HttpStatus.BAD_REQUEST, "missing value")),
        HttpStatus.BAD_REQUEST,
        "missing value");
  }

  @Test
  public void reasonlessResponseStatusExceptionOmitsDetail() {
    ResponseEntity<ApiV2Problem> response =
        controller.handleResponseStatus(new ResponseStatusException(HttpStatus.BAD_REQUEST));
    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertNull(response.getBody().detail());
  }

  private void assertProblem(
      ResponseEntity<ApiV2Problem> response, HttpStatus expectedStatus, String expectedDetail) {
    assertEquals(expectedStatus, response.getStatusCode());
    assertEquals(ApiV2Problem.PROBLEM_JSON, response.getHeaders().getContentType());
    ApiV2Problem problem = response.getBody();
    assertEquals(expectedStatus.getReasonPhrase(), problem.title());
    assertEquals(expectedStatus.value(), problem.status());
    assertEquals(expectedDetail, problem.detail());
  }
}
