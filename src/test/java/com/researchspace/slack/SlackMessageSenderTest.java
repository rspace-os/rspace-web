package com.researchspace.slack;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.extmessages.base.ExternalMessageSender;
import com.researchspace.model.User;
import com.researchspace.model.apps.App;
import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.model.apps.UserAppConfig;
import com.researchspace.testutils.TestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    assertThrows(IllegalArgumentException.class, () -> createAppConfigSet(unsupported, anyUser));
  }

  private AppConfigElementSet createAppConfigSet(App unsupported, User anyUser) {
    AppConfigElementSet set = new AppConfigElementSet();
    UserAppConfig cfg = new UserAppConfig(anyUser, unsupported, true);
    cfg.addConfigSet(set);
    return set;
  }
}
