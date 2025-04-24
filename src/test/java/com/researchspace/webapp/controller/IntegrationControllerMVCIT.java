package com.researchspace.webapp.controller;

import static com.researchspace.model.preference.Preference.BOX;
import static com.researchspace.model.preference.Preference.DROPBOX;
import static com.researchspace.service.IntegrationsHandler.ARGOS_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.CLUSTERMARKET_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.DATAVERSE_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.DIGITAL_COMMONS_DATA_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.DMPONLINE_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.DMPTOOL_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.DRYAD_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.EGNYTE_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.EVERNOTE_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.FIELDMARK_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.FIGSHARE_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.GALAXY_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.GITHUB_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.JOVE_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.MSTEAMS_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.NEXTCLOUD_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.OMERO_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.ORCID_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.OWNCLOUD_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.PROTOCOLS_IO_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.PYRAT_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.SLACK_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.ZENODO_APP_NAME;
import static com.researchspace.webapp.integrations.pyrat.PyratClient.PYRAT_ALIAS;
import static com.researchspace.webapp.integrations.pyrat.PyratClient.PYRAT_APIKEY;
import static com.researchspace.webapp.integrations.pyrat.PyratClient.PYRAT_CONFIGURED_SERVERS;
import static com.researchspace.webapp.integrations.pyrat.PyratClient.PYRAT_URL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.model.User;
import com.researchspace.model.dto.IntegrationInfo;
import com.researchspace.model.preference.BoxLinkType;
import com.researchspace.model.preference.Preference;
import com.researchspace.service.IntegrationsHandler;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.service.impl.ConditionalTestRunner;
import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

@RunWith(ConditionalTestRunner.class)
@TestPropertySource(
    properties =
        "pyrat.server.config={\"mice server\": {\"url\": \"https://pyrat1.server.com\", \"token\":"
            + " \"server1-secret-token\"}, \"frogs server\": {\"url\":"
            + " \"https://pyrat2.server.com\", \"token\": \"server2-secret-token\"}}")
public class IntegrationControllerMVCIT extends MVCTestBase {

  final int TOTAL_INTEGRATIONS = 27;
  Principal mockPrincipal = null;

  @Autowired private UserConnectionManager userConnectionManager;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    mockPrincipal = piUser::getUsername;
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void getAllIntegrations() throws Exception {

    logoutAndLoginAs(piUser);

    MvcResult resultAll =
        mockMvc
            .perform(get("/integration/allIntegrations").principal(mockPrincipal))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    Map<String, Map<String, Object>> infos = getFromJsonAjaxReturnObject(resultAll, Map.class);
    assertEquals(TOTAL_INTEGRATIONS, infos.size());

    // check options
    Map<String, String[]> expectedOptions = new TreeMap<>();
    expectedOptions.put(
        "BOX", new String[] {"box.linking.enabled", "box.api.enabled", "BOX_LINK_TYPE"});
    expectedOptions.put("DROPBOX", new String[] {"dropbox.linking.enabled"});
    expectedOptions.put("GOOGLEDRIVE", new String[] {"googledrive.linking.enabled"});
    expectedOptions.put("ONEDRIVE", new String[] {"onedrive.linking.enabled"});
    expectedOptions.put("CHEMISTRY", new String[] {});
    expectedOptions.put(SLACK_APP_NAME, new String[] {});
    expectedOptions.put(DATAVERSE_APP_NAME, new String[] {});
    expectedOptions.put(GITHUB_APP_NAME, new String[] {});
    expectedOptions.put(FIGSHARE_APP_NAME, new String[] {});
    expectedOptions.put(OWNCLOUD_APP_NAME, new String[] {});
    expectedOptions.put(NEXTCLOUD_APP_NAME, new String[] {});
    expectedOptions.put(EVERNOTE_APP_NAME, new String[] {});
    expectedOptions.put(EGNYTE_APP_NAME, new String[] {});
    expectedOptions.put(MSTEAMS_APP_NAME, new String[] {});
    expectedOptions.put(PROTOCOLS_IO_APP_NAME, new String[] {}); // no token if not authenticated
    expectedOptions.put(PYRAT_APP_NAME, new String[] {PYRAT_CONFIGURED_SERVERS});
    expectedOptions.put(CLUSTERMARKET_APP_NAME, new String[] {});
    expectedOptions.put(DRYAD_APP_NAME, new String[] {});
    expectedOptions.put(JOVE_APP_NAME, new String[] {});
    expectedOptions.put(DMPONLINE_APP_NAME, new String[] {});
    expectedOptions.put(DMPTOOL_APP_NAME, new String[] {});
    expectedOptions.put(ARGOS_APP_NAME, new String[] {});
    expectedOptions.put(ZENODO_APP_NAME, new String[] {});
    expectedOptions.put(OMERO_APP_NAME, new String[] {});
    expectedOptions.put(DIGITAL_COMMONS_DATA_APP_NAME, new String[] {});
    expectedOptions.put(FIELDMARK_APP_NAME, new String[] {});
    expectedOptions.put(GALAXY_APP_NAME, new String[] {});

    for (var info : infos.values()) {
      String integrationName = (String) info.get("name");
      Map options = (Map) info.get("options");
      assertEquals(
          "For " + integrationName, expectedOptions.get(integrationName).length, options.size());
      for (String expectedOption : expectedOptions.get(integrationName)) {
        assertTrue(
            integrationName + " should contain " + expectedOption,
            options.containsKey(expectedOption));
      }
    }
  }

