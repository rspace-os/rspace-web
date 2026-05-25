package com.researchspace.webapp.integrations.dmpassistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.model.User;
import com.researchspace.service.UserManager;
import com.researchspace.webapp.controller.AjaxReturnObject;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Pure unit tests for {@link DMPAssistantController}. The controller is now thin: each test
 * verifies it resolves the caller's User from the Principal, delegates to a mocked {@link
 * DMPAssistantProvider}, and translates {@link HttpClientErrorException} responses into the
 * controller's {@link AjaxReturnObject} error contract.
 */
class DMPAssistantControllerTest {

  private static final String USERNAME = "auser";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @InjectMocks private DMPAssistantController controller;
  @Mock private UserManager userManager;
  @Mock private DMPAssistantProvider dmpAssistantProvider;

  private User user;
  private final Principal principal = () -> USERNAME;

  @BeforeEach
  void setUp() {
    openMocks(this);
    user = new User(USERNAME);
    when(userManager.getUserByUsername(USERNAME)).thenReturn(user);
  }

  @Test
  void meProxyDelegatesToProviderWithResolvedUser() throws Exception {
    JsonNode payload = MAPPER.readTree("{\"email\":\"u@example.ca\"}");
    when(dmpAssistantProvider.me(user)).thenReturn(payload);

    AjaxReturnObject<JsonNode> result = controller.me(principal);

    assertNotNull(result.getData());
    assertEquals("u@example.ca", result.getData().get("email").asText());
  }

  @Test
  void listPlansProxyForwardsPaginationAndComplete() throws Exception {
    JsonNode payload = MAPPER.readTree("{\"items\":[]}");
    when(dmpAssistantProvider.listPlans(eq("2"), eq("25"), eq(true), eq(user))).thenReturn(payload);

    AjaxReturnObject<JsonNode> result = controller.listPlans("2", "25", true, principal);

    assertEquals(0, result.getData().get("items").size());
  }

  @Test
  void getPlanByIdProxyForwardsCompleteFlag() throws Exception {
    JsonNode payload = MAPPER.readTree("{\"dmp\":{\"id\":3}}");
    when(dmpAssistantProvider.getPlanById(eq("3"), eq(false), eq(user))).thenReturn(payload);

    AjaxReturnObject<JsonNode> result = controller.getPlanById("3", false, principal);

    assertEquals(3, result.getData().get("dmp").get("id").asInt());
  }

  @Test
  void createPlanProxyPostsBodyToProvider() throws Exception {
    JsonNode body = MAPPER.readTree("{\"dmp\":{\"title\":\"X\"}}");
    JsonNode response = MAPPER.readTree("{\"dmp\":{\"id\":1,\"title\":\"X\"}}");
    when(dmpAssistantProvider.createPlan(eq(body), eq(user))).thenReturn(response);

    AjaxReturnObject<JsonNode> result = controller.createPlan(body, principal);

    assertEquals(1, result.getData().get("dmp").get("id").asInt());
  }

  @Test
  void editPlanAnswersProxyPutsBodyToProvider() throws Exception {
    JsonNode body = MAPPER.readTree("{\"answers\":[]}");
    JsonNode response = MAPPER.readTree("{\"ok\":true}");
    when(dmpAssistantProvider.editPlanAnswers(eq("9"), eq(body), eq(user))).thenReturn(response);

    AjaxReturnObject<JsonNode> result = controller.editPlanAnswers("9", body, principal);

    assertEquals(true, result.getData().get("ok").asBoolean());
  }

  @Test
  void listTemplatesProxyDelegates() throws Exception {
    JsonNode payload = MAPPER.readTree("{\"items\":[{\"id\":1}]}");
    when(dmpAssistantProvider.listTemplates(eq(user))).thenReturn(payload);

    AjaxReturnObject<JsonNode> result = controller.listTemplates(principal);

    assertEquals(1, result.getData().get("items").get(0).get("id").asInt());
  }

  @Test
  void getTemplateByIdProxyDelegates() throws Exception {
    JsonNode payload = MAPPER.readTree("{\"id\":42}");
    when(dmpAssistantProvider.getTemplateById(eq("42"), eq(user))).thenReturn(payload);

    AjaxReturnObject<JsonNode> result = controller.getTemplateById("42", principal);

    assertEquals(42, result.getData().get("id").asInt());
  }

  @Test
  void proxyTranslatesHttpStatusCodeExceptionIntoErrorList() throws Exception {
    when(dmpAssistantProvider.me(user))
        .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND, "no token"));

    AjaxReturnObject<JsonNode> result = controller.me(principal);

    assertNull(result.getData());
    assertNotNull(result.getError());
  }
}
