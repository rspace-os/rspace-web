package com.researchspace.webapp.integrations.omero;

import static com.researchspace.service.IntegrationsHandler.OMERO_APP_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

import com.researchspace.model.oauth.UserConnection;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.service.UserManager;
import com.researchspace.testutils.TestFactory;
import com.researchspace.webapp.controller.IgnoreInLoggingInterceptor;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

class OmeroAuthControllerTest {

  @Mock private UserConnectionManager userConnectionManager;
  @Mock private UserManager userManager;
  @Mock private IPropertyHolder properties;
  @Mock private JSONClient jsonClient;

  @InjectMocks
  private OmeroAuthController controller =
      new OmeroAuthController() {
        @Override
        JSONClient newJsonClient() {
          return jsonClient;
        }
      };

  @BeforeEach
  void setUp() {
    openMocks(this);
    when(userManager.getAuthenticatedUserInSession())
        .thenReturn(TestFactory.createAnyUser("rspaceuser"));
    ReflectionTestUtils.setField(controller, "omeroServerName", "omero-server");
    ReflectionTestUtils.setField(
        controller, "messages", new MessageSourceUtils(new JsonMessageSource()));
  }

  @Test
  void disconnectDeletesTheStoredCredentials() {
    controller.disconnect();

    verify(userConnectionManager).deleteByUserAndProvider("rspaceuser", OMERO_APP_NAME);
  }

  @Test
  void connectReplacesAnyExistingCredentialsWithTheNewOnes() throws Exception {
    when(jsonClient.getServers()).thenReturn(Map.of("omero-server", 1));
    when(jsonClient.login(anyString(), anyString(), anyInt())).thenReturn(anOmeroLoginResponse());
    when(properties.getServerUrl()).thenReturn("http://localhost:8080");

    ModelAndView mav = controller.connect(new OmeroUser("omerouser", "omeropassword"));

    ArgumentCaptor<UserConnection> saved = ArgumentCaptor.forClass(UserConnection.class);
    // one atomic replace, not a delete followed by a separate save: a failed save must not leave
    // the user with no connection at all
    verify(userConnectionManager).replaceConnection(saved.capture());
    verify(userConnectionManager, never()).deleteByUserAndProvider(anyString(), anyString());

    verify(jsonClient).login(eq("omerouser"), eq("omeropassword"), anyInt());
    assertEquals("rspaceuser", saved.getValue().getId().getUserId());
    assertEquals(OMERO_APP_NAME, saved.getValue().getId().getProviderId());
    assertEquals("omerouser", saved.getValue().getId().getProviderUserId());
    assertEquals("omerouser_,_omeropassword", saved.getValue().getAccessToken());
    // rank 1 is what the (userId, providerId, rank) unique index relies on to keep OMERO single
    assertEquals(1, saved.getValue().getRank());
    assertEquals(
        "http://localhost:8080/apps/omero/redirect_uri", ((RedirectView) mav.getView()).getUrl());
  }

  @Test
  void connectDoesNotStoreCredentialsWhenOmeroIsUnreachable() throws Exception {
    when(jsonClient.getServers()).thenThrow(new IllegalStateException("omero is down"));

    ModelAndView mav = controller.connect(new OmeroUser("omerouser", "omeropassword"));

    assertNothingStored();
    assertReportsFailure(mav, "omero is down");
  }

  @Test
  void connectDoesNotStoreCredentialsOmeroHasRejected() throws Exception {
    when(jsonClient.getServers()).thenReturn(Map.of("omero-server", 1));
    // JSONClient.login returns null rather than throwing when OMERO refuses the credentials
    when(jsonClient.login(anyString(), anyString(), anyInt())).thenReturn(null);

    ModelAndView mav = controller.connect(new OmeroUser("omerouser", "wrongpassword"));

    assertNothingStored();
    assertReportsFailure(mav, "rejected");
  }

  @Test
  void connectReportsAMisconfiguredServerNameRatherThanNull() throws Exception {
    when(jsonClient.getServers()).thenReturn(Map.of("a-different-server", 1));

    ModelAndView mav = controller.connect(new OmeroUser("omerouser", "omeropassword"));

    assertNothingStored();
    verify(jsonClient, never()).login(anyString(), anyString(), anyInt());
    assertReportsFailure(mav, "omero-server");
  }

  @ParameterizedTest
  @CsvSource({",omeropassword", "omerouser,", "'   ',omeropassword", "omerouser,'   '"})
  void connectRejectsBlankCredentials(String username, String password) throws Exception {
    ModelAndView mav = controller.connect(new OmeroUser(username, password));

    assertNothingStored();
    verify(jsonClient, never()).login(anyString(), anyString(), anyInt());
    assertReportsFailure(mav, "required");
  }

  @ParameterizedTest
  @CsvSource({"'omero_,_user',omeropassword", "omerouser,'omero_,_password'"})
  void connectRejectsCredentialsContainingTheStorageDelimiter(String username, String password)
      throws Exception {
    ModelAndView mav = controller.connect(new OmeroUser(username, password));

    assertNothingStored();
    verify(jsonClient, never()).login(anyString(), anyString(), anyInt());
    assertReportsFailure(mav, "_,_");
  }

  @Test
  void connectExcludesTheOmeroPasswordFromTheRequestLog() throws Exception {
    // LoggingInterceptor is mapped to /** and logs every request parameter, so without this
    // annotation the OMERO password is written to the request log in cleartext
    IgnoreInLoggingInterceptor annotation =
        OmeroAuthController.class
            .getMethod("connect", OmeroUser.class)
            .getAnnotation(IgnoreInLoggingInterceptor.class);

    assertNotNull(annotation, "connect() must suppress its request params from the request log");
    assertTrue(
        List.of(annotation.ignoreRequestParams()).contains("omeropassword"),
        "omeropassword must be among the ignored request params");
  }

  private JsonObject anOmeroLoginResponse() {
    return Json.createObjectBuilder().add("sessionUuid", "a-session").build();
  }

  private void assertNothingStored() {
    verify(userConnectionManager, never()).replaceConnection(any());
    verify(userConnectionManager, never()).save(any());
    verify(userConnectionManager, never()).deleteByUserAndProvider(anyString(), anyString());
  }

  /**
   * The popup broadcasts connectionError back to the Apps page, so it must be present and useful
   */
  private void assertReportsFailure(ModelAndView mav, String expectedInMessage) {
    assertEquals("connect/connected", mav.getViewName());
    String error = (String) mav.getModel().get("connectionError");
    assertNotNull(error, "connectionError must be set so the Apps page reports the failure");
    assertFalse(error.contains("null"), "connectionError should not surface a bare null: " + error);
    assertTrue(
        error.contains(expectedInMessage),
        "expected '" + expectedInMessage + "' in connectionError but got: " + error);
  }
}