  @Test
  public void updateDropboxEnablement() throws Exception {
    logoutAndLoginAs(piUser);

    String integrationName = Preference.DROPBOX.toString();
    IntegrationInfo info = getIntegrationInfoFromServer(mockPrincipal, integrationName);
    assertEquals(integrationName, info.getName());
    assertFalse(info.isEnabled());

    // now enable dropbox
    info.setEnabled(true);
    String json = mvcUtils.getAsJsonString(info);

    MvcResult updateResult =
        mockMvc
            .perform(
                post("/integration/update")
                    .content(json)
                    .contentType(MediaType.APPLICATION_JSON)
                    .principal(mockPrincipal))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    info = getFromJsonAjaxReturnObject(updateResult, IntegrationInfo.class);
    assertEquals(integrationName, info.getName());
    assertTrue(info.isEnabled());

    User anotheruser = createInitAndLoginAnyUser();
    logoutAndLoginAs(anotheruser); // should still be disabled for other users
    info = getIntegrationInfoFromServer(anotheruser::getUsername, integrationName);
    assertFalse(info.isEnabled());
  }

  @Test
  public void updateBoxIntegration() throws Exception {
    Principal mock = piUser::getUsername;
    logoutAndLoginAs(piUser);

    String integrationName = Preference.BOX.toString();
    IntegrationInfo info = getIntegrationInfoFromServer(mock, integrationName);
    assertEquals(integrationName, info.getName());
    assertFalse(info.isEnabled());
    assertEquals(
        BoxLinkType.LIVE.toString(), info.getOptions().get(Preference.BOX_LINK_TYPE.toString()));

    // set enabled
    info.setEnabled(true);

    // try updating with incorrect link type
    Map<String, Object> options = new HashMap<String, Object>();
    options.put("BOX_LINK_TYPE", "asdf");
    info.setOptions(options);
    String json = mvcUtils.getAsJsonString(info);

    MvcResult invalidLinkTypeResult =
        mockMvc
            .perform(
                post("/integration/update")
                    .content(json)
                    .contentType(MediaType.APPLICATION_JSON)
                    .principal(mock))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    assertNotNull(invalidLinkTypeResult.getResolvedException());

    // now update with correct link type
    options.put("BOX_LINK_TYPE", BoxLinkType.VERSIONED.toString());
    json = mvcUtils.getAsJsonString(info);

    MvcResult updateResult =
        mockMvc
            .perform(
                post("/integration/update")
                    .content(json)
                    .contentType(MediaType.APPLICATION_JSON)
                    .principal(mock))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    info = getFromJsonAjaxReturnObject(updateResult, IntegrationInfo.class);
    assertEquals(integrationName, info.getName());
    assertTrue(info.isEnabled());
    assertEquals(
        BoxLinkType.VERSIONED.toString(),
        info.getOptions().get(Preference.BOX_LINK_TYPE.toString()));
  }

