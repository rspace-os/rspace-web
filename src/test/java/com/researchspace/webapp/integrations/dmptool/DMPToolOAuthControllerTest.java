package com.researchspace.webapp.integrations.dmptool;

import java.net.MalformedURLException;
import java.net.URL;
import org.junit.Before;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.view.RedirectView;

public class DMPToolOAuthControllerTest {

  private DMPToolOAuthController testee;

  @Before
  public void setUp() throws MalformedURLException {
    testee = new DMPToolOAuthController();
    ReflectionTestUtils.setField(testee, "callbackBaseUrl", "https://callbackdmptool.org");
    ReflectionTestUtils.setField(testee, "baseUrl", new URL("https://basedmptool.org"));
  }

  @org.junit.Test
  public void testTokenScopeRequestedWithEditAndRead() throws MalformedURLException {
    RedirectView rv = testee.connect();
    assert (rv.getUrl().contains("scope=read_dmps+edit_dmps"));
  }
}
