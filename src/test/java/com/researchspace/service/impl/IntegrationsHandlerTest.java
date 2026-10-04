package com.researchspace.service.impl;

import static com.researchspace.service.IntegrationsHandler.ACCESS_TOKEN_SETTING;
import static com.researchspace.service.IntegrationsHandler.DATAVERSE_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.DIGITAL_COMMONS_DATA_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.DIGITAL_COMMONS_DATA_USER_TOKEN;
import static com.researchspace.service.IntegrationsHandler.DMPASSISTANT_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.DSW_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.EGNYTE_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.EGNYTE_DOMAIN_SETTING;
import static com.researchspace.service.IntegrationsHandler.GITHUB_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.OMERO_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.ONBOARDING_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.PYRAT_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.SLACK_APP_NAME;
import static com.researchspace.service.SystemPropertyName.BOX_AVAILABLE;
import static com.researchspace.service.SystemPropertyName.DIGITAL_COMMON_DATA_AVAILABLE;
import static com.researchspace.service.SystemPropertyName.DMPASSISTANT_AVAILABLE;
import static com.researchspace.service.SystemPropertyName.DROPBOX_AVAILABLE;
import static com.researchspace.service.SystemPropertyName.OMERO_AVAILABLE;
import static com.researchspace.service.SystemPropertyName.PYRAT_AVAILABLE;
import static com.researchspace.service.SystemPropertyName.SLACK_AVAILABLE;
import static com.researchspace.testutils.SystemPropertyTestFactory.createPermissionEnumSystemProperty;
import static com.researchspace.webapp.integrations.dsw.DSWClient.DSW_ALIAS;
import static com.researchspace.webapp.integrations.dsw.DSWClient.DSW_APIKEY;
import static com.researchspace.webapp.integrations.dsw.DSWClient.DSW_URL;
import static com.researchspace.webapp.integrations.pyrat.PyratClient.PYRAT_ALIAS;
import static com.researchspace.webapp.integrations.pyrat.PyratClient.PYRAT_APIKEY;
import static com.researchspace.webapp.integrations.pyrat.PyratClient.PYRAT_CONFIGURED_SERVERS;
import static com.researchspace.webapp.integrations.pyrat.PyratClient.PYRAT_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.model.PropertyDescriptor;
import com.researchspace.model.User;
import com.researchspace.model.UserPreference;
import com.researchspace.model.apps.App;
import com.researchspace.model.apps.AppConfigElement;
import com.researchspace.model.apps.AppConfigElementDescriptor;
import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.model.apps.UserAppConfig;
import com.researchspace.model.dto.IntegrationInfo;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import com.researchspace.model.preference.Preference;
import com.researchspace.model.preference.SettingsType;
import com.researchspace.model.system.SystemProperty;
import com.researchspace.model.system.SystemPropertyValue;
import com.researchspace.service.CommunityServiceManager;
import com.researchspace.service.IRepositoryConfigFactory;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.SystemPropertyManager;
import com.researchspace.service.SystemPropertyName;
import com.researchspace.service.SystemPropertyPermissionManager;
import com.researchspace.service.UserAppConfigManager;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.service.UserManager;
import com.researchspace.testutils.SystemPropertyTestFactory;
import com.researchspace.testutils.TestFactory;
import com.researchspace.webapp.integrations.ServerConfigurationDTO;
import com.researchspace.webapp.integrations.pyrat.PyratClient;
import com.researchspace.webapp.integrations.pyrat.PyratServerConfigurationDTO;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class IntegrationsHandlerTest {

  @Mock private UserManager userMgr;
  @Mock private SystemPropertyManager sysPropMgr;
  @Mock private SystemPropertyPermissionManager systemPropertyPermissionUtils;
  @Mock private UserAppConfigManager appCfgMgr;
  @Mock private CommunityServiceManager communityMgr;
  @Mock private UserConnectionManager userConnectionManager;
  @Mock private PyratClient pyratClient;
  @Mock private IRepositoryConfigFactory repositoryConfigFactory;
  @InjectMocks private IntegrationsHandlerImpl handler;

  private User subject;

  @BeforeEach
  public void setup() {
    subject = TestFactory.createAnyUser("any");
    handler.setUserConnectionManager(userConnectionManager);
    ReflectionTestUtils.setField(
        handler, "messages", new MessageSourceUtils(new JsonMessageSource()));
  }

  @Test
  public void init() throws Exception {
    List<SystemProperty> parentAndChild = new ArrayList<>();
    SystemProperty parent = createSystemPropertyForName("box.available").getProperty();
    SystemProperty child = createSystemPropertyForName("box.linking.available").getProperty();
    parentAndChild.add(parent);
    parentAndChild.add(child);
    child.setDependent(parent);
    assertParentChildMapSetupOK(parentAndChild, parent, child);
    // reverse order, check is OK
    parentAndChild.set(0, child);
    parentAndChild.set(1, parent);
    assertParentChildMapSetupOK(parentAndChild, parent, child);
  }

  private void assertParentChildMapSetupOK(
      List<SystemProperty> parentAndChild, SystemProperty parent, SystemProperty child) {
    Map<SystemProperty, List<SystemProperty>> parent2child;
    when(sysPropMgr.listSystemPropertyDefinitions()).thenReturn(parentAndChild);
    handler.init();
    parent2child = handler.getParent2ChildMap();
    assertThat(parent2child.get(parent)).hasSize(1);
    assertEquals(child, parent2child.get(parent).get(0));
    assertNull(parent2child.get(child));
  }

  @Test
  public void deletingASlackChannelDeletesItsConnection() {
    stubSetForApp(7L, App.APP_SLACK);
    handler.deleteAppOptions(7L, SLACK_APP_NAME, subject);
    verify(appCfgMgr).deleteAppConfigSet(7L, subject, App.APP_SLACK);
    verify(userConnectionManager)
        .deleteByUserAndProvider(subject.getUsername(), SLACK_APP_NAME, "7");
  }

  @Test
  public void deletingOneOfSeveralGitHubRepositoriesKeepsTheToken() {
    stubSetForApp(7L, App.APP_GITHUB);
    when(appCfgMgr.getByAppName(App.APP_GITHUB, subject))
        .thenReturn(
            SystemPropertyTestFactory.createAnyAppWithConfigElements(subject, App.APP_GITHUB));
    handler.deleteAppOptions(7L, GITHUB_APP_NAME, subject);
    verify(userConnectionManager)
        .deleteByUserAndProvider(subject.getUsername(), GITHUB_APP_NAME, "7");
    verify(userConnectionManager, never()).deleteByUserAndProvider(any(), any());
  }

  @Test
  public void addingAGitHubRepositoryBindsTheCurrentTokenAtomically() {
    String currentToken = "github-oauth-token";
    Map<String, String> settings = Map.of("GITHUB_REPOSITORY_FULL_NAME", "owner/repo");
    when(userConnectionManager.findByUserNameProviderName(
            subject.getUsername(), GITHUB_APP_NAME, GITHUB_APP_NAME))
        .thenReturn(
            Optional.of(
                new UserConnection(
                    new UserConnectionId(subject.getUsername(), GITHUB_APP_NAME, GITHUB_APP_NAME),
                    currentToken)));

    handler.saveAppOptions(null, settings, GITHUB_APP_NAME, false, subject);

    verify(userConnectionManager)
        .saveWithNewAppConfigElementSet(
            settings, GITHUB_APP_NAME, currentToken, null, false, subject);
  }

  @Test
  public void addingAGitHubRepositoryWithoutOAuthTokenDoesNotSaveTheConfig() {
    Map<String, String> settings = Map.of("GITHUB_REPOSITORY_FULL_NAME", "owner/repo");
    when(userConnectionManager.findByUserNameProviderName(
            subject.getUsername(), GITHUB_APP_NAME, GITHUB_APP_NAME))
        .thenReturn(Optional.empty());

    assertThrows(
        IllegalStateException.class,
        () -> handler.saveAppOptions(null, settings, GITHUB_APP_NAME, false, subject));

    verify(userConnectionManager, never())
        .saveWithNewAppConfigElementSet(any(), any(), any(), any(), anyBoolean(), any());
    verify(appCfgMgr, never())
        .saveAppConfigElementSet(any(), any(), anyBoolean(), any(), anyString());
  }

  private UserAppConfig stubSetForApp(Long setId, String appName) {
    UserAppConfig cfg = SystemPropertyTestFactory.createAnyAppWithConfigElements(subject, appName);
    when(appCfgMgr.deleteAppConfigSet(setId, subject, appName))
        .thenReturn(cfg.getAppConfigElementSets().iterator().next());
    return cfg;
  }

  @Test
  public void isValidIntegration() {
    assertTrue(handler.isValidIntegration("OneDrive"));
    assertTrue(handler.isValidIntegration(Preference.DROPBOX.name()));
    // this is not an integration, but another preference
    assertFalse(handler.isValidIntegration(Preference.BROADCAST_NOTIFICATIONS_BY_EMAIL.name()));
    assertFalse(handler.isValidIntegration("xyz")); // invalid preference handled gracefully
  }

  @Test
  public void getForPropertyThrowsIAEIfUnknownProperty() {
    assertThrows(IllegalArgumentException.class, () -> handler.getIntegration(subject, "unknown"));
  }

  private SystemPropertyValue createSystemPropertyForName(String prefName) {
    SystemProperty sp = SystemPropertyTestFactory.createASystemProperty();
    String propName = prefName.toLowerCase() + ".enabled";
    sp.getDescriptor().setName(propName);
    SystemPropertyValue rc = new SystemPropertyValue(sp, "true");
    return rc;
  }

  @Test
  public void getForPropertyHappyCase() {
    String propName = "DROPBOX";
    SystemPropertyName systemPropertyName = DROPBOX_AVAILABLE;
    SystemPropertyValue dropboxAvailable = getSystemPropertyValueAllowed(systemPropertyName);
    UserPreference dropboxEnabled = new UserPreference(Preference.DROPBOX, subject, "true");

    when(systemPropertyPermissionUtils.isPropertyAllowed(
            eq(subject), eq(systemPropertyName.getPropertyName())))
        .thenReturn(true);
    when(userMgr.getPreferenceForUser(any(), any())).thenReturn(dropboxEnabled);
    when(sysPropMgr.findByName(eq(systemPropertyName))).thenReturn(dropboxAvailable);

    IntegrationInfo info = handler.getIntegration(subject, propName);
    assertTrue(info.isAvailable());
    assertTrue(info.isEnabled());
    assertEquals(propName, info.getName());
    assertNotNull(info.getOptions());
    assertThat(info.getOptions()).isEmpty();

    UserPreference dropboxDisabled = new UserPreference(Preference.DROPBOX, subject, "false");
    when(userMgr.getPreferenceForUser(subject, Preference.DROPBOX)).thenReturn(dropboxDisabled);
    info = handler.getIntegration(subject, propName);
    assertTrue(info.isAvailable());
    assertFalse(info.isEnabled());

    when(systemPropertyPermissionUtils.isPropertyAllowed(
            eq(subject), eq(systemPropertyName.getPropertyName())))
        .thenReturn(false);
    info = handler.getIntegration(subject, propName);
    assertFalse(info.isAvailable());
  }

  @Test
  public void testUpdateOK() {
    IntegrationInfo info = new IntegrationInfo();
    info.setAvailable(true);
    info.setEnabled(false);
    info.setName(Preference.DROPBOX.name());

    UserPreference expectedDropboxPref = new UserPreference(Preference.DROPBOX, subject, "false");
    SystemPropertyValue expectedDropboxAvailableProp =
        new SystemPropertyValue(new SystemProperty(null), "true");
    when(userMgr.getPreferenceForUser(subject, Preference.DROPBOX)).thenReturn(expectedDropboxPref);
    when(sysPropMgr.findByName(DROPBOX_AVAILABLE)).thenReturn(expectedDropboxAvailableProp);

    assertNotNull(handler.updateIntegrationInfo(subject, info));
  }

  @Test
  public void testUpdateBoxProperty() {
    IntegrationInfo info = new IntegrationInfo();
    info.setAvailable(true);
    info.setEnabled(false);
    info.setName(Preference.BOX.name());

    Map<String, Object> options = new HashMap<>();
    options.put(Preference.BOX_LINK_TYPE.toString(), "VERSIONED");
    info.setOptions(options);

    UserPreference expectedBoxLinkTypePref =
        new UserPreference(Preference.BOX_LINK_TYPE, subject, "VERSIONED");
    UserPreference expectedBoxPref = new UserPreference(Preference.BOX, subject, "false");
    SystemPropertyValue expectedBoxAvailableProp =
        new SystemPropertyValue(new SystemProperty(null), "true");

    when(userMgr.getPreferenceForUser(subject, Preference.BOX_LINK_TYPE))
        .thenReturn(expectedBoxLinkTypePref);
    when(userMgr.getPreferenceForUser(subject, Preference.BOX)).thenReturn(expectedBoxPref);
    when(sysPropMgr.findByName(BOX_AVAILABLE)).thenReturn(expectedBoxAvailableProp);

    handler.updateIntegrationInfo(subject, info);

    Mockito.verify(userMgr, times(1))
        .setPreference(Preference.BOX, info.isEnabled() + "", subject.getUsername());
    Mockito.verify(userMgr, times(1))
        .setPreference(Preference.BOX_LINK_TYPE, "VERSIONED", subject.getUsername());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void appSettingsAreWithheldUnlessListedAsClientReadable() {
    AppConfigElementSet set = new AppConfigElementSet();
    set.setConfigElements(
        Set.of(
            new AppConfigElement(
                new AppConfigElementDescriptor(
                    new PropertyDescriptor("DATAVERSE_URL", SettingsType.STRING, "")),
                "https://dataverse.example"),
            new AppConfigElement(
                new AppConfigElementDescriptor(
                    new PropertyDescriptor("DATAVERSE_APIKEY", SettingsType.STRING, "")),
                "dataverse-secret"),
            new AppConfigElement(
                new AppConfigElementDescriptor(
                    new PropertyDescriptor("DATAVERSE_UNLISTED", SettingsType.STRING, "")),
                "unlisted-value")));
    UserAppConfig cfg =
        new UserAppConfig(subject, new App("app.dataverse", "Dataverse", true), true);
    cfg.addConfigSet(set);
    when(appCfgMgr.getByAppName("app.dataverse", subject)).thenReturn(cfg);

    IntegrationInfo info = handler.getIntegration(subject, DATAVERSE_APP_NAME);

    Map<String, String> options =
        (Map<String, String>) info.getOptions().values().iterator().next();
    assertThat(options).containsEntry("DATAVERSE_APIKEY", null);
    assertThat(options).containsEntry("DATAVERSE_UNLISTED", null); // fail-closed for new settings
    assertEquals("https://dataverse.example", options.get("DATAVERSE_URL"));
  }

  @Test
  public void testUpdateSlackApp() {

    // set slack as available
    SystemPropertyValue slackAvailable = getSystemPropertyValueAllowed(SLACK_AVAILABLE);
    App app = new App(SLACK_APP_NAME, "Slack", true);
    UserAppConfig slackConfig = new UserAppConfig(subject, app, false);
    when(appCfgMgr.getByAppName("app.slack", subject)).thenReturn(slackConfig);

    // create new integration info
    IntegrationInfo info = new IntegrationInfo();
    info.setAvailable(true);
    info.setName(SLACK_APP_NAME);

    // mark it as enabled
    info.setEnabled(true);

    // save
    handler.updateIntegrationInfo(subject, info);
    assertTrue(slackConfig.isEnabled()); // handler should update slackConfig to enable the app
    Mockito.verify(appCfgMgr, times(1)).save(slackConfig);
  }

  @Test
  public void testUpdateEgnyteAppConfigOption() {
    IntegrationInfo info = new IntegrationInfo();
    info.setAvailable(true);
    info.setName(EGNYTE_APP_NAME);

    String testEgnyteDomain = "url://egnyte_domain";
    Map<String, Object> options = new HashMap<>();
    options.put(EGNYTE_DOMAIN_SETTING, testEgnyteDomain);
    options.put("unknown_option", "unknown_value");
    info.setOptions(options);

    // mock egnyte app in the system
    App app = new App(EGNYTE_APP_NAME, "Egnyte", false);
    UserAppConfig egnyteConfig = new UserAppConfig(subject, app, false);
    when(appCfgMgr.getByAppName("app.egnyte", subject)).thenReturn(egnyteConfig);

    handler.updateIntegrationInfo(subject, info);

    Map<String, String> expectedOptions = new HashMap<>();
    expectedOptions.put(EGNYTE_DOMAIN_SETTING, testEgnyteDomain);
    Mockito.verify(appCfgMgr, times(1))
        .saveAppConfigElementSet(expectedOptions, null, false, subject, "app.egnyte");
  }

  @Test
  public void enablingEgnyteWithoutADomainLeavesItsOptionsAlone() {
    IntegrationInfo info = new IntegrationInfo();
    info.setAvailable(true);
    info.setEnabled(true);
    info.setName(EGNYTE_APP_NAME);
    UserAppConfig egnyteConfig =
        new UserAppConfig(subject, new App(EGNYTE_APP_NAME, "Egnyte", false), false);
    when(appCfgMgr.getByAppName("app.egnyte", subject)).thenReturn(egnyteConfig);

    handler.updateIntegrationInfo(subject, info);

    assertTrue(egnyteConfig.isEnabled());
    Mockito.verify(appCfgMgr, never())
        .saveAppConfigElementSet(any(), any(), anyBoolean(), any(), anyString());
  }

  @Test
  public void testUpdateOnboardingAppConfigOption() {
    IntegrationInfo info = new IntegrationInfo();
    info.setAvailable(true);
    info.setName(ONBOARDING_APP_NAME);

    Map<String, Object> options = new HashMap<>();
    options.put("unknown_option", "unknown_value");
    info.setOptions(options);
    info.setEnabled(true);

    // mock onboarding app in the system
    App app = new App(ONBOARDING_APP_NAME, "Onboarding", false);
    UserAppConfig onboardingConfig = new UserAppConfig(subject, app, false);
    when(appCfgMgr.getByAppName("app.onboarding", subject)).thenReturn(onboardingConfig);

    IntegrationInfo updateInfo = handler.updateIntegrationInfo(subject, info);
    assertTrue(updateInfo.isEnabled());
  }

  @Test
  public void testUpdateUnknownIntegrationName() {
    IntegrationInfo infor = new IntegrationInfo();
    infor.setAvailable(true);
    infor.setEnabled(false);
    infor.setName("UNKNOWN");

    assertThrows(
        IllegalArgumentException.class, () -> handler.updateIntegrationInfo(subject, infor));
    Mockito.verify(userMgr, never())
        .setPreference(Preference.DROPBOX, infor.isEnabled() + "", subject.getUsername());
  }

  @Test
  public void getBoxOptions() {
    SystemPropertyValue boxAvailable = getSystemPropertyValueAllowed(BOX_AVAILABLE);
    when(sysPropMgr.findByName(BOX_AVAILABLE)).thenReturn(boxAvailable);

    UserPreference boxEnablement = new UserPreference(Preference.BOX, subject, "true");
    when(userMgr.getPreferenceForUser(subject, Preference.BOX)).thenReturn(boxEnablement);

    UserPreference boxLinkTypePref = new UserPreference(Preference.BOX_LINK_TYPE, subject, "LIVE");
    when(userMgr.getPreferenceForUser(subject, Preference.BOX_LINK_TYPE))
        .thenReturn(boxLinkTypePref);
    IntegrationInfo info = handler.getIntegration(subject, Preference.BOX.name());
    assertThat(info.getOptions()).containsEntry(Preference.BOX_LINK_TYPE.name(), "LIVE");
  }

  @Test
  public void getSlackOptions() {
    SystemPropertyValue slackAvailable = getSystemPropertyValueAllowed(SLACK_AVAILABLE);
    when(sysPropMgr.findByName(SLACK_AVAILABLE)).thenReturn(slackAvailable);

    IntegrationInfo info = handler.getIntegration(subject, SLACK_APP_NAME);
    Map<String, Object> options = info.getOptions();
    assertNotNull(options);
    assertThat(options).isEmpty();
  }

  @Test
  public void getDigitalCommonsDataOptions() {
    SystemPropertyValue digitalCommonsDataAvailable =
        getSystemPropertyValueAllowed(DIGITAL_COMMON_DATA_AVAILABLE);

    UserConnection userConn = new UserConnection();
    userConn.setAccessToken("<ACCESS_TOKEN>");

    when(sysPropMgr.findByName(DIGITAL_COMMON_DATA_AVAILABLE))
        .thenReturn(digitalCommonsDataAvailable);
    when(userConnectionManager.findByUserNameProviderName(
            anyString(), eq(DIGITAL_COMMONS_DATA_APP_NAME)))
        .thenReturn(Optional.of(userConn));

    IntegrationInfo info = handler.getIntegration(subject, DIGITAL_COMMONS_DATA_APP_NAME);
    assertEquals(DIGITAL_COMMONS_DATA_APP_NAME, info.getName());
    Map<String, Object> options = info.getOptions();
    assertNotNull(options);
    assertThat(options).hasSize(1);
    assertThat(options).containsEntry(DIGITAL_COMMONS_DATA_USER_TOKEN, null);
  }

  @Test
  public void getDmpAssistantOAuthStatus() {
    SystemPropertyValue dmpAssistantAvailable =
        getSystemPropertyValueAllowed(DMPASSISTANT_AVAILABLE);

    UserConnection userConn = new UserConnection();
    userConn.setAccessToken("<ACCESS_TOKEN>");

    when(sysPropMgr.findByName(DMPASSISTANT_AVAILABLE)).thenReturn(dmpAssistantAvailable);
    when(userConnectionManager.findByUserNameProviderName(anyString(), eq(DMPASSISTANT_APP_NAME)))
        .thenReturn(Optional.of(userConn));

    IntegrationInfo info = handler.getIntegration(subject, DMPASSISTANT_APP_NAME);
    assertEquals(DMPASSISTANT_APP_NAME, info.getName());
    assertTrue(info.isOauthConnected());
    Map<String, Object> options = info.getOptions();
    assertNotNull(options);
    assertThat(options).hasSize(1);
    // the real token must never be surfaced to the Apps page: a stored token is sent as null
    assertThat(options).containsEntry(ACCESS_TOKEN_SETTING, null);
  }

  /**
   * OMERO is a single-option-set appConfig integration, so getIntegration replaces the whole
   * options map from the app config before postProcessInfo adds the withheld token. Stubbing the
   * app config is what makes this test exercise that real ordering rather than a null-config
   * shortcut.
   */
  private void stubOmeroAppConfig() {
    UserAppConfig omeroConfig =
        new UserAppConfig(subject, new App(OMERO_APP_NAME, "Omero", true), true);
    when(appCfgMgr.getByAppName("app.omero", subject)).thenReturn(omeroConfig);
  }

  @Test
  public void getOmeroConnectionStatus() {
    SystemPropertyValue omeroAvailable = getSystemPropertyValueAllowed(OMERO_AVAILABLE);

    UserConnection userConn = new UserConnection();
    userConn.setAccessToken("omerouser_,_omeropassword");

    when(sysPropMgr.findByName(OMERO_AVAILABLE)).thenReturn(omeroAvailable);
    stubOmeroAppConfig();
    when(userConnectionManager.findByUserNameProviderName(anyString(), eq(OMERO_APP_NAME)))
        .thenReturn(Optional.of(userConn));

    IntegrationInfo info = handler.getIntegration(subject, OMERO_APP_NAME);
    assertEquals(OMERO_APP_NAME, info.getName());
    assertTrue(info.isOauthConnected());
    // the stored OMERO username/password must never reach the Apps page
    assertThat(info.getOptions()).containsEntry(ACCESS_TOKEN_SETTING, null);
    assertThat(info.getOptions().toString())
        .as("the real OMERO credentials must not appear anywhere in the options")
        .doesNotContain("omeropassword");
  }

  @Test
  public void getOmeroConnectionStatusWhenNotConnected() {
    SystemPropertyValue omeroAvailable = getSystemPropertyValueAllowed(OMERO_AVAILABLE);

    when(sysPropMgr.findByName(OMERO_AVAILABLE)).thenReturn(omeroAvailable);
    stubOmeroAppConfig();
    when(userConnectionManager.findByUserNameProviderName(anyString(), eq(OMERO_APP_NAME)))
        .thenReturn(Optional.empty());

    IntegrationInfo info = handler.getIntegration(subject, OMERO_APP_NAME);
    assertFalse(info.isOauthConnected());
    assertThat(info.getOptions()).doesNotContainKey(ACCESS_TOKEN_SETTING);
  }

  @Test
  public void clearedUserTokenReadsAsUnset() {
    when(sysPropMgr.findByName(DIGITAL_COMMON_DATA_AVAILABLE))
        .thenReturn(getSystemPropertyValueAllowed(DIGITAL_COMMON_DATA_AVAILABLE));
    UserConnection cleared = new UserConnection();
    cleared.setAccessToken("");
    when(userConnectionManager.findByUserNameProviderName(
            anyString(), eq(DIGITAL_COMMONS_DATA_APP_NAME)))
        .thenReturn(Optional.of(cleared));

    IntegrationInfo info = handler.getIntegration(subject, DIGITAL_COMMONS_DATA_APP_NAME);

    assertThat(info.getOptions()).doesNotContainKey(DIGITAL_COMMONS_DATA_USER_TOKEN);
  }

  @Test
  public void getPyratIntegrationOptions() {
    SystemPropertyValue pyratDataAvailable = getSystemPropertyValueAllowed(PYRAT_AVAILABLE);

    UserConnectionId userConnId1 = new UserConnectionId("user1", PYRAT_APP_NAME, "alias1");
    UserConnection userConn1 = new UserConnection();
    userConn1.setId(userConnId1);
    userConn1.setAccessToken("<API_KEY_1>");
    userConn1.setRank(1);

    UserConnectionId userConnId2 = new UserConnectionId("user2", PYRAT_APP_NAME, "alias2");
    UserConnection userConn2 = new UserConnection();
    userConn2.setId(userConnId2);
    userConn2.setAccessToken("<API_KEY_2>");
    userConn2.setRank(2);

    when(sysPropMgr.findByName(PYRAT_AVAILABLE)).thenReturn(pyratDataAvailable);
    when(userConnectionManager.findListByUserNameProviderName(anyString(), eq(PYRAT_APP_NAME)))
        .thenReturn(List.of(userConn1, userConn2));

    App app = new App(PYRAT_APP_NAME, "Pyrat", true);
    UserAppConfig pyratConfig = new UserAppConfig(subject, app, false);
    AppConfigElementSet appConfigElementSet1 = new AppConfigElementSet();
    appConfigElementSet1.setUserAppConfig(pyratConfig);
    appConfigElementSet1.addConfigElement(
        new AppConfigElement(
            new AppConfigElementDescriptor(
                new PropertyDescriptor(PYRAT_APIKEY, SettingsType.STRING, null)),
            ""));
    appConfigElementSet1.addConfigElement(
        new AppConfigElement(
            new AppConfigElementDescriptor(
                new PropertyDescriptor(PYRAT_URL, SettingsType.STRING, null)),
            "http://pyrat1.server.com/"));
    appConfigElementSet1.addConfigElement(
        new AppConfigElement(
            new AppConfigElementDescriptor(
                new PropertyDescriptor(PYRAT_ALIAS, SettingsType.STRING, null)),
            "alias1"));
    pyratConfig.addConfigSet(appConfigElementSet1);
    when(appCfgMgr.getByAppName(any(), any())).thenReturn(pyratConfig);
    when(repositoryConfigFactory.getDisplayLabelForAppConfig(any(), any()))
        .thenReturn(Optional.empty());

    Map<String, PyratServerConfigurationDTO> serverByAlias =
        Map.of(
            "alias1", new PyratServerConfigurationDTO(null, "http://pyrat1.server.com/"),
            "alias2", new PyratServerConfigurationDTO(null, "http://pyrat2.server.com/"));
    when(pyratClient.getServerMapByAlias()).thenReturn(serverByAlias);

    IntegrationInfo info = handler.getIntegration(subject, PYRAT_APP_NAME);
    assertEquals(PYRAT_APP_NAME, info.getName());
    Map<String, Object> options = info.getOptions();
    assertNotNull(options);
    assertThat(options).hasSize(2);
    Collections.sort((List<ServerConfigurationDTO>) options.get(PYRAT_CONFIGURED_SERVERS));
    assertEquals(
        new ServerConfigurationDTO("alias1", "http://pyrat1.server.com/"),
        ((List<ServerConfigurationDTO>) options.get(PYRAT_CONFIGURED_SERVERS)).get(0));
    assertEquals(
        new ServerConfigurationDTO("alias2", "http://pyrat2.server.com/"),
        ((List<ServerConfigurationDTO>) options.get(PYRAT_CONFIGURED_SERVERS)).get(1));

    // here we do get("null") becasue since the AppCnfigSet is not saved into DB (as per mocks)
    // then it has not got a proper numerical ID
    assertThat(((Map<String, String>) options.get("null"))).containsEntry(PYRAT_ALIAS, "alias1");
    assertThat(((Map<String, String>) options.get("null"))).containsEntry(PYRAT_APIKEY, null);
    assertThat(((Map<String, String>) options.get("null")))
        .containsEntry(PYRAT_URL, "http://pyrat1.server.com/");
  }

  @Test
  public void testSaveDSWAppConfigOption() {
    String origDswAlias = "dswAlias";
    String origDswUrl = "dsw.url.org";
    String origDswToken = "abc123";

    AppConfigElementSet aces = new AppConfigElementSet();
    aces.addConfigElement(
        new AppConfigElement(
            new AppConfigElementDescriptor(
                new PropertyDescriptor(DSW_ALIAS, SettingsType.STRING, null)),
            origDswAlias));
    aces.addConfigElement(
        new AppConfigElement(
            new AppConfigElementDescriptor(
                new PropertyDescriptor(DSW_URL, SettingsType.STRING, null)),
            origDswUrl));
    aces.addConfigElement(
        new AppConfigElement(
            new AppConfigElementDescriptor(
                new PropertyDescriptor(DSW_APIKEY, SettingsType.STRING, null)),
            origDswToken));

    UserConnection existingConnection = new UserConnection();
    existingConnection.setDisplayName("DSW Display Name");
    existingConnection.setRank(1);
    existingConnection.setId(
        new UserConnectionId(subject.getUsername(), DSW_APP_NAME, origDswAlias));
    existingConnection.setExpireTime(0l);
    existingConnection.setAccessToken(origDswToken);

    // The potentially updated options that are being passed in
    // from the UI.  Note that the API Key is null, which is how a stored
    // key is returned to the UI and means "keep it".
    Map<String, String> dswOptions = new HashMap<>();
    dswOptions.put(DSW_ALIAS, origDswAlias);
    dswOptions.put(DSW_URL, origDswUrl);
    dswOptions.put(DSW_APIKEY, null);

    handler.saveAppOptions(null, dswOptions, DSW_APP_NAME, false, subject);
    Mockito.verify(userConnectionManager, times(0))
        .deleteByUserAndProvider(subject.getUsername(), DSW_APP_NAME, origDswAlias);
    Mockito.verify(userConnectionManager, times(0)).save(any());
  }

  @Test
  public void testUpdateDSWAppConfigOptionAlias() {
    String origDswAlias = "dswAlias";
    String origDswUrl = "dsw.url.org";
    String origDswToken = "abc123";
    String updatedDswAlias = "newDswAlias";

    AppConfigElementSet aces = new AppConfigElementSet();
    aces.addConfigElement(
        new AppConfigElement(
            new AppConfigElementDescriptor(
                new PropertyDescriptor(DSW_ALIAS, SettingsType.STRING, null)),
            origDswAlias));
    aces.addConfigElement(
        new AppConfigElement(
            new AppConfigElementDescriptor(
                new PropertyDescriptor(DSW_URL, SettingsType.STRING, null)),
            origDswUrl));
    aces.addConfigElement(
        new AppConfigElement(
            new AppConfigElementDescriptor(
                new PropertyDescriptor(DSW_APIKEY, SettingsType.STRING, null)),
            origDswToken));

    UserConnection existingConnection = new UserConnection();
    existingConnection.setDisplayName("DSW Display Name");
    existingConnection.setRank(1);
    existingConnection.setId(
        new UserConnectionId(subject.getUsername(), DSW_APP_NAME, origDswAlias));
    existingConnection.setExpireTime(0l);
    existingConnection.setAccessToken(origDswToken);

    when(appCfgMgr.findByAppConfigElementSetId(1l)).thenReturn(Optional.of(aces));
    when(userConnectionManager.findByUserNameProviderName(
            subject.getUsername(), DSW_APP_NAME, origDswAlias))
        .thenReturn(Optional.of(existingConnection));

    Map<String, String> dswOptions = new HashMap<>();
    dswOptions.put(DSW_ALIAS, updatedDswAlias);
    dswOptions.put(DSW_URL, origDswUrl);
    dswOptions.put(DSW_APIKEY, null);

    handler.saveAppOptions(1l, dswOptions, DSW_APP_NAME, false, subject);
    Mockito.verify(userConnectionManager, times(1))
        .deleteByUserAndProvider(subject.getUsername(), DSW_APP_NAME, origDswAlias);
    Mockito.verify(userConnectionManager, times(1)).save(any());
  }

  @Test
  public void testUpdateDSWAppConfigOptionUrl() {
    String origDswAlias = "dswAlias";
    String origDswUrl = "dsw.url.org";
    String origDswToken = "abc123";
    String updatedDswUrl = "dsw.newurl.com";

    AppConfigElementSet aces = new AppConfigElementSet();
    aces.addConfigElement(
        new AppConfigElement(
            new AppConfigElementDescriptor(
                new PropertyDescriptor(DSW_ALIAS, SettingsType.STRING, null)),
            origDswAlias));
    aces.addConfigElement(
        new AppConfigElement(
            new AppConfigElementDescriptor(
                new PropertyDescriptor(DSW_URL, SettingsType.STRING, null)),
            origDswUrl));
    aces.addConfigElement(
        new AppConfigElement(
            new AppConfigElementDescriptor(
                new PropertyDescriptor(DSW_APIKEY, SettingsType.STRING, null)),
            origDswToken));

    when(appCfgMgr.findByAppConfigElementSetId(1l)).thenReturn(Optional.of(aces));

    Map<String, String> dswOptions = new HashMap<>();
    dswOptions.put(DSW_ALIAS, origDswAlias);
    dswOptions.put(DSW_URL, updatedDswUrl);
    dswOptions.put(DSW_APIKEY, null);

    handler.saveAppOptions(1l, dswOptions, DSW_APP_NAME, false, subject);
    // There will be no interactions with the userConnectionManager methods since
    // the URL is not stored in the UserConnection table.
    Mockito.verify(userConnectionManager, never())
        .findByUserNameProviderName(subject.getUsername(), DSW_APP_NAME, origDswAlias);
    Mockito.verify(userConnectionManager, times(0))
        .deleteByUserAndProvider(subject.getUsername(), DSW_APP_NAME, origDswAlias);
    Mockito.verify(userConnectionManager, times(0)).save(any());
  }

  @Test
  public void testUpdateDSWAppConfigOptionAPIKey() {
    String origDswAlias = "dswAlias";
    String origDswUrl = "dsw.url.org";
    String origDswToken = "abc123";
    String updatedDswToken = "987zyx";

    AppConfigElementSet aces = new AppConfigElementSet();
    aces.addConfigElement(
        new AppConfigElement(
            new AppConfigElementDescriptor(
                new PropertyDescriptor(DSW_ALIAS, SettingsType.STRING, null)),
            origDswAlias));
    aces.addConfigElement(
        new AppConfigElement(
            new AppConfigElementDescriptor(
                new PropertyDescriptor(DSW_URL, SettingsType.STRING, null)),
            origDswUrl));
    aces.addConfigElement(
        new AppConfigElement(
            new AppConfigElementDescriptor(
                new PropertyDescriptor(DSW_APIKEY, SettingsType.STRING, null)),
            origDswToken));

    UserConnection existingConnection = new UserConnection();
    existingConnection.setDisplayName("DSW Display Name");
    existingConnection.setRank(1);
    existingConnection.setId(
        new UserConnectionId(subject.getUsername(), DSW_APP_NAME, origDswAlias));
    existingConnection.setExpireTime(0l);
    existingConnection.setAccessToken(origDswToken);

    when(appCfgMgr.findByAppConfigElementSetId(1l)).thenReturn(Optional.of(aces));
    when(userConnectionManager.findByUserNameProviderName(
            subject.getUsername(), DSW_APP_NAME, origDswAlias))
        .thenReturn(Optional.of(existingConnection));

    Map<String, String> dswOptions = new HashMap<>();
    dswOptions.put(DSW_ALIAS, origDswAlias);
    dswOptions.put(DSW_URL, origDswUrl);
    dswOptions.put(DSW_APIKEY, updatedDswToken);

    handler.saveAppOptions(1l, dswOptions, DSW_APP_NAME, false, subject);
    Mockito.verify(userConnectionManager, times(1))
        .deleteByUserAndProvider(subject.getUsername(), DSW_APP_NAME, origDswAlias);
    Mockito.verify(userConnectionManager, times(1)).save(any());
  }

  private SystemPropertyValue getSystemPropertyValueAllowed(SystemPropertyName propertyName) {
    SystemProperty sp = createPermissionEnumSystemProperty();

    sp.getDescriptor().setName(propertyName.name());
    SystemPropertyValue systemDefault = new SystemPropertyValue(sp, "ALLOWED");
    return systemDefault;
  }
}
