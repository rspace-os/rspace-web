package com.researchspace.slack;

import static org.junit.Assert.assertFalse;

import com.researchspace.extmessages.base.ExternalMessageSender;
import com.researchspace.extmessages.base.MessageDetails;
import com.researchspace.model.User;
import com.researchspace.model.apps.App;
import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.model.apps.UserAppConfig;
import com.researchspace.model.record.TestFactory;
import java.util.Collections;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SlackMessageSenderTest {

  private ExternalMessageSender slackSender;

  @Before
  public void setUp() throws Exception {
    slackSender = new SlackMessageSender();
  }

  @After
  public void tearDown() throws Exception {}

  @Test(expected = IllegalArgumentException.class)
  public void testUnsupportedAppThrowsIAE() {
    App unsupported = new App("any", "label", false);
    assertFalse(slackSender.supportsApp(unsupported));
    User anyUser = TestFactory.createAnyUser("any");
    AppConfigElementSet set = createAppConfigSet(unsupported, anyUser);
    MessageDetails message =
        new MessageDetails(anyUser, "Hello <br /> line 2", Collections.emptyList());
    slackSender.sendMessage(message, set, anyUser);
  }

  private AppConfigElementSet createAppConfigSet(App unsupported, User anyUser) {
    AppConfigElementSet set = new AppConfigElementSet();
    UserAppConfig cfg = new UserAppConfig(anyUser, unsupported, true);
    cfg.addConfigSet(set);
    return set;
  }
}
