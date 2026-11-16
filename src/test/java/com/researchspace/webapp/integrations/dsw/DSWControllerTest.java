package com.researchspace.webapp.integrations.dsw;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.researchspace.model.User;
import com.researchspace.model.apps.AppConfigElement;
import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.model.apps.UserAppConfig;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.UserAppConfigManager;
import com.researchspace.service.UserManager;
import com.researchspace.webapp.controller.AjaxReturnObject;
import com.researchspace.webapp.integrations.dsw.exception.DSWVersionCheckException;
import com.researchspace.webapp.integrations.dsw.exception.DSWVersionCheckException.Reason;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;

@ExtendWith(MockitoExtension.class)
class DSWControllerTest {

  private static final String ALIAS = "dsw";
  private static final String PLAN_UUID = "22222222-2222-2222-2222-222222222222";

  @Mock private DSWClient dswClient;
  @Mock private UserManager userManager;
  @Mock private UserAppConfigManager userAppConfigManager;
  @Mock private MessageSourceUtils messages;
  @Mock private User user;
  @Mock private UserAppConfig userAppConfig;
  @Mock private AppConfigElementSet cfg;
  @Mock private AppConfigElement aliasElement;

  private DSWController controller;

  @BeforeEach
  void setUp() {
    controller = new DSWController(dswClient, userManager, userAppConfigManager);
    ReflectionTestUtils.setField(controller, "messages", messages);
    when(userManager.getAuthenticatedUserInSession()).thenReturn(user);
    when(userAppConfigManager.getByAppName("app.dsw", user)).thenReturn(userAppConfig);
    when(userAppConfig.getAppConfigElementSets()).thenReturn(Set.of(cfg));
    when(cfg.getConfigElements()).thenReturn(Set.of(aliasElement));
    when(aliasElement.getValue()).thenReturn(ALIAS);
  }

  private static DSWVersionCheckException versionCheckFailure(Reason reason) {
    return new DSWVersionCheckException(reason, "version check failed");
  }

  private void givenMessage(String code) {
    when(messages.getMessage(code)).thenReturn("message for " + code);
  }

  private static void assertError(String code, AjaxReturnObject<JsonNode> result) {
    assertNull(result.getData());
    assertEquals(List.of("message for " + code), result.getErrorMsg().getErrorMessages());
  }

  @Test
  void listPlansReportsVersionCheckFailureRatherThanCredentials() throws Exception {
    when(dswClient.getProjectsForCurrentUserJson(ALIAS, cfg))
        .thenThrow(versionCheckFailure(Reason.UNAVAILABLE));
    givenMessage("apps.dsw.errors.versionCheck");

    assertError("apps.dsw.errors.versionCheck", controller.listDSWPlans(ALIAS));
  }

  @Test
  void listPlansReportsUnfollowedRedirect() throws Exception {
    when(dswClient.getProjectsForCurrentUserJson(ALIAS, cfg))
        .thenThrow(versionCheckFailure(Reason.REDIRECT));
    givenMessage("apps.dsw.errors.redirect");

    assertError("apps.dsw.errors.redirect", controller.listDSWPlans(ALIAS));
  }

  @Test
  void currentUserReturnsBadGatewayWithMessageOnVersionCheckFailure() throws Exception {
    when(dswClient.currentUser(ALIAS, cfg)).thenThrow(versionCheckFailure(Reason.UNAVAILABLE));
    givenMessage("apps.dsw.errors.versionCheck");

    ResponseEntity<JsonNode> response = controller.currentUsers(ALIAS);

    assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
    assertEquals(
        "message for apps.dsw.errors.versionCheck", response.getBody().get("message").asText());
  }

  @Test
  void importPlanReportsVersionCheckFailure() throws Exception {
    when(dswClient.importPlan(ALIAS, cfg, PLAN_UUID))
        .thenThrow(versionCheckFailure(Reason.UNAVAILABLE));
    givenMessage("apps.dsw.errors.versionCheck");

    assertError("apps.dsw.errors.versionCheck", controller.importPlan(ALIAS, PLAN_UUID));
  }

  @Test
  void importPlanReportsRejectedApiKeyAsCredentialsError() throws Exception {
    when(dswClient.importPlan(ALIAS, cfg, PLAN_UUID))
        .thenThrow(
            HttpClientErrorException.create(
                HttpStatus.UNAUTHORIZED, "Unauthorized", new HttpHeaders(), new byte[0], null));
    givenMessage("apps.dsw.errors.credentials");

    assertError("apps.dsw.errors.credentials", controller.importPlan(ALIAS, PLAN_UUID));
  }

  @Test
  void importPlanReportsOtherClientErrorAsProjectError() throws Exception {
    when(dswClient.importPlan(ALIAS, cfg, PLAN_UUID))
        .thenThrow(
            HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found", new HttpHeaders(), new byte[0], null));
    givenMessage("apps.dsw.errors.project");

    assertError("apps.dsw.errors.project", controller.importPlan(ALIAS, PLAN_UUID));
  }

  @Test
  void importPlanReportsUnexpectedFailureInsteadOfFailingRequest() throws Exception {
    when(dswClient.importPlan(ALIAS, cfg, PLAN_UUID))
        .thenThrow(new IllegalStateException("unexpected"));
    givenMessage("apps.dsw.errors.project");

    assertError("apps.dsw.errors.project", controller.importPlan(ALIAS, PLAN_UUID));
  }
}
