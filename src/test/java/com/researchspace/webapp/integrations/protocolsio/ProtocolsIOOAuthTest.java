package com.researchspace.webapp.integrations.protocolsio;

import static com.researchspace.service.IntegrationsHandler.PROTOCOLS_IO_APP_NAME;
import static com.researchspace.webapp.integrations.protocolsio.ProtocolsIO_OAuthController.PROTOCOLSIO_ACCESS_TOKEN_URL;
import static com.researchspace.webapp.integrations.protocolsio.ProtocolsIO_OAuthController.REFRESH_TOKEN_EXPIRED_CODE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;

import com.researchspace.core.util.JacksonUtil;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.record.TestFactory;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.service.impl.ShiroTestUtils;
import com.researchspace.webapp.integrations.protocolsio.ProtocolsIO_OAuthController.AccessToken;
import com.researchspace.webapp.integrations.protocolsio.ProtocolsIO_OAuthController.ClientError;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.shiro.session.mgt.SimpleSession;
import org.apache.shiro.subject.Subject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.OngoingStubbing;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.view.RedirectView;

@Slf4j
public class ProtocolsIOOAuthTest {

  private static final String USERNAME = "user";

  @Rule public MockitoRule rule = MockitoJUnit.rule();

  @Mock UserConnectionManager userConnMgr;
  @Mock RestTemplate restTemplate;
  @Mock IPropertyHolder properties;
  @Mock Subject subjct;
  static ShiroTestUtils shiroUtils;

  @InjectMocks ProtocolsIO_OAuthController ctrller;

  @Before
  public void setUp() throws Exception {
    shiroUtils = new ShiroTestUtils();
    shiroUtils.setSubject(subjct);
    Mockito.when(subjct.getSession()).thenReturn(new SimpleSession());
  }

  @After
  public void tearDown() throws Exception {
    shiroUtils.clearSubject();
  }

  @Value
  public class MockPrincipal implements Principal {
    private String name;
  }

  @Test
  public void connect() throws MalformedURLException {
    Mockito.when(properties.getServerUrl()).thenReturn("http://somerspace.com");
    RedirectView view = ctrller.connect();
    // assert is valid URL syntax
    URL url = new URL(view.getUrl());
    assertNotNull(url);
    List<NameValuePair> nvps = URLEncodedUtils.parse(view.getUrl(), Charset.forName("UTF-8"));
    assertTrue(nvps.stream().anyMatch(nvp -> nvp.getName().equals("scope")));
    assertTrue(nvps.stream().anyMatch(nvp -> nvp.getName().equals("state")));
    assertTrue(nvps.stream().anyMatch(nvp -> nvp.getName().equals("redirect_url")));
  }

  @Test
  public void refreshTokenReturnsBadREquestIfNoRefreshTokenExists() {
    Mockito.when(userConnMgr.findByUserNameProviderName(USERNAME, PROTOCOLS_IO_APP_NAME))
        .thenReturn(Optional.empty());
    assertEquals(
        HttpStatus.BAD_REQUEST, ctrller.refreshToken(new MockPrincipal(USERNAME)).getStatusCode());
  }

  @Test
  public void refreshTokenSuccess() {
    UserConnection validConn = TestFactory.createUserConnection(USERNAME);
    mockValidUserConnection(validConn);
    afterValidRequest().thenReturn(validAccessToken());
    ResponseEntity<String> resp = ctrller.refreshToken(new MockPrincipal(USERNAME));
    verify(userConnMgr).save(validConn);
    assertEquals(HttpStatus.OK, resp.getStatusCode());
    assertEquals("newaccess", validConn.getAccessToken());
    assertEquals("newrefresh", validConn.getRefreshToken());
  }