  @Test
  public void owncloudIntegration() throws Exception {
    IntegrationInfo info =
        getIntegrationInfoFromServer(mockPrincipal, IntegrationsHandler.OWNCLOUD_APP_NAME);
    assertTrue(info.isAvailable());
    assertFalse(info.isEnabled());
  }

  @Test
  public void nextcloudIntegration() throws Exception {
    IntegrationInfo info = getIntegrationInfoFromServer(mockPrincipal, NEXTCLOUD_APP_NAME);
    assertFalse(info.isAvailable());
    assertFalse(info.isEnabled());
  }

  @Test
  public void updateSlackIntegration() throws Exception {

    logoutAndLoginAs(piUser);

    // retrieve initial state of integration
    String integrationName = SLACK_APP_NAME;
    IntegrationInfo info = getIntegrationInfoFromServer(mockPrincipal, integrationName);
    assertEquals(integrationName, info.getName());
    assertTrue(info.isEnabled()); // slack is enabled by default

    // now disable slack
    info.setEnabled(false);
    String integrationInfoJson = mvcUtils.getAsJsonString(info);

    MvcResult result =
        mockMvc
            .perform(
                post("/integration/update")
                    .content(integrationInfoJson)
                    .contentType(MediaType.APPLICATION_JSON)
                    .principal(mockPrincipal))
            .andReturn();
    assertNull(result.getResolvedException());

    // check result updated fine
    info = getIntegrationInfoFromServer(mockPrincipal, integrationName);
    assertEquals(integrationName, info.getName());
    assertFalse(info.isEnabled()); // app is disabled now
  }

  @Test
  public void addEditDeleteSlackChannel() throws Exception {

    String integrationName = SLACK_APP_NAME;
    logoutAndLoginAs(piUser);

    String INITIAL_LABEL = "initialSlackLabel";
    String EDITED_LABEL = "editedSlackLabel";

    // create option set for slack channel
    Map<String, String> channelOptions = new HashMap<>();
    channelOptions.put("SLACK_TEAM_NAME", "testTeamName");
    channelOptions.put("SLACK_CHANNEL_NAME", "testChannelName");
    channelOptions.put("SLACK_CHANNEL_LABEL", INITIAL_LABEL);
    channelOptions.put("SLACK_WEBHOOK_URL", "testWebhookUrl");
    channelOptions.put("SLACK_USER_ID", "U123");
    channelOptions.put("SLACK_TEAM_ID", "T456");
    channelOptions.put("SLACK_CHANNEL_ID", "C789");
    channelOptions.put("SLACK_USER_ACCESS_TOKEN", "xoxp-123456789");

    String optionsJson = mvcUtils.getAsJsonString(channelOptions);
    MvcResult result =
        mockMvc
            .perform(
                post("/integration/saveAppOptions")
                    .param("appName", integrationName)
                    .content(optionsJson)
                    .contentType(MediaType.APPLICATION_JSON)
                    .principal(mockPrincipal))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    assertNull(result.getResolvedException());

    // verify that slack channel is created
    IntegrationInfo info = getFromJsonAjaxReturnObject(result, IntegrationInfo.class);
    Map<String, Object> savedOptions = info.getOptions();
    assertEquals(1, savedOptions.size()); // the channel was added
    String setId = savedOptions.keySet().iterator().next();
    assertTrue(StringUtils.isNotEmpty(setId));
    assertEquals(
        INITIAL_LABEL,
        ((Map<String, String>) savedOptions.values().iterator().next()).get("SLACK_CHANNEL_LABEL"));

    // now try saving the same slack channel with different label
    channelOptions.put("SLACK_CHANNEL_LABEL", EDITED_LABEL);
    optionsJson = mvcUtils.getAsJsonString(channelOptions);
    result =
        mockMvc
            .perform(
                post("/integration/saveAppOptions")
                    .param("optionsId", setId)
                    .param("appName", integrationName)
                    .content(optionsJson)
                    .contentType(MediaType.APPLICATION_JSON)
                    .principal(mockPrincipal))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    assertNull(result.getResolvedException());

    // verify that label was updated
    IntegrationInfo updatedInfo = getFromJsonAjaxReturnObject(result, IntegrationInfo.class);
    Map<String, Object> updatedOptions = updatedInfo.getOptions();
    assertEquals(1, updatedOptions.size()); // still just one channel
    assertEquals(
        EDITED_LABEL,
        ((Map<String, String>) updatedOptions.values().iterator().next())
            .get("SLACK_CHANNEL_LABEL"));

    // now delete
    result =
        mockMvc
            .perform(
                post("/integration/deleteAppOptions")
                    .param("optionsId", setId)
                    .param("appName", integrationName)
                    .principal(mockPrincipal))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    assertNull(result.getResolvedException());

    // verify the channel is no longer there
    IntegrationInfo deletedChannelInfo = getFromJsonAjaxReturnObject(result, IntegrationInfo.class);
    Map<String, Object> deletedOptions = deletedChannelInfo.getOptions();
    assertEquals(0, deletedOptions.size());
  }

