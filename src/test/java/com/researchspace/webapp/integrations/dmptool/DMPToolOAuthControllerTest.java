package com.researchspace.webapp.integrations.dmptool;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.service.impl.ShiroTestUtils;
import java.net.MalformedURLException;
import java.net.URL;
import org.apache.shiro.session.mgt.SimpleSession;
import org.apache.shiro.subject.Subject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.view.RedirectView;

public class DMPToolOAuthControllerTest {
  private final ShiroTestUtils shiro = new ShiroTestUtils();

  @AfterEach
  void clearSubject() {
    shiro.clearSubject();
  }

  private DMPToolOAuthController testee;

  @BeforeEach
  public void setUp() throws MalformedURLException {
    testee = new DMPToolOAuthController();
    Subject subject = mock(Subject.class);
    when(subject.getSession()).thenReturn(new SimpleSession());
    shiro.setSubject(subject);
    ReflectionTestUtils.setField(testee, "callbackBaseUrl", "https://callbackdmptool.org");
    ReflectionTestUtils.setField(testee, "baseUrl", new URL("https://basedmptool.org"));
  }

  @Test
  public void testTokenScopeRequestedWithEditAndRead() throws MalformedURLException {
    RedirectView rv = testee.connect();
    assert (rv.getUrl().contains("scope=read_dmps+edit_dmps"));
  }
}
