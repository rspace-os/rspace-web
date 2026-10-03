package com.researchspace.webapp.controller;

import static com.researchspace.model.preference.Preference.BOX;
import static com.researchspace.model.preference.Preference.DROPBOX;
import static com.researchspace.service.IntegrationsHandler.ARGOS_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.CLUSTERMARKET_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.DATAVERSE_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.DIGITAL_COMMONS_DATA_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.DMPASSISTANT_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.DMPONLINE_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.DMPTOOL_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.DRYAD_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.DSW_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.EGNYTE_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.FIELDMARK_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.FIGSHARE_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.GALAXY_ALIAS;
import static com.researchspace.service.IntegrationsHandler.GALAXY_APIKEY;
import static com.researchspace.service.IntegrationsHandler.GALAXY_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.GALAXY_CONFIGURED_SERVERS;
import static com.researchspace.service.IntegrationsHandler.GITHUB_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.MSTEAMS_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.NEXTCLOUD_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.OMERO_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.ORCID_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.OWNCLOUD_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.PROTOCOLS_IO_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.PYRAT_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.RAID_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.SLACK_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.ZENODO_APP_NAME;
import static com.researchspace.service.raid.impl.RaIDServiceClientAdapterImpl.RAID_CONFIGURED_SERVERS;
import static com.researchspace.webapp.integrations.pyrat.PyratClient.PYRAT_ALIAS;
import static com.researchspace.webapp.integrations.pyrat.PyratClient.PYRAT_APIKEY;
import static com.researchspace.webapp.integrations.pyrat.PyratClient.PYRAT_CONFIGURED_SERVERS;
import static com.researchspace.webapp.integrations.pyrat.PyratClient.PYRAT_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.model.User;
import com.researchspace.model.apps.App;
import com.researchspace.model.apps.UserAppConfig;
import com.researchspace.model.dto.IntegrationInfo;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.preference.BoxLinkType;
import com.researchspace.model.preference.Preference;
import com.researchspace.service.IntegrationsHandler;
import com.researchspace.service.UserAppConfigManager;
import com.researchspace.service.UserConnectionManager;
import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.logging.log4j.util.Strings;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@TestPropertySource(
    properties = {
      "pyrat.server.config={\"mice server\": {\"url\": \"https://pyrat1.server.com\", \"token\":"
          + " \"server1-secret-token\"}, \"frogs server\": {\"url\":"
          + " \"https://pyrat2.server.com\", \"token\": \"server2-secret-token\"}}",
      "raid.server.config={      \"DEMO\": {                \"url\": \"https://demo.raid.au/\", "
          + "                \"authUrl\":"
          + " \"https://demo.raid.org/realms/raid/protocol/openid-connect/\",                "
          + " \"servicePointId\": 12345678,                 \"clientId\": \"rspace\",              "
          + "   \"clientSecret\": \"secretfgubdfigu\"       }}"
    })
public class IntegrationControllerMVCIT extends MVCTestBase {

  final int TOTAL_INTEGRATIONS = 28;
  Principal mockPrincipal = null;

