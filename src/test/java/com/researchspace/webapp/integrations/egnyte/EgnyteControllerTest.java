package com.researchspace.webapp.integrations.egnyte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.service.IntegrationsHandler;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.webapp.controller.AjaxReturnObject;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;

public class EgnyteControllerTest extends SpringTransactionalTest {

  @Autowired private EgnyteController controller;

  @Autowired private UserConnectionManager userConnectionManager;

  @Autowired private EgnyteAuthConnector autowiredConnector;
  private EgnyteAuthConnector mockConnector;

  @Before
  public void setUp() {
    mockConnector = Mockito.mock(EgnyteAuthConnectorImpl.class);
    controller.setEgnyteConnector(mockConnector);
  }

  @After
  public void tearDown() {
    controller.setEgnyteConnector(autowiredConnector);
  }

  @Test
  public void testConnectingUserToFilestore() throws Exception {

    User user = createAndSaveRandomUser();
    Principal mockPrincipal = new MockPrincipal(user.getUsername());

    Optional<UserConnection> noConnection =
        userConnectionManager.findByUserNameProviderName(
            user.getUsername(), IntegrationsHandler.EGNYTE_APP_NAME);
    assertFalse(noConnection.isPresent(), "no egnyte connection expected initially");

    // mock connector responses
    Map<String, Object> accessTokenMockResponse = new HashMap<>();
    accessTokenMockResponse.put("access_token", "dummyToken");
    accessTokenMockResponse.put("expires_in", -1);
    when(mockConnector.queryForEgnyteAccessToken("dummyUser", "dummyPassword"))
        .thenReturn(accessTokenMockResponse);

    Map<String, Object> tokenVerifyMockResponse = new HashMap<>();
    tokenVerifyMockResponse.put("username", "dummyUser");
    tokenVerifyMockResponse.put("id", 1);
    when(mockConnector.queryForEgnyteUserInfoWithAccessToken("dummyToken"))
        .thenReturn(tokenVerifyMockResponse);

    // try connecting the user
    AjaxReturnObject<Boolean> connectResult =
        controller.connectUserToEgnyteFilestore(
            "dummyUser", "dummyPassword", new MockHttpSession(), mockPrincipal);
    assertTrue(connectResult.getData());

    // check if user connection saved
    Optional<UserConnection> savedConnectionOpt =
        userConnectionManager.findByUserNameProviderName(
            user.getUsername(), IntegrationsHandler.EGNYTE_APP_NAME);
    assertTrue(savedConnectionOpt.isPresent());
    assertEquals("dummyToken", savedConnectionOpt.get().getAccessToken());

    // now disconnect and confirm the user connection is removed
    AjaxReturnObject<Boolean> disconnectResult =
        controller.disconnectUserFromEgnyteFilestore(new MockHttpSession(), mockPrincipal);
    assertTrue(disconnectResult.getData());

    Optional<UserConnection> removedConnectionOpt =
        userConnectionManager.findByUserNameProviderName(
            user.getUsername(), IntegrationsHandler.EGNYTE_APP_NAME);
    assertFalse(removedConnectionOpt.isPresent());
  }
}
