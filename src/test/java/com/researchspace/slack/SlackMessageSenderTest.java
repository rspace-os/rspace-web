package com.researchspace.slack;

import static com.researchspace.service.IntegrationsHandler.SLACK_APP_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.extmessages.base.ExternalMessageSender;
import com.researchspace.model.User;
import com.researchspace.model.apps.App;
import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.model.apps.UserAppConfig;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.testutils.TestFactory;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class SlackMessageSenderTest {

  private ExternalMessageSender slackSender;

  @BeforeEach
  public void setUp() throws Exception {
    slackSender = new SlackMessageSender();
  }

  @Test
  public void testUnsupportedAppThrowsIAE() {
    App unsupported = new App("any", "label", false);
    assertFalse(slackSender.supportsApp(unsupported));
    User anyUser = TestFactory.createAnyUser("any");
    AppConfigElementSet set = new AppConfigElementSet();
    UserAppConfig cfg = new UserAppConfig(anyUser, unsupported, true);

    assertThrows(IllegalArgumentException.class, () -> cfg.addConfigSet(set));
  }

  @Test
  public void webhookUrlComesFromTheChannelsUserConnection() {
    User user = TestFactory.createAnyUser("any");
    AppConfigElementSet set = new AppConfigElementSet();
    ReflectionTestUtils.setField(set, "id", 5L);
    set.setUserAppConfig(new UserAppConfig(user, new App(App.APP_SLACK, "Slack", true), true));
    UserConnection connection =
        new UserConnection(new UserConnectionId(user.getUsername(), SLACK_APP_NAME, "5"), "token");
    connection.setSecret("https://hooks.slack.com/services/x");
    SlackMessageSender sender = new SlackMessageSender();
    sender.userConnectionManager = mock(UserConnectionManager.class);
    when(sender.userConnectionManager.findByUserNameProviderName(
            user.getUsername(), SLACK_APP_NAME, "5"))
        .thenReturn(Optional.of(connection));

    assertEquals("https://hooks.slack.com/services/x", sender.doGetPostUrl(set));
  }
}
