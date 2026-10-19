package com.researchspace.webapp.integrations.protocolsio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.service.impl.ShiroTestUtils;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.shiro.session.mgt.SimpleSession;
import org.apache.shiro.subject.Subject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.view.RedirectView;

@ExtendWith(MockitoExtension.class)
@Slf4j
public class ProtocolsIOOAuthTest {

  private static final String PROTOCOLSIO_ACCESS_TOKEN_URL =
      "https://www.protocols.io/api/v3/oauth/token";
  private static final String PROTOCOLSIO_AUTH_URL =
      "https://www.protocols.io/api/v3/oauth/authorize";

  @Mock UserConnectionManager userConnMgr;
  @Mock IPropertyHolder properties;
  @Mock Subject subjct;
  static ShiroTestUtils shiroUtils;

  @InjectMocks ProtocolsIO_OAuthController ctrller;

  @BeforeEach
  public void setUp() throws Exception {
    ctrller.setMessageSource(new MessageSourceUtils(new JsonMessageSource()));
    shiroUtils = new ShiroTestUtils();
    shiroUtils.setSubject(subjct);
    ReflectionTestUtils.setField(
        ctrller, "protocolsioAccessTokenUrl", PROTOCOLSIO_ACCESS_TOKEN_URL);
    ReflectionTestUtils.setField(ctrller, "protocolsioAuthUrl", PROTOCOLSIO_AUTH_URL);
  }

  @AfterEach
  public void tearDown() throws Exception {
    shiroUtils.clearSubject();
  }

  @Test
  public void connect() throws MalformedURLException {
    Mockito.when(subjct.getSession()).thenReturn(new SimpleSession());
    Mockito.when(properties.getServerUrl()).thenReturn("http://somerspace.com");
    RedirectView view = ctrller.connect();
    // assert is valid URL syntax
    URL url = new URL(view.getUrl());
    assertNotNull(url);
    List<NameValuePair> nvps = URLEncodedUtils.parse(view.getUrl(), Charset.forName("UTF-8"));
    assertThat(nvps).anyMatch(nvp -> nvp.getName().equals("scope"));
    assertThat(nvps).anyMatch(nvp -> nvp.getName().equals("state"));
    assertThat(nvps).anyMatch(nvp -> nvp.getName().equals("redirect_url"));
  }
}