  @Autowired private UserConnectionManager userConnectionManager;
  private @Autowired UserAppConfigManager userAppConfigManager;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
    mockPrincipal = piUser::getUsername;
    logoutAndLoginAs(piUser);
  }

  @AfterEach
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
    assertThat(infos).hasSize(TOTAL_INTEGRATIONS);

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
    expectedOptions.put(EGNYTE_APP_NAME, new String[] {});
    expectedOptions.put(MSTEAMS_APP_NAME, new String[] {});
    expectedOptions.put(PROTOCOLS_IO_APP_NAME, new String[] {}); // no token if not authenticated
    expectedOptions.put(PYRAT_APP_NAME, new String[] {PYRAT_CONFIGURED_SERVERS});
    expectedOptions.put(RAID_APP_NAME, new String[] {RAID_CONFIGURED_SERVERS});
    expectedOptions.put(CLUSTERMARKET_APP_NAME, new String[] {});
    expectedOptions.put(DRYAD_APP_NAME, new String[] {});
    expectedOptions.put(DMPONLINE_APP_NAME, new String[] {});
    expectedOptions.put(DMPTOOL_APP_NAME, new String[] {});
    expectedOptions.put(ARGOS_APP_NAME, new String[] {});
    expectedOptions.put(ZENODO_APP_NAME, new String[] {});
    expectedOptions.put(OMERO_APP_NAME, new String[] {});
    expectedOptions.put(DIGITAL_COMMONS_DATA_APP_NAME, new String[] {});
    expectedOptions.put(FIELDMARK_APP_NAME, new String[] {});
    expectedOptions.put(GALAXY_APP_NAME, new String[] {"GALAXY_CONFIGURED_SERVERS"});
    expectedOptions.put(DSW_APP_NAME, new String[] {});
    expectedOptions.put(DMPASSISTANT_APP_NAME, new String[] {});

    for (var info : infos.values()) {
      String integrationName = (String) info.get("name");
      Map options = (Map) info.get("options");
      assertThat(options)
          .as("For " + integrationName)
          .hasSameSizeAs(expectedOptions.get(integrationName));
      for (String expectedOption : expectedOptions.get(integrationName)) {
        assertThat(options)
            .as(integrationName + " should contain " + expectedOption)
            .containsKey(expectedOption);
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
    assertThat(info.getOptions())
        .containsEntry(Preference.BOX_LINK_TYPE.toString(), BoxLinkType.LIVE.toString());

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
    assertThat(info.getOptions())
        .containsEntry(Preference.BOX_LINK_TYPE.toString(), BoxLinkType.VERSIONED.toString());
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

  @ParameterizedTest
  @ValueSource(strings = {"SLACK", "slack"})
  public void updateSlackIntegration(String postedName) throws Exception {

    logoutAndLoginAs(piUser);

    // retrieve initial state of integration
    String integrationName = SLACK_APP_NAME;
    IntegrationInfo info = getIntegrationInfoFromServer(mockPrincipal, integrationName);
    assertEquals(integrationName, info.getName());
    assertTrue(info.isEnabled()); // slack is enabled by default

    // now disable slack
    info.setEnabled(false);
    info.setName(postedName);
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
    channelOptions.put("SLACK_USER_ID", "U123");
    channelOptions.put("SLACK_TEAM_ID", "T456");
    channelOptions.put("SLACK_CHANNEL_ID", "C789");

    // only Slack's OAuth callback adds a channel, so the API refuses to create one
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
    assertInstanceOf(AuthorizationException.class, result.getResolvedException());
    UserAppConfig cfg =
        userAppConfigManager.saveAppConfigElementSet(
            channelOptions, null, true, piUser, App.APP_SLACK);
    String setId = cfg.getAppConfigElementSets().iterator().next().getId().toString();

    // nor may it change the ids that route slash commands to this user
    Map<String, String> hijack = new HashMap<>(channelOptions);
    hijack.put("SLACK_USER_ID", "U999");
    result =
        mockMvc
            .perform(
                post("/integration/saveAppOptions")
                    .param("optionsId", setId)
                    .param("appName", integrationName)
                    .content(mvcUtils.getAsJsonString(hijack))
                    .contentType(MediaType.APPLICATION_JSON)
                    .principal(mockPrincipal))
            .andReturn();
    assertInstanceOf(AuthorizationException.class, result.getResolvedException());

    // but it can relabel the channel
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
    assertThat(updatedOptions).hasSize(1); // still just one channel
    assertThat(((Map<String, String>) updatedOptions.values().iterator().next()))
        .containsEntry("SLACK_CHANNEL_LABEL", EDITED_LABEL);

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
    assertThat(deletedOptions).isEmpty();
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
    assertThat(savedOptions).hasSize(2);
    List configuredServers = (List) savedOptions.get(PYRAT_CONFIGURED_SERVERS);
    assertThat(configuredServers).hasSize(2);
    assertThat(((Map<String, String>) configuredServers.get(0)))
        .containsEntry("alias", "mice server");
    assertThat(((Map<String, String>) configuredServers.get(0)))
        .containsEntry("url", "https://pyrat1.server.com");
    assertThat(((Map<String, String>) configuredServers.get(1)))
        .containsEntry("alias", "frogs server");
    assertThat(((Map<String, String>) configuredServers.get(1)))
        .containsEntry("url", "https://pyrat2.server.com");

    String optionsSetId = "";
    for (String key : savedOptions.keySet()) {
      if (!key.equals(PYRAT_CONFIGURED_SERVERS)) {
        optionsSetId = key;
      }
    }
    assertThat(optionsSetId).isNotEmpty();

    Map<String, String> uiOptions = (Map<String, String>) savedOptions.get(optionsSetId);
    assertThat(uiOptions).containsEntry(PYRAT_ALIAS, "mice server");
    // verify the apikey is returned as empty in the ui options since it is not saved in clear
    assertThat(uiOptions).containsEntry(PYRAT_APIKEY, Strings.EMPTY);
    // verify the api-key is not saved into UserConnection table (cause it is saved into the
    // AppConfig)
    assertThat(
            userConnectionManager.findByUserNameProviderName(
                piUser.getUsername(), PYRAT_APP_NAME, "mice server"))
        .isEmpty();
    assertThat(uiOptions).containsEntry(PYRAT_URL, "http://pyrat1.server.com");

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
    assertThat(uiOptions).containsEntry(PYRAT_ALIAS, "mice server");
    // make sure the apikey is returned as empty since it is not saved in clear
    assertThat(uiOptions).containsEntry(PYRAT_APIKEY, Strings.EMPTY);
    assertThat(
            userConnectionManager.findByUserNameProviderName(
                piUser.getUsername(), PYRAT_APP_NAME, "mice server"))
        .isPresent();
    assertThat(uiOptions).containsEntry(PYRAT_URL, "http://pyrat1.server.com");

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
    assertThat(deletedOptions).hasSize(1);
    assertThat(deletedOptions).containsKey(PYRAT_CONFIGURED_SERVERS);
    assertThat(
            userConnectionManager.findByUserNameProviderName(
                piUser.getUsername(), PYRAT_APP_NAME, "mice server"))
        .isEmpty();
  }

  @Test
  public void galaxyKeyIsKeptOnlyInTheUserConnection() throws Exception {
    logoutAndLoginAs(piUser);
    Map<String, String> serverOptions = new HashMap<>();
    serverOptions.put(GALAXY_ALIAS, "galaxy eu");
    serverOptions.put(GALAXY_APIKEY, "");
    serverOptions.put("GALAXY_URL", "https://usegalaxy.eu");
    MvcResult added = saveGalaxyOptions(serverOptions, null);
    String optionsSetId =
        getFromJsonAjaxReturnObject(added, IntegrationInfo.class).getOptions().keySet().stream()
            .filter(key -> !key.equals(GALAXY_CONFIGURED_SERVERS))
            .findFirst()
            .orElseThrow();

    serverOptions.put(GALAXY_APIKEY, "galaxy-key");
    MvcResult edited = saveGalaxyOptions(serverOptions, optionsSetId);

    // RSDEV-1525: the key is sent as null (stored, withheld) and kept only in the encrypted
    // connection; the raw body must carry the key, as an absent one would read as "no key"
    assertThat(edited.getResponse().getContentAsString())
        .containsPattern("\"GALAXY_APIKEY\"\\s*:\\s*null");
    Map<String, String> uiOptions =
        (Map<String, String>)
            getFromJsonAjaxReturnObject(edited, IntegrationInfo.class)
                .getOptions()
                .get(optionsSetId);
    assertThat(uiOptions).containsEntry(GALAXY_APIKEY, null);
    assertThat(
            userAppConfigManager
                .getAppConfigElementSetById(Long.valueOf(optionsSetId))
                .getConfigElements())
        .extracting(element -> element.getAppConfigElementDescriptor().getDescriptor().getName())
        .containsExactlyInAnyOrder(GALAXY_ALIAS, "GALAXY_URL");
    assertThat(
            userConnectionManager
                .findByUserNameProviderName(piUser.getUsername(), GALAXY_APP_NAME, "galaxy eu")
                .map(UserConnection::getAccessToken))
        .contains("galaxy-key");

    mockMvc
        .perform(
            post("/integration/deleteAppOptions")
                .param("optionsId", optionsSetId)
                .param("appName", GALAXY_APP_NAME)
                .principal(mockPrincipal))
        .andExpect(status().is2xxSuccessful());
    assertThat(
            userConnectionManager.findByUserNameProviderName(
                piUser.getUsername(), GALAXY_APP_NAME, "galaxy eu"))
        .isEmpty();
  }

  private MvcResult saveGalaxyOptions(Map<String, String> options, String optionsId)
      throws Exception {
    MockHttpServletRequestBuilder request =
        post("/integration/saveAppOptions")
            .param("appName", GALAXY_APP_NAME)
            .content(mvcUtils.getAsJsonString(options))
            .contentType(MediaType.APPLICATION_JSON)
            .principal(mockPrincipal);
    if (optionsId != null) {
      request.param("optionsId", optionsId);
    }
    MvcResult result = mockMvc.perform(request).andExpect(status().is2xxSuccessful()).andReturn();
    assertNull(result.getResolvedException());
    return result;
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
    assertThat(elements).hasSize(2);
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