  @Test
  public void refreshTokenExpired() {
    UserConnection validConn = TestFactory.createUserConnection(USERNAME);
    mockValidUserConnection(validConn);
    byte[] clientErrorBytes = setupClientErrorBody(REFRESH_TOKEN_EXPIRED_CODE);
    afterValidRequest().thenThrow(clientErrorException(clientErrorBytes));

    ResponseEntity<String> resp = ctrller.refreshToken(new MockPrincipal(USERNAME));
    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, resp.getStatusCode());
    assertConnectionNotUpdated(validConn);
  }

  @Test
  public void refreshToken_someOtherClientErrorHandled() {
    UserConnection validConn = TestFactory.createUserConnection(USERNAME);
    mockValidUserConnection(validConn);
    byte[] clientErrorBytes = setupClientErrorBody(-1);
    afterValidRequest().thenThrow(clientErrorException(clientErrorBytes));
    ResponseEntity<String> resp = ctrller.refreshToken(new MockPrincipal(USERNAME));
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getStatusCode());
    assertConnectionNotUpdated(validConn);
  }

  @Test
  public void refreshToken_someOtherServerErrorHandled() {
    UserConnection validConn = TestFactory.createUserConnection(USERNAME);
    mockValidUserConnection(validConn);
    byte[] clientErrorBytes = setupClientErrorBody(-1);
    afterValidRequest().thenThrow(serverErrorException(clientErrorBytes));
    ResponseEntity<String> resp = ctrller.refreshToken(new MockPrincipal(USERNAME));
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getStatusCode());
    assertConnectionNotUpdated(validConn);
  }

  @Test
  public void refreshToken_nonParsableClientErrorHandled() {
    UserConnection validConn = TestFactory.createUserConnection(USERNAME);
    mockValidUserConnection(validConn);
    byte[] clientErrorBytes = new byte[] {0, 1, 2};
    afterValidRequest().thenThrow(clientErrorException(clientErrorBytes));
    ResponseEntity<String> resp = ctrller.refreshToken(new MockPrincipal(USERNAME));
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getStatusCode());
    assertConnectionNotUpdated(validConn);
  }

  private HttpClientErrorException clientErrorException(byte[] clientErrorBytes) {
    return new HttpClientErrorException(
        HttpStatus.BAD_REQUEST, "some message", clientErrorBytes, utf8());
  }

  private HttpServerErrorException serverErrorException(byte[] clientErrorBytes) {
    return new HttpServerErrorException(
        HttpStatus.INTERNAL_SERVER_ERROR, "some message", clientErrorBytes, utf8());
  }

  private void assertConnectionNotUpdated(UserConnection validConn) {
    verify(userConnMgr, Mockito.never()).save(validConn);
  }

  private OngoingStubbing<ResponseEntity<AccessToken>> afterValidRequest() {
    return Mockito.when(
        restTemplate.exchange(
            Mockito.eq(PROTOCOLSIO_ACCESS_TOKEN_URL),
            Mockito.eq(HttpMethod.POST),
            Mockito.any(HttpEntity.class),
            Mockito.eq(AccessToken.class)));
  }

  private byte[] setupClientErrorBody(int refreshTokenExpiredCode) {
    ClientError error = new ClientError(refreshTokenExpiredCode, "some message");
    return JacksonUtil.toJson(error).getBytes(utf8());
  }

  private void mockValidUserConnection(UserConnection validConn) {
    Mockito.when(userConnMgr.findByUserNameProviderName(USERNAME, PROTOCOLS_IO_APP_NAME))
        .thenReturn(Optional.of(validConn));
  }

  private Charset utf8() {
    return Charset.forName("UTF-8");
  }

  private ResponseEntity<AccessToken> validAccessToken() {
    AccessToken at = new AccessToken();
    at.setAccessToken("newaccess");
    at.setRefreshToken("newrefresh");
    at.setExpiresIn(1000L);
    return new ResponseEntity<AccessToken>(at, HttpStatus.OK);
  }

  private HttpHeaders setUpHeadersWithAccessToken(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
    headers.add("Authorization", "Bearer " + token);
    return headers;
  }
}
