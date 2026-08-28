package com.researchspace.webapp.integrations.omero;

import static com.researchspace.service.IntegrationsHandler.OMERO_APP_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

import com.researchspace.integrations.omero.service.OmeroService;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.service.UserManager;
import com.researchspace.testutils.TestFactory;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class OmeroControllerTest {

  @Mock private UserManager userManager;
  @Mock private OmeroService omeroService;
  @Mock private MessageSourceUtils messages;
  @Mock private UserConnectionManager userConnectionManager;

  private OmeroController controller;

  @BeforeEach
  void setUp() {
    openMocks(this);
    when(userManager.getAuthenticatedUserInSession())
        .thenReturn(TestFactory.createAnyUser("rspaceuser"));
    controller = new OmeroController(userManager, omeroService, messages, userConnectionManager);
  }

  private void storedCredentials(String accessToken) {
    UserConnection conn = new UserConnection();
    conn.setAccessToken(accessToken);
    when(userConnectionManager.findByUserNameProviderName("rspaceuser", OMERO_APP_NAME))
        .thenReturn(Optional.of(conn));
  }

  @Test
  void projectsUseTheCredentialsStoredForTheUser() {
    storedCredentials("omerouser_,_omeropassword");
    when(omeroService.getProjectsAndScreens("omerouser_,_omeropassword", "all"))
        .thenReturn(List.of());

    controller.getProjects("all");

    verify(omeroService).getProjectsAndScreens("omerouser_,_omeropassword", "all");
  }

  @Test
  void projectsAreRejectedWhenTheUserHasNoStoredCredentials() {
    when(userConnectionManager.findByUserNameProviderName("rspaceuser", OMERO_APP_NAME))
        .thenReturn(Optional.empty());
    when(messages.getMessage("apps.omero.errors.notConnected")).thenReturn("not connected");

    RuntimeException e = assertThrows(RuntimeException.class, () -> controller.getProjects("all"));

    assertEquals("not connected", e.getMessage());
  }

  @Test
  void datasetsAreRejectedWhenTheUserHasNoStoredCredentials() {
    when(userConnectionManager.findByUserNameProviderName("rspaceuser", OMERO_APP_NAME))
        .thenReturn(Optional.empty());
    when(messages.getMessage("apps.omero.errors.notConnected")).thenReturn("not connected");

    RuntimeException e =
        assertThrows(RuntimeException.class, () -> controller.getDatasetsForProject(1L));

    assertEquals("not connected", e.getMessage());
  }
}