  @Test
  public void addEditDeletePyratOptions() throws Exception {
    String integrationName = PYRAT_APP_NAME;
    logoutAndLoginAs(piUser);

    // add a server url to the configuration
    Map<String, String> channelOptions = new HashMap<>();
    channelOptions.put(PYRAT_ALIAS, "mice server");
    channelOptions.put(PYRAT_APIKEY, "");
    channelOptions.put(PYRAT_URL, "http://pyrat1.server.com");

    String optionsJson = mvcUtils.getAsJsonString(channelOptions);
    MvcResult result =
        mockMvc
            .perform(
                post("/integration/saveAppOptions")
                    .param("appName", integrationName)
                    .content(optionsJson)
                    .contentType(MediaType.APPLICATION_JSON)
                    .principal(mockPrincipal))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    assertNull(result.getResolvedException());

    // verify options are returned correctly
    IntegrationInfo info = getFromJsonAjaxReturnObject(result, IntegrationInfo.class);
    Map<String, Object> savedOptions = info.getOptions();
    assertEquals(2, savedOptions.size());
    List configuredServers = (List) savedOptions.get(PYRAT_CONFIGURED_SERVERS);
    assertEquals(2, configuredServers.size());
    assertEquals("mice server", ((Map<String, String>) configuredServers.get(0)).get("alias"));
    assertEquals(
        "https://pyrat1.server.com", ((Map<String, String>) configuredServers.get(0)).get("url"));
    assertEquals("frogs server", ((Map<String, String>) configuredServers.get(1)).get("alias"));
    assertEquals(
        "https://pyrat2.server.com", ((Map<String, String>) configuredServers.get(1)).get("url"));

    String optionsSetId = "";
    for (String key : savedOptions.keySet()) {
      if (!key.equals(PYRAT_CONFIGURED_SERVERS)) {
        optionsSetId = key;
      }
    }
    assertTrue(StringUtils.isNotEmpty(optionsSetId));

    Map<String, String> uiOptions = (Map<String, String>) savedOptions.get(optionsSetId);
    assertEquals("mice server", uiOptions.get(PYRAT_ALIAS));
    // verify the apikey is returned as empty in the ui options since it is not saved in clear
    assertEquals(Strings.EMPTY, uiOptions.get(PYRAT_APIKEY));
    // verify the api-key is instead saved into the UserConnection table
    assertTrue(
        userConnectionManager
            .findByUserNameProviderName(piUser.getUsername(), PYRAT_APP_NAME, "mice server")
            .isEmpty());
    assertEquals("http://pyrat1.server.com", uiOptions.get(PYRAT_URL));

    // add a server api-key to the configuration
    channelOptions = new HashMap<>();
    channelOptions.put(PYRAT_ALIAS, "mice server");
    channelOptions.put(PYRAT_APIKEY, "api-key-1-mice-server");
    channelOptions.put(PYRAT_URL, "http://pyrat1.server.com");

    optionsJson = mvcUtils.getAsJsonString(channelOptions);
    result =
        mockMvc
            .perform(
                post("/integration/saveAppOptions")
                    .param("appName", integrationName)
                    .param("optionsId", optionsSetId)
                    .content(optionsJson)
                    .contentType(MediaType.APPLICATION_JSON)
                    .principal(mockPrincipal))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    assertNull(result.getResolvedException());

    uiOptions = (Map<String, String>) savedOptions.get(optionsSetId);
    assertEquals("mice server", uiOptions.get(PYRAT_ALIAS));
    // make sure the apikey is returned as empty since it is not saved in clear
    assertEquals(Strings.EMPTY, uiOptions.get(PYRAT_APIKEY));
    assertEquals(
        "api-key-1-mice-server",
        userConnectionManager
            .findByUserNameProviderName(piUser.getUsername(), PYRAT_APP_NAME, "mice server")
            .get()
            .getAccessToken());
    assertEquals("http://pyrat1.server.com", uiOptions.get(PYRAT_URL));

    // delete the user configuration
    result =
        mockMvc
            .perform(
                post("/integration/deleteAppOptions")
                    .param("optionsId", optionsSetId)
                    .param("appName", integrationName)
                    .principal(mockPrincipal))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    assertNull(result.getResolvedException());

    // verify the ui options and the api-key are not anymore there
    IntegrationInfo deletedChannelInfo = getFromJsonAjaxReturnObject(result, IntegrationInfo.class);
    Map<String, Object> deletedOptions = deletedChannelInfo.getOptions();
    assertEquals(1, deletedOptions.size());
    assertTrue(deletedOptions.containsKey(PYRAT_CONFIGURED_SERVERS));
    assertTrue(
        userConnectionManager
            .findByUserNameProviderName(piUser.getUsername(), PYRAT_APP_NAME, "mice server")
            .isEmpty());
  }

