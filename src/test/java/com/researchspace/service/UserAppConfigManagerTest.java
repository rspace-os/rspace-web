package com.researchspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.model.User;
import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.model.apps.UserAppConfig;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@Sql(
    statements = {
      "insert into App values (-5, 1, 'slack', 'slack.app'),(-6, 1, 'dataverse', 'dataverse.app')",
      "insert into PropertyDescriptor values (-100, '', 'slackChannelName', '2'),(-101, '',"
          + " 'slackChannelLabel', '2')",
      "insert into AppConfigElementDescriptor (id, descriptor_id,app_id)  values  (-100,-100,"
          + " -5),(-101,-101, -5)"
    })
public class UserAppConfigManagerTest extends SpringTransactionalTest {

  private static final String SLACK_APP = "slack.app";
  private static final String SLACK_CHANNEL_LABEL = "slackChannelLabel";
  private static final String SLACK_CHANNEL_NAME = "slackChannelName";
  private static final String SLACK_CHANNEL1 = "slackChannel1";
  private static final String SLACK_CHANNEL2 = "slackChannel2";

  @Autowired private UserAppConfigManager userAppCfgMgr;
  User u1, otherUser;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
    u1 = createAndSaveRandomUser();
    otherUser = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(u1, otherUser);
    logoutAndLoginAs(u1);
  }

  @AfterEach
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testSaveAndRetrieveNewCfg() {
    Map<String, String> props = createValidPropertyMap();
    UserAppConfig savedCfg =
        userAppCfgMgr.saveAppConfigElementSet(props, null, false, u1, SLACK_APP);
    AppConfigElementSet retrievedElementSet =
        userAppCfgMgr.getAppConfigElementSetById(
            savedCfg.getAppConfigElementSets().iterator().next().getId());
    assertEquals("slack.app", savedCfg.getApp().getName());
    assertThat(savedCfg.getAppConfigElementSets()).hasSize(1);
    assertEquals(
        SLACK_CHANNEL1,
        retrievedElementSet.findElementByPropertyName(SLACK_CHANNEL_NAME).getValue());
  }

  @Test
  public void testDeleteCfg() throws Exception {
    Map<String, String> props = createValidPropertyMap();
    UserAppConfig cfg = userAppCfgMgr.saveAppConfigElementSet(props, null, false, u1, SLACK_APP);
    Long idToDelete = cfg.getAppConfigElementSets().iterator().next().getId();
    // otherUser lacks permissions
    logoutAndLoginAs(otherUser);
    assertThrows(
        AuthorizationException.class,
        () -> userAppCfgMgr.deleteAppConfigSet(idToDelete, otherUser, SLACK_APP));
    logoutAndLoginAs(u1);
    AppConfigElementSet deleted = userAppCfgMgr.deleteAppConfigSet(idToDelete, u1, SLACK_APP);
    assertNotNull(deleted);
    List<UserAppConfig> cfgs = userAppCfgMgr.getAll();
    assertThat(cfgs.get(0).getAppConfigElementSets()).isEmpty();
  }

  @Test
  public void deleteChecksTheAppOnlyAfterPermission() {
    UserAppConfig cfg =
        userAppCfgMgr.saveAppConfigElementSet(createValidPropertyMap(), null, false, u1, SLACK_APP);
    Long setId = cfg.getAppConfigElementSets().iterator().next().getId();

    // another user learns nothing about the set's app, and a missing set looks the same
    logoutAndLoginAs(otherUser);
    assertThrows(
        AuthorizationException.class,
        () -> userAppCfgMgr.deleteAppConfigSet(setId, otherUser, "dataverse.app"));
    assertThrows(
        AuthorizationException.class,
        () -> userAppCfgMgr.deleteAppConfigSet(-1L, otherUser, SLACK_APP));

    logoutAndLoginAs(u1);
    assertThrows(
        IllegalArgumentException.class,
        () -> userAppCfgMgr.deleteAppConfigSet(setId, u1, "dataverse.app"));
    assertNotNull(userAppCfgMgr.getAppConfigElementSetById(setId));
  }

  @Test
  public void expectedAppNameIsCaseInsensitiveLikeTheDatabase() {
    UserAppConfig cfg =
        userAppCfgMgr.saveAppConfigElementSet(
            createValidPropertyMap(), null, false, u1, "SLACK.App");
    assertEquals(SLACK_APP, cfg.getApp().getName());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            userAppCfgMgr.saveAppConfigElementSet(
                createValidPropertyMap(), null, false, u1, "dataverse.app"));
  }

  @Test
  public void incorrectConfigELementCountThrowsIAE() {
    Map<String, String> props = new HashMap<>();
    // we're missing the 'label' property
    props.put(SLACK_CHANNEL_NAME, SLACK_CHANNEL1);
    assertThrows(
        IllegalArgumentException.class,
        () -> userAppCfgMgr.saveAppConfigElementSet(props, null, false, u1, SLACK_APP));
  }

  @Test
  public void testUpdateExistingCfg() throws Exception {
    Map<String, String> props = new HashMap<>();
    props.put(SLACK_CHANNEL_NAME, SLACK_CHANNEL1);
    props.put(SLACK_CHANNEL_LABEL, "label");
    UserAppConfig cfg = userAppCfgMgr.saveAppConfigElementSet(props, null, false, u1, SLACK_APP);
    props.put(SLACK_CHANNEL_NAME, SLACK_CHANNEL2);
    Long setId = cfg.getAppConfigElementSets().iterator().next().getId();
    assertThrows(
        AuthorizationException.class,
        () -> userAppCfgMgr.saveAppConfigElementSet(props, setId, false, otherUser, SLACK_APP));
    cfg = userAppCfgMgr.saveAppConfigElementSet(props, setId, false, u1, SLACK_APP);
    assertEquals(
        SLACK_CHANNEL2,
        cfg.getAppConfigElementSets()
            .iterator()
            .next()
            .findElementByPropertyName(SLACK_CHANNEL_NAME)
            .getValue());
  }

  @Test
  public void testOrcidAppCanBeOnlyUpdatedFromTrustedSource() throws Exception {
    Map<String, String> props = new HashMap<>();
    props.put("ORCID_ID", "testId");

    // untrusted call to create new options
    assertThrows(
        AuthorizationException.class,
        () -> userAppCfgMgr.saveAppConfigElementSet(props, null, false, u1, "app.orcid"));

    // trusted call
    UserAppConfig cfg = userAppCfgMgr.saveAppConfigElementSet(props, null, true, u1, "app.orcid");
    AppConfigElementSet elementSet = cfg.getAppConfigElementSets().iterator().next();
    assertEquals("testId", elementSet.findElementByPropertyName("ORCID_ID").getValue());

    // now update with a new value
    props.put("ORCID_ID", "testId2");

    // untrusted call to update existing options
    Long elementSetId = elementSet.getId();
    assertThrows(
        AuthorizationException.class,
        () -> userAppCfgMgr.saveAppConfigElementSet(props, elementSetId, false, u1, "app.orcid"));

    // trusted call executes fine
    cfg = userAppCfgMgr.saveAppConfigElementSet(props, elementSet.getId(), true, u1, "app.orcid");
    AppConfigElementSet updatedElementSet = cfg.getAppConfigElementSets().iterator().next();
    assertEquals("testId2", updatedElementSet.findElementByPropertyName("ORCID_ID").getValue());
  }

  @Test
  public void postedNullKeepsTheStoredValue() {
    UserAppConfig cfg =
        userAppCfgMgr.saveAppConfigElementSet(createValidPropertyMap(), null, false, u1, SLACK_APP);
    Long setId = cfg.getAppConfigElementSets().iterator().next().getId();
    Map<String, String> update = createValidPropertyMap();
    update.put(SLACK_CHANNEL_NAME, null); // what the UI posts for an untouched secret
    update.put(SLACK_CHANNEL_LABEL, "renamed");

    userAppCfgMgr.saveAppConfigElementSet(update, setId, false, u1, SLACK_APP);

    AppConfigElementSet saved = userAppCfgMgr.getAppConfigElementSetById(setId);
    assertEquals(SLACK_CHANNEL1, saved.findElementByPropertyName(SLACK_CHANNEL_NAME).getValue());
    assertEquals("renamed", saved.findElementByPropertyName(SLACK_CHANNEL_LABEL).getValue());
  }

  @Test
  public void newSetCannotKeepANullValue() {
    Map<String, String> props = createValidPropertyMap();
    props.put(SLACK_CHANNEL_NAME, null);

    assertThrows(
        IllegalArgumentException.class,
        () -> userAppCfgMgr.saveAppConfigElementSet(props, null, false, u1, SLACK_APP));
  }

  @Test
  public void cannotUpdateAnotherUsersSetById() {
    UserAppConfig u1Cfg =
        userAppCfgMgr.saveAppConfigElementSet(createValidPropertyMap(), null, false, u1, SLACK_APP);
    Long u1SetId = u1Cfg.getAppConfigElementSets().iterator().next().getId();
    logoutAndLoginAs(otherUser);
    userAppCfgMgr.saveAppConfigElementSet(
        createValidPropertyMap(), null, false, otherUser, SLACK_APP);
    Map<String, String> overwrite = createValidPropertyMap();
    overwrite.put(SLACK_CHANNEL_NAME, SLACK_CHANNEL2);

    assertThrows(
        AuthorizationException.class,
        () ->
            userAppCfgMgr.saveAppConfigElementSet(overwrite, u1SetId, false, otherUser, SLACK_APP));
    assertEquals(
        SLACK_CHANNEL1,
        userAppCfgMgr
            .getAppConfigElementSetById(u1SetId)
            .findElementByPropertyName(SLACK_CHANNEL_NAME)
            .getValue());
  }

  private Map<String, String> createValidPropertyMap() {
    Map<String, String> props = new HashMap<>();
    props.put(SLACK_CHANNEL_NAME, SLACK_CHANNEL1);
    props.put(SLACK_CHANNEL_LABEL, "label");
    return props;
  }

  @Test
  public void testSaveAppConfigElementSetUnknownPropThrowsIAE() {
    Map<String, String> unknownProps = new HashMap<>();
    unknownProps.put("unknown", "any");
    assertThrows(
        IllegalArgumentException.class,
        () -> userAppCfgMgr.saveAppConfigElementSet(unknownProps, null, false, u1, SLACK_APP));
  }

  @Test
  public void testSaveAppConfigElementSetEmptySetThrowsIAE() {
    Map<String, String> emptyProps = new HashMap<>();
    assertThrows(
        IllegalArgumentException.class,
        () -> userAppCfgMgr.saveAppConfigElementSet(emptyProps, null, false, u1, SLACK_APP));
  }

  @Test
  public void testGetConfigByAppNameThrowsISEForUnknownApp() {
    assertThrows(IllegalStateException.class, () -> userAppCfgMgr.getByAppName("unknown", u1));
  }

  @Test
  public void testGetConfigByAppName() {
    UserAppConfig uac = userAppCfgMgr.getByAppName("dataverse.app", u1);
    assertNotNull(uac);

    UserAppConfig uac2 = userAppCfgMgr.getByAppName("dataverse.app", u1);
    assertNotNull(uac2);
    assertEquals(uac.getId(), uac2.getId());

    User u2 = createAndSaveRandomUser();
    UserAppConfig uac3 = userAppCfgMgr.getByAppName("dataverse.app", u2);
    assertFalse(uac3.getId().equals(uac.getId()), "Users should have their own configuration");
  }
}
