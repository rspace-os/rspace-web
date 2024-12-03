package com.researchspace.webapp.integrations.fieldmark;

import static com.researchspace.service.IntegrationsHandler.FIELDMARK_APP_NAME;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.fieldmark.model.FieldmarkNotebook;
import com.researchspace.model.User;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;

@Ignore(
    "We leave the test Disabled so we can potentially run it manually by adding the bearer token")
public class FieldmarkApiControllerRealConnectionTest extends SpringTransactionalTest {

  private @Autowired FieldmarkApiController fieldmarkApiController;
  private @Autowired UserConnectionManager userConnectionManager;

  private static final FieldmarkApiImportRequest IMPORT_REQ =
      new FieldmarkApiImportRequest("1726126204618-rspace-igsn-demo");
  private static final String ACCESS_TOKEN = "________PASTE_TOKEN_HERE________";
  private BindingResult mockBindingResult = mock(BindingResult.class);
  private User user;

  @Before
  public void setUp() {
    user = createInitAndLoginAnyUser();
    assertTrue(user.isContentInitialized());

    UserConnection actualConnection = new UserConnection();
    actualConnection.setId(
        new UserConnectionId(user.getUsername(), FIELDMARK_APP_NAME, "ProviderUserIdNotNeeded"));
    actualConnection.setAccessToken(ACCESS_TOKEN);
    actualConnection.setRefreshToken("REFRESH_TOKEN");
    actualConnection.setExpireTime(299L);
    actualConnection.setDisplayName("Fieldmark access token");
    userConnectionManager.save(actualConnection);
    when(mockBindingResult.hasErrors()).thenReturn(false);
  }

  @Test
  public void testGetNotebooks() throws BindException, IOException, URISyntaxException {
    List<FieldmarkNotebook> result = fieldmarkApiController.getNotebooks(user);
    assertNotNull(result);
  }

  public void testImportNotebook() throws BindException {
    FieldmarkApiImportResult result =
        fieldmarkApiController.importNotebook(IMPORT_REQ, mockBindingResult, user);
    assertNotNull(result);
  }
}
