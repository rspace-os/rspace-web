package com.researchspace.webapp.integrations.ascenscia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.service.UserManager;
import com.researchspace.webapp.integrations.ascenscia.client.AscensciaClient;
import com.researchspace.webapp.integrations.ascenscia.dto.AuthResponseDTO;
import com.researchspace.webapp.integrations.ascenscia.dto.ConnectDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

@ExtendWith(MockitoExtension.class)
public class AscensciaControllerTest {

  @Mock private AscensciaClient ascensciaClient;

  @Mock private UserConnectionManager userConnectionManager;

  @Mock private UserManager userManager;

  @Mock private User user;

  @InjectMocks private AscensciaController ascensciaController;

  @Captor private ArgumentCaptor<UserConnection> userConnectionCaptor;

  private ConnectDTO connectDTO;
  private AuthResponseDTO authResponseDTO;

  @BeforeEach
  public void setUp() {
    connectDTO = new ConnectDTO();
    connectDTO.setUsername("testuser");
    connectDTO.setPassword("testpassword");
    connectDTO.setOrganization("testorg");

    authResponseDTO = new AuthResponseDTO();
    authResponseDTO.setAccessToken("test-jwt-token");

    // Set userManager in BaseController
    ascensciaController.setUserManager(userManager);

    // Always return the user when getAuthenticatedUserInSession is called
    when(userManager.getAuthenticatedUserInSession()).thenReturn(user);
  }

  @Test
  public void whenAuthenticationSuccess_thenReturnAuthResponseAndSaveToken() {
    when(user.getUsername()).thenReturn("rspaceuser");
    when(ascensciaClient.authenticate(eq(connectDTO), any(User.class))).thenReturn(authResponseDTO);

    AuthResponseDTO response = ascensciaController.connect(connectDTO);

    assertEquals(authResponseDTO, response);

    verify(userConnectionManager).save(userConnectionCaptor.capture());
    UserConnection savedConnection = userConnectionCaptor.getValue();
    assertEquals("test-jwt-token", savedConnection.getAccessToken());
    assertEquals("Ascenscia token", savedConnection.getDisplayName());
  }

  @Test
  public void whenClientReturnsUnauthorized_thenThrowException() {
    when(ascensciaClient.authenticate(eq(connectDTO), any(User.class)))
        .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> {
              ascensciaController.connect(connectDTO);
            });

    assertEquals("Invalid credentials", exception.getMessage());
  }

  @Test
  public void whenClientReturnsBadRequest_thenThrowException() {
    when(ascensciaClient.authenticate(eq(connectDTO), any(User.class)))
        .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad request"));

    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> {
              ascensciaController.connect(connectDTO);
            });

    assertEquals("400 BAD_REQUEST Bad request", exception.getMessage());
  }

  @Test
  public void whenExceptionThrownByClient_thenThrowException() {
    when(ascensciaClient.authenticate(eq(connectDTO), any(User.class)))
        .thenThrow(new RuntimeException("Unexpected error"));

    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> {
              ascensciaController.connect(connectDTO);
            });

    assertEquals("An error occurred while connecting to Ascenscia", exception.getMessage());
  }
}
