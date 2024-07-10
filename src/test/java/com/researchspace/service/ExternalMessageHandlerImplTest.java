package com.researchspace.service;

import static com.researchspace.core.util.TransformerUtils.toList;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.apps.UserAppConfig;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.impl.ConditionalTestRunner;
import com.researchspace.service.impl.RunIfSystemPropertyDefined;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.jdbc.Sql;

@RunWith(ConditionalTestRunner.class)
@Sql(
    statements = {
      "insert into App values (-5, 1, 'other.app', 'other.app')",
      "insert into PropertyDescriptor values (-100, '', 'otherAppConfig', '2')",
      "insert into AppConfigElementDescriptor (id, descriptor_id,app_id) values (-100,-100, -5)"
    })
public class ExternalMessageHandlerImplTest extends SpringTransactionalTest {

  private static final String EXPECTED_MESSAGE = "hello from Spring Test";

  @Value("${slack.realConnectionTest.webhookUrl}")
  private String slackTestWebhookUrl;

  @Value("${msteams.realConnectionTest.webhookUrl}")
  private String msTeamsTestWebhookUrl;

  private @Autowired ExternalMessageHandler handler;
  private @Autowired UserAppConfigManager mgr;
  private User testUser;

  @Before
  public void setUp() throws Exception {
    testUser = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(testUser);
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void sendExternalMessageToSlack() {
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(testUser, "any");
    StructuredDocument doc2 = createBasicDocumentInRootFolderWithText(testUser, "any2");
    StructuredDocument doc3 = createBasicDocumentInRootFolderWithText(testUser, "any3");
    Long cfgSetId = setUpAppConfigForUser(testUser, () -> getSlackDevDfg());
    logoutAndLoginAs(testUser);

    ServiceOperationResult<ResponseEntity<String>> response =
        handler.sendExternalMessage(
            EXPECTED_MESSAGE, cfgSetId, toList(doc.getId(), doc2.getId(), doc3.getId()), testUser);
    assertTrue(response.isSucceeded());
    ServiceOperationResult<ResponseEntity<String>> response2 =
        handler.sendExternalMessage(EXPECTED_MESSAGE, cfgSetId, toList(doc.getId()), testUser);
    assertTrue(response2.isSucceeded());
  }

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void sendExternalMessageToMSTeams() {
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(testUser, "any");
    StructuredDocument doc2 = createBasicDocumentInRootFolderWithText(testUser, "any2");
    StructuredDocument doc3 = createBasicDocumentInRootFolderWithText(testUser, "any3");
    Long cfgSetId = setUpAppConfigForUser(testUser, () -> getMsTeamsCfg());
    logoutAndLoginAs(testUser);

    ServiceOperationResult<ResponseEntity<String>> response =
        handler.sendExternalMessage(
            EXPECTED_MESSAGE, cfgSetId, toList(doc.getId(), doc2.getId(), doc3.getId()), testUser);
    assertTrue(response.isSucceeded());

    ServiceOperationResult<ResponseEntity<String>> response2 =
        handler.sendExternalMessage(EXPECTED_MESSAGE, cfgSetId, toList(doc.getId()), testUser);
    assertTrue(response2.isSucceeded());
  }

  private Long setUpAppConfigForUser(User user, Supplier<Map<String, String>> appConfigSupplier) {
    Map<String, String> config = appConfigSupplier.get();
    return saveAndReturnId(user, config);
  }

  private Long saveAndReturnId(User user, Map<String, String> config) {
    UserAppConfig appConfig = mgr.saveAppConfigElementSet(config, null, false, user);
    Long cfgSetId = appConfig.getAppConfigElementSets().iterator().next().getId();
    return cfgSetId;
  }

  private Long setUpNonMessageAppConfig() {
    Map<String, String> config = new HashMap<>();
    config.put("otherAppConfig", "some value");
    UserAppConfig appConfig = mgr.saveAppConfigElementSet(config, null, false, testUser);
    Long cfgSetId = appConfig.getAppConfigElementSets().iterator().next().getId();
    return cfgSetId;
  }

  @Test
  public void sendExternalMessageReturnsEmptyForNonMessageApp() {

    Long cfgSetId = setUpNonMessageAppConfig();
    logoutAndLoginAs(testUser);
    ServiceOperationResult<ResponseEntity<String>> response =
        handler.sendExternalMessage(EXPECTED_MESSAGE, cfgSetId, toList(-10L), testUser);
    assertFalse(response.isSucceeded());
  }

  @Test
  public void sendExternalMessageReturnsEmptyForUnknownId() {
    ServiceOperationResult<ResponseEntity<String>> response =
        handler.sendExternalMessage(EXPECTED_MESSAGE, -100L, toList(-10L), testUser);
    assertFalse(response.isSucceeded());
  }

  @Test()
  public void sendExternalMessagePermissions() throws Exception {
    Long cfgSetId = setUpAppConfigForUser(testUser, () -> getSlackDevDfg());
    logoutAndLoginAs(testUser);
    // other user can't access u1's apps
    User u2 = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(u2);
    logoutAndLoginAs(u2);
    RSpaceTestUtils.assertAuthExceptionThrown(
        () -> handler.sendExternalMessage(EXPECTED_MESSAGE, cfgSetId, toList(-10L), u2));
  }

  private Map<String, String> getSlackDevDfg() {
    Map<String, String> map = new HashMap<>();
    map.put("SLACK_CHANNEL_NAME", "general");
    map.put("SLACK_TEAM_NAME", "rspacedev");
    map.put("SLACK_WEBHOOK_URL", slackTestWebhookUrl);
    map.put("SLACK_CHANNEL_LABEL", "general");
    map.put("SLACK_USER_ID", "U123");
    map.put("SLACK_TEAM_ID", "T456");
    map.put("SLACK_CHANNEL_ID", "C789");
    map.put("SLACK_USER_ACCESS_TOKEN", "xoxp-123456789");
    return map;
  }

  private Map<String, String> getMsTeamsCfg() {
    Map<String, String> map = new HashMap<>();
    map.put("MSTEAMS_CHANNEL_LABEL", "general");
    map.put("MSTEAMS_WEBHOOK_URL", msTeamsTestWebhookUrl);
    return map;
  }
}
