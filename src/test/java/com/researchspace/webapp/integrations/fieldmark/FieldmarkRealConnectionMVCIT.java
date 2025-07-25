package com.researchspace.webapp.integrations.fieldmark;

import static com.researchspace.service.IntegrationsHandler.FIELDMARK_APP_NAME;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.controller.API_MVC_TestBase;
import com.researchspace.api.v1.controller.API_VERSION;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.model.User;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import com.researchspace.service.UserConnectionManager;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MvcResult;

@Ignore(
    "We leave the test Ignored so we can potentially run it manually "
        + "by pasting the bearer token")
public class FieldmarkRealConnectionMVCIT extends API_MVC_TestBase {

  private static final FieldmarkApiImportRequest IMPORT_REQUEST =
      new FieldmarkApiImportRequest("1726126204618-rspace-igsn-demo");
  private static final String ACCESS_TOKEN = "_______PASTE_TOKEN_HERE_________";

  private User user;
  private String apiKey;
  private @Autowired UserConnectionManager userConnectionManager;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    user = createInitAndLoginAnyUser();
    apiKey = createNewApiKeyForUser(user);
    UserConnection actualConnection = new UserConnection();
    actualConnection.setId(
        new UserConnectionId(user.getUsername(), FIELDMARK_APP_NAME, "ProviderUserIdNotNeeded"));
    actualConnection.setAccessToken(ACCESS_TOKEN);
    actualConnection.setRefreshToken("REFRESH_TOKEN");
    actualConnection.setExpireTime(299L);
    actualConnection.setDisplayName("Fieldmark access token");
    userConnectionManager.save(actualConnection);
  }

  @Test
  public void testGetNotebookList() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForInventoryGet(API_VERSION.ONE, apiKey, "fieldmark/notebooks", user))
            .andExpect(status().is(HttpStatus.OK.value()))
            .andReturn();
    assertNotNull(result.getResponse());
  }

  @Test
  public void testImportNotebook() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForInventoryPostWithJSONBody(
                    apiKey, "/import/fieldmark/notebook", user, IMPORT_REQUEST))
            .andExpect(status().isCreated())
            .andReturn();
    assertNotNull(result.getResponse());
    ObjectMapper objectMapper = new ObjectMapper();
    FieldmarkApiImportResult importResult =
        objectMapper.readValue(
            result.getResponse().getContentAsString(), FieldmarkApiImportResult.class);

    ApiContainer container =
        containerApiMgr.getApiContainerIfExists(importResult.getContainerId(), user);
    assertNotNull(container);
    ApiSampleTemplate sampleTemplate =
        sampleApiMgr.getApiSampleTemplateById(importResult.getSampleTemplateId(), user);
    assertNotNull(sampleTemplate);
    for (Long currentSampleId : importResult.getSampleIds()) {
      ApiSample currentSample = sampleApiMgr.getApiSampleById(currentSampleId, user);
      assertNotNull(currentSample);
    }
  }

  @Test
  public void testGetIgsnCandidateFields() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForInventoryGet(
                    API_VERSION.ONE,
                    apiKey,
                    "fieldmark/notebooks/igsnCandidateFields?notebookId="
                        + IMPORT_REQUEST.getNotebookId(),
                    user))
            .andExpect(status().isOk())
            .andReturn();
    assertNotNull(result.getResponse());
  }
}