  @Test
  public void directOrcidIdUpdateForbidden() throws Exception {

    String integrationName = ORCID_APP_NAME;
    logoutAndLoginAs(piUser);

    // create option set for orcid app
    Map<String, String> channelOptions = new HashMap<>();
    channelOptions.put("ORCID_ID", "testId");

    String optionsJson = mvcUtils.getAsJsonString(channelOptions);
    MvcResult result =
        mockMvc
            .perform(
                post("/integration/saveAppOptions")
                    .param("appName", integrationName)
                    .content(optionsJson)
                    .contentType(MediaType.APPLICATION_JSON)
                    .principal(mockPrincipal))
            .andReturn();
    assertNotNull(result.getResolvedException());
    assertEquals("This App cannot be updated this way", result.getResolvedException().getMessage());
  }

  @Test
  public void testGetIntegrations() throws Exception {
    logoutAndLoginAs(piUser);
    MvcResult result =
        mockMvc
            .perform(
                get("/integration/integrationInfos")
                    .param("name[]", BOX.name(), DROPBOX.name())
                    .principal(mockPrincipal))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    List elements = getFromJsonAjaxReturnObject(result, List.class);
    assertEquals(2, elements.size());
  }

  private IntegrationInfo getIntegrationInfoFromServer(Principal mock, String integrationName)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/integration/integrationInfo").param("name", integrationName).principal(mock))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    return getFromJsonAjaxReturnObject(result, IntegrationInfo.class);
  }
}
