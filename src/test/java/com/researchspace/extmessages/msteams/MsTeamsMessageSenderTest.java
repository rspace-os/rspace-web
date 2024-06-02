package com.researchspace.extmessages.msteams;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.apps.App;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class MsTeamsMessageSenderTest {

  MsTeamsMessageSender msteamsSender;

  @Before
  public void setUp() throws Exception {
    msteamsSender = new MsTeamsMessageSender();
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testSupportsApp() {
    assertTrue(msteamsSender.supportsApp(new App(App.APP_MSTEAMS, "any", false)));
    assertFalse(msteamsSender.supportsApp(new App(App.APP_SLACK, "any", false)));
  }
}
