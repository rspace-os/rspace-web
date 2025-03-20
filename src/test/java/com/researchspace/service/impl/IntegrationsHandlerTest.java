package com.researchspace.service.impl;

import static com.researchspace.model.system.SystemPropertyTestFactory.createPermissionEnumSystemProperty;
import static com.researchspace.service.IntegrationsHandler.DIGITAL_COMMONS_DATA_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.DIGITAL_COMMONS_DATA_USER_TOKEN;
import static com.researchspace.service.IntegrationsHandler.EGNYTE_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.EGNYTE_DOMAIN_SETTING;
import static com.researchspace.service.IntegrationsHandler.ONBOARDING_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.PYRAT_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.SLACK_APP_NAME;
import static com.researchspace.webapp.integrations.pyrat.PyratClient.PYRAT_ALIAS;
import static com.researchspace.webapp.integrations.pyrat.PyratClient.PYRAT_APIKEY;
import static com.researchspace.webapp.integrations.pyrat.PyratClient.PYRAT_CONFIGURED_SERVERS;
import static com.researchspace.webapp.integrations.pyrat.PyratClient.PYRAT_URL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.system.SystemProperty;
import com.researchspace.model.system.SystemPropertyTestFactory;
import com.researchspace.model.system.SystemPropertyValue;
import com.researchspace.service.CommunityServiceManager;
import com.researchspace.service.IRepositoryConfigFactory;
import com.researchspace.service.SystemPropertyManager;
import com.researchspace.service.SystemPropertyName;
import com.researchspace.service.SystemPropertyPermissionManager;
import com.researchspace.service.UserAppConfigManager;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.service.UserManager;
import com.researchspace.webapp.integrations.pyrat.PyratClient;
import com.researchspace.webapp.integrations.pyrat.PyratServerDTO;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class IntegrationsHandlerTest {

  @Rule public MockitoRule mockito = MockitoJUnit.rule();

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

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
    subject = TestFactory.createAnyUser("any");
    when(communityMgr.listCommunitiesForUser(eq(subject.getId()))).thenReturn(new ArrayList<>());
    handler.setUserConnectionManager(userConnectionManager);
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
    assertEquals(1, parent2child.get(parent).size());
    assertEquals(child, parent2child.get(parent).get(0));
    assertNull(parent2child.get(child));
  }

  @Test
  public void isValidIntegration() {
    assertTrue(handler.isValidIntegration("OneDrive"));
    assertTrue(handler.isValidIntegration(Preference.DROPBOX.name()));
    // this is not an integration, but another preference
    assertFalse(handler.isValidIntegration(Preference.BROADCAST_NOTIFICATIONS_BY_EMAIL.name()));
    assertFalse(handler.isValidIntegration("xyz")); // invalid preference handled gracefully
  }

  @Test(expected = IllegalArgumentException.class)
  public void getForPropertyThrowsIAEIfUnknownProperty() {
    handler.getIntegration(subject, "unknown");
  }

  private SystemPropertyValue createSystemPropertyForName(String prefName) {
    SystemProperty sp = SystemPropertyTestFactory.createASystemProperty();
    String propName = prefName.toLowerCase() + ".enabled";
    sp.getDescriptor().setName(propName);
    SystemPropertyValue rc = new SystemPropertyValue(sp, "true");
    return rc;
  }

  @Test()
  public void getForPropertyHappyCase() {
    String propName = "DROPBOX", systemPropertyName = propName.toLowerCase() + ".available";
    SystemPropertyValue dropboxAvailable = getSystemPropertyValueAllowed(systemPropertyName);
    UserPreference dropboxEnabled = new UserPreference(Preference.DROPBOX, subject, "true");

    when(systemPropertyPermissionUtils.isPropertyAllowed(eq(subject), eq(systemPropertyName)))
        .thenReturn(true);
    when(userMgr.getPreferenceForUser(subject, Preference.DROPBOX)).thenReturn(dropboxEnabled);
    when(sysPropMgr.findByName(eq(systemPropertyName))).thenReturn(dropboxAvailable);

    IntegrationInfo info = handler.getIntegration(subject, propName);
    assertTrue(info.isAvailable());
    assertTrue(info.isEnabled());
    assertEquals(propName, info.getName());
    assertNotNull(info.getOptions());
    assertEquals(0, info.getOptions().size());

    UserPreference dropboxDisabled = new UserPreference(Preference.DROPBOX, subject, "false");
    when(userMgr.getPreferenceForUser(subject, Preference.DROPBOX)).thenReturn(dropboxDisabled);
    info = handler.getIntegration(subject, propName);
    assertTrue(info.isAvailable());
    assertFalse(info.isEnabled());

    when(systemPropertyPermissionUtils.isPropertyAllowed(eq(subject), eq(systemPropertyName)))
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
    when(sysPropMgr.findByName(SystemPropertyName.DROPBOX_AVAILABLE.getPropertyName()))
        .thenReturn(expectedDropboxAvailableProp);

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
    when(sysPropMgr.findByName(SystemPropertyName.BOX_AVAILABLE.getPropertyName()))
        .thenReturn(expectedBoxAvailableProp);

    handler.updateIntegrationInfo(subject, info);

    Mockito.verify(userMgr, times(1))
        .setPreference(Preference.BOX, info.isEnabled() + "", subject.getUsername());
    Mockito.verify(userMgr, times(1))
        .setPreference(Preference.BOX_LINK_TYPE, "VERSIONED", subject.getUsername());
  }

  @Test
  public void testUpdateSlackApp() {

    // set slack as available
    SystemPropertyValue slackAvailable = getSystemPropertyValueAllowed("slack.available");
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
        .saveAppConfigElementSet(expectedOptions, null, false, subject);
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

  @Test(expected = IllegalArgumentException.class)
  public void testUpdateUnknownIntegrationName() {
    IntegrationInfo infor = new IntegrationInfo();
    infor.setAvailable(true);
    infor.setEnabled(false);
    infor.setName("UNKNOWN");

    handler.updateIntegrationInfo(subject, infor);
    Mockito.verify(userMgr, never())
        .setPreference(Preference.DROPBOX, infor.isEnabled() + "", subject.getUsername());
  }

  @Test
  public void getBoxOptions() {
    SystemPropertyValue boxAvailable = getSystemPropertyValueAllowed("box.available");
    when(sysPropMgr.findByName(SystemPropertyName.BOX_AVAILABLE.getPropertyName()))
        .thenReturn(boxAvailable);

    UserPreference boxEnablement = new UserPreference(Preference.BOX, subject, "true");
    when(userMgr.getPreferenceForUser(subject, Preference.BOX)).thenReturn(boxEnablement);

    UserPreference boxLinkTypePref = new UserPreference(Preference.BOX_LINK_TYPE, subject, "LIVE");
    when(userMgr.getPreferenceForUser(subject, Preference.BOX_LINK_TYPE))
        .thenReturn(boxLinkTypePref);
    IntegrationInfo info = handler.getIntegration(subject, Preference.BOX.name());
    assertEquals("LIVE", info.getOptions().get(Preference.BOX_LINK_TYPE.name()));
  }

  @Test
  public void getSlackOptions() {
    SystemPropertyValue slackAvailable = getSystemPropertyValueAllowed("slack.available");
    when(sysPropMgr.findByName(SystemPropertyName.SLACK_AVAILABLE)).thenReturn(slackAvailable);

    IntegrationInfo info = handler.getIntegration(subject, SLACK_APP_NAME);
    Map<String, Object> options = info.getOptions();
    assertNotNull(options);
    assertEquals(0, options.size());
  }

  @Test
  public void getDigitalCommonsDataOptions() {
    String DIGITAL_COMMONS_DATA_AVAILABLE = "digitalCommonsData.available";
    SystemPropertyValue digitalCommonsDataAvailable =
        getSystemPropertyValueAllowed(DIGITAL_COMMONS_DATA_AVAILABLE);

    UserConnection userConn = new UserConnection();
    userConn.setAccessToken("<ACCESS_TOKEN>");

    when(sysPropMgr.findByName(DIGITAL_COMMONS_DATA_AVAILABLE))
        .thenReturn(digitalCommonsDataAvailable);
    when(userConnectionManager.findByUserNameProviderName(
            anyString(), eq(DIGITAL_COMMONS_DATA_APP_NAME)))
        .thenReturn(Optional.of(userConn));

    IntegrationInfo info = handler.getIntegration(subject, DIGITAL_COMMONS_DATA_APP_NAME);
    assertEquals(DIGITAL_COMMONS_DATA_APP_NAME, info.getName());
    Map<String, Object> options = info.getOptions();
    assertNotNull(options);
    assertEquals(1, options.size());
    assertEquals(options.get(DIGITAL_COMMONS_DATA_USER_TOKEN), "<ACCESS_TOKEN>");
  }

  @Test
  public void getPyratIntegrationOptions() {
    String PYRAT_AVAILABLE = "pyrat.available";
    SystemPropertyValue digitalCommonsDataAvailable =
        getSystemPropertyValueAllowed(PYRAT_AVAILABLE);

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

    when(sysPropMgr.findByName(PYRAT_AVAILABLE)).thenReturn(digitalCommonsDataAvailable);
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

    Map<String, PyratServerDTO> serverByAlias =
        Map.of(
            "alias1", new PyratServerDTO(null, "http://pyrat1.server.com/"),
            "alias2", new PyratServerDTO(null, "http://pyrat2.server.com/"));
    when(pyratClient.getServerByAlias()).thenReturn(serverByAlias);

    IntegrationInfo info = handler.getIntegration(subject, PYRAT_APP_NAME);
    assertEquals(PYRAT_APP_NAME, info.getName());
    Map<String, Object> options = info.getOptions();
    assertNotNull(options);
    assertEquals(2, options.size());
    Collections.sort((List<PyratServerDTO>) options.get(PYRAT_CONFIGURED_SERVERS));
    assertEquals(
        new PyratServerDTO("alias1", "http://pyrat1.server.com/"),
        ((List<PyratServerDTO>) options.get(PYRAT_CONFIGURED_SERVERS)).get(0));
    assertEquals(
        new PyratServerDTO("alias2", "http://pyrat2.server.com/"),
        ((List<PyratServerDTO>) options.get(PYRAT_CONFIGURED_SERVERS)).get(1));

    // here we do get("null") becasue since the AppCnfigSet is not saved into DB (as per mocks)
    // then it has not got a proper numerical ID
    assertEquals("alias1", ((Map<String, String>) options.get("null")).get(PYRAT_ALIAS));
    assertEquals("<API_KEY_1>", ((Map<String, String>) options.get("null")).get(PYRAT_APIKEY));
    assertEquals(
        "http://pyrat1.server.com/", ((Map<String, String>) options.get("null")).get(PYRAT_URL));
  }

  private SystemPropertyValue getSystemPropertyValueAllowed(String propertyName) {
    SystemProperty sp = createPermissionEnumSystemProperty();

    sp.getDescriptor().setName(propertyName);
    SystemPropertyValue systemDefault = new SystemPropertyValue(sp, "ALLOWED");
    return systemDefault;
  }
}
