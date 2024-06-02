package com.researchspace.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import com.researchspace.model.User;
import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.model.apps.UserAppConfig;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
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

  private static final String SLACK_CHANNEL_LABEL = "slackChannelLabel";
  private static final String SLACK_CHANNEL_NAME = "slackChannelName";
  private static final String SLACK_CHANNEL1 = "slackChannel1";
  private static final String SLACK_CHANNEL2 = "slackChannel2";

  @Autowired private UserAppConfigManager userAppCfgMgr;
  User u1, otherUser;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    u1 = createAndSaveRandomUser();
    otherUser = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(u1, otherUser);
    logoutAndLoginAs(u1);
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testSaveNewCfg() {
    Map<String, String> props = createValidPropertyMap();
    UserAppConfig cfg = userAppCfgMgr.saveAppConfigElementSet(props, null, false, u1);
    assertEquals("slack.app", cfg.getApp().getName());
    assertEquals(1, cfg.getAppConfigElementSets().size());
    assertEquals(
        SLACK_CHANNEL1,
        cfg.getAppConfigElementSets()
            .iterator()
            .next()
            .findElementByPropertyName(SLACK_CHANNEL_NAME)
            .getValue());
  }

  @Test
  public void testDeleteCfg() throws Exception {
    Map<String, String> props = createValidPropertyMap();
    UserAppConfig cfg = userAppCfgMgr.saveAppConfigElementSet(props, null, false, u1);
    Long idToDelete = cfg.getAppConfigElementSets().iterator().next().getId();
    // otherUser lacks permissions
    logoutAndLoginAs(otherUser);
    assertAuthorisationExceptionThrown(
        () -> userAppCfgMgr.deleteAppConfigSet(idToDelete, otherUser));
    logoutAndLoginAs(u1);
    AppConfigElementSet deleted = userAppCfgMgr.deleteAppConfigSet(idToDelete, u1);
    assertNotNull(deleted);
    List<UserAppConfig> cfgs = userAppCfgMgr.getAll();
    assertEquals(0, cfgs.get(0).getAppConfigElementSets().size());
  }

  @Test(expected = IllegalArgumentException.class)
  public void incorrectConfigELementCountThrowsIAE() {
    Map<String, String> props = new HashMap<>();
    // we're missing the 'label' property
    props.put(SLACK_CHANNEL_NAME, SLACK_CHANNEL1);
    // setup
    userAppCfgMgr.saveAppConfigElementSet(props, null, false, u1);
  }

  @Test
  public void testUpdateExistingCfg() throws Exception {
    Map<String, String> props = new HashMap<>();
    props.put(SLACK_CHANNEL_NAME, SLACK_CHANNEL1);
    props.put(SLACK_CHANNEL_LABEL, "label");
    // setup
    UserAppConfig cfg = userAppCfgMgr.saveAppConfigElementSet(props, null, false, u1);
    // update
    props.put(SLACK_CHANNEL_NAME, SLACK_CHANNEL2);
    Long setId = cfg.getAppConfigElementSets().iterator().next().getId();
    assertAuthorisationExceptionThrown(
        () -> userAppCfgMgr.saveAppConfigElementSet(props, setId, false, otherUser));
    cfg = userAppCfgMgr.saveAppConfigElementSet(props, setId, false, u1);
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
    assertAuthorisationExceptionThrown(
        () -> userAppCfgMgr.saveAppConfigElementSet(props, null, false, u1));

    // trusted call
    UserAppConfig cfg = userAppCfgMgr.saveAppConfigElementSet(props, null, true, u1);
    AppConfigElementSet elementSet = cfg.getAppConfigElementSets().iterator().next();
    assertEquals("testId", elementSet.findElementByPropertyName("ORCID_ID").getValue());

    // now update with a new value
    props.put("ORCID_ID", "testId2");

    // untrusted call to update existing options
    assertAuthorisationExceptionThrown(
        () -> userAppCfgMgr.saveAppConfigElementSet(props, elementSet.getId(), false, u1));

    // trusted call executes fine
    cfg = userAppCfgMgr.saveAppConfigElementSet(props, elementSet.getId(), true, u1);
    AppConfigElementSet updatedElementSet = cfg.getAppConfigElementSets().iterator().next();
    assertEquals("testId2", updatedElementSet.findElementByPropertyName("ORCID_ID").getValue());
  }

  private Map<String, String> createValidPropertyMap() {
    Map<String, String> props = new HashMap<>();
    props.put(SLACK_CHANNEL_NAME, SLACK_CHANNEL1);
    props.put(SLACK_CHANNEL_LABEL, "label");
    return props;
  }

  @Test(expected = IllegalArgumentException.class)
  public void testSaveAppConfigElementSetUnknownPropThrowsIAE() {
    Map<String, String> unknownProps = new HashMap<>();
    unknownProps.put("unknown", "any");
    userAppCfgMgr.saveAppConfigElementSet(unknownProps, null, false, u1);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testSaveAppConfigElementSetEmptySetThrowsIAE() {
    Map<String, String> emptyProps = new HashMap<>();
    userAppCfgMgr.saveAppConfigElementSet(emptyProps, null, false, u1);
  }

  @Test(expected = IllegalStateException.class)
  public void testGetConfigByAppNameThrowsISEForUnknownApp() {
    userAppCfgMgr.getByAppName("unknown", u1);
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
    assertFalse("Users should have their own configuration", uac3.getId().equals(uac.getId()));
  }
}
