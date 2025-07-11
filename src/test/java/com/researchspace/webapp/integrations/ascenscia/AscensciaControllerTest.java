package com.researchspace.webapp.integrations.ascenscia;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.springframework.http.ResponseEntity;
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

    ascensciaController.setUserManager(userManager);
  }

  @Test
  public void whenAuthenticationSuccess_thenReturnAuthResponseAndSaveToken() {
    when(userManager.getAuthenticatedUserInSession()).thenReturn(user);
    when(user.getUsername()).thenReturn("rspaceuser");
    when(ascensciaClient.authenticate(eq(connectDTO), any(User.class))).thenReturn(authResponseDTO);

    ResponseEntity<?> responseEntity = ascensciaController.connect(connectDTO);

    assertEquals(HttpStatus.CREATED, responseEntity.getStatusCode());
    assertEquals(authResponseDTO, responseEntity.getBody());

    verify(userConnectionManager).save(userConnectionCaptor.capture());
    UserConnection savedConnection = userConnectionCaptor.getValue();
    assertEquals("test-jwt-token", savedConnection.getAccessToken());
    assertEquals("Ascenscia token", savedConnection.getDisplayName());
  }

  @Test
  public void whenClientReturnsUnauthorized_thenReturnErrorResponse() {
    when(userManager.getAuthenticatedUserInSession()).thenReturn(user);
    when(ascensciaClient.authenticate(eq(connectDTO), any(User.class)))
        .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

    ResponseEntity<?> responseEntity = ascensciaController.connect(connectDTO);

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, responseEntity.getStatusCode());
    assertEquals("Unable to generate API key", responseEntity.getBody());
  }

  @Test
  public void whenClientReturnsBadRequest_thenReturnErrorResponse() {
    when(userManager.getAuthenticatedUserInSession()).thenReturn(user);
    when(ascensciaClient.authenticate(eq(connectDTO), any(User.class)))
        .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad request"));

    ResponseEntity<?> responseEntity = ascensciaController.connect(connectDTO);

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, responseEntity.getStatusCode());
    assertEquals("Unable to generate API key", responseEntity.getBody());
  }

  @Test
  public void whenExceptionThrownByClient_thenReturnErrorResponse() {
    when(userManager.getAuthenticatedUserInSession()).thenReturn(user);
    when(ascensciaClient.authenticate(eq(connectDTO), any(User.class)))
        .thenThrow(new RuntimeException("Unexpected error"));

    ResponseEntity<?> responseEntity = ascensciaController.connect(connectDTO);

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, responseEntity.getStatusCode());
    assertEquals("Unable to generate API key", responseEntity.getBody());
  }

  @Test
  public void whenConnectDTOIsNull_thenReturnBadRequest() {
    ResponseEntity<?> responseEntity = ascensciaController.connect(null);

    assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
    assertEquals("ConnectDTO must not be null", responseEntity.getBody());
  }

  @Test
  public void whenUsernameIsEmpty_thenReturnBadRequest() {
    connectDTO.setUsername("");

    ResponseEntity<?> responseEntity = ascensciaController.connect(connectDTO);

    assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
    assertEquals("Username must not be empty", responseEntity.getBody());
  }

  @Test
  public void whenPasswordIsEmpty_thenReturnBadRequest() {
    connectDTO.setPassword("");

    ResponseEntity<?> responseEntity = ascensciaController.connect(connectDTO);

    assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
    assertEquals("Password must not be empty", responseEntity.getBody());
  }

  @Test
  public void whenOrganizationIsEmpty_thenReturnBadRequest() {
    connectDTO.setOrganization("");

    ResponseEntity<?> responseEntity = ascensciaController.connect(connectDTO);

    assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
    assertEquals("Organization must not be empty", responseEntity.getBody());
  }
}
