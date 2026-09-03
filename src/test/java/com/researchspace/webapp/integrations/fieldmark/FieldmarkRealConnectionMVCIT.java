package com.researchspace.webapp.integrations.fieldmark;

import static com.researchspace.service.IntegrationsHandler.FIELDMARK_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.PROVIDER_USER_ID;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.controller.API_MVC_TestBase;
import com.researchspace.api.v1.controller.API_VERSION;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.fieldmark.model.FieldmarkNotebook;
import com.researchspace.model.User;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import com.researchspace.service.ApiAvailabilityHandler;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.webapp.integrations.datacite.DataCiteConnectorDummy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Runs against the real Fieldmark service when explicitly enabled; requires the FIELDMARK_TOKEN
 * environment variable to hold a valid bearer token.
 */
@EnabledIfSystemProperty(named = "fieldmark.realConnectionTests", matches = "true")
public class FieldmarkRealConnectionMVCIT extends API_MVC_TestBase {

  private static final FieldmarkApiImportRequest IMPORT_REQUEST =
      new FieldmarkApiImportRequest("1726126204618-rspace-igsn-demo");
  private static final String LONG_LIVED_TOKEN = System.getenv("FIELDMARK_TOKEN");

  private User user;
  private String apiKey;
  private @Autowired UserConnectionManager userConnectionManager;
  private @Autowired ApiAvailabilityHandler apiHandler;

  @BeforeEach
  public void setUp() throws Exception {
    assumeTrue(
        LONG_LIVED_TOKEN != null,
        "Skipping: set the FIELDMARK_TOKEN environment variable to run this");
    super.setUp();
    user = createInitAndLoginAnyUser();
    apiKey = createNewApiKeyForUser(user);
    UserConnection actualConnection = new UserConnection();
    actualConnection.setId(
        new UserConnectionId(user.getUsername(), FIELDMARK_APP_NAME, PROVIDER_USER_ID));
    actualConnection.setAccessToken(LONG_LIVED_TOKEN);
    actualConnection.setRefreshToken("REFRESH_TOKEN");
    actualConnection.setExpireTime(299L);
    actualConnection.setDisplayName("Fieldmark access token");
    userConnectionManager.save(actualConnection);

    apiHandler.setDataCiteConnector(new DataCiteConnectorDummy());
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

    FieldmarkNotebook[] notebooks =
        new ObjectMapper()
            .readValue(result.getResponse().getContentAsString(), FieldmarkNotebook[].class);
    assertFalse(notebooks.length == 0, "notebook list is empty");
    for (FieldmarkNotebook notebook : notebooks) {
      String name = notebook.getName();
      assertNotNull(name, "name is null");
      assertNotNull(notebook.getStatus(), "status is null for notebook " + name);
      assertNotNull(notebook.getId(), "id is null for notebook " + name);
      assertNotNull(notebook.getProjectId(), "projectId is null for notebook " + name);
      assertNotNull(notebook.getMetadata(), "metadata is null for notebook " + name);
      // listingId and ui-specification are not asserted: the current Fieldmark API has no
      // listing_id, and its notebook-list response omits the notebook design entirely
    }
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
