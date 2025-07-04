package com.researchspace.webapp.integrations.ascenscia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.webapp.integrations.ascenscia.client.AscensciaClient;
import com.researchspace.webapp.integrations.ascenscia.dto.AuthResponseDTO;
import com.researchspace.webapp.integrations.ascenscia.dto.ConnectDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
public class AscensciaClientTest {

  @Mock private RestTemplate restTemplate;

  @InjectMocks private AscensciaClient ascensciaClient;

  private ConnectDTO connectDTO;
  private User user;
  private AuthResponseDTO authResponseDTO;

  @BeforeEach
  public void setUp() {
    connectDTO = new ConnectDTO();
    connectDTO.setUsername("testuser");
    connectDTO.setPassword("testpassword");
    connectDTO.setOrganization("testorg");

    user = new User();
    user.setUsername("rspaceuser");

    authResponseDTO = new AuthResponseDTO();
    authResponseDTO.setAccessToken("test-jwt-token");
  }

  @Test
  public void whenAuthenticationIsSuccessful_thenReturnToken() {
    when(restTemplate.postForEntity(
            eq("https://ascenscia.app/api/auth/login"),
            any(HttpEntity.class),
            eq(AuthResponseDTO.class)))
        .thenReturn(new ResponseEntity<>(authResponseDTO, HttpStatus.OK));

    AuthResponseDTO response = ascensciaClient.authenticate(connectDTO, user);

    assertEquals(authResponseDTO, response);
  }

  @ParameterizedTest
  @EnumSource(
      value = HttpStatus.class,
      names = {"UNAUTHORIZED", "BAD_REQUEST", "INTERNAL_SERVER_ERROR"})
  public void whenAuthenticationError_thenThrowException(HttpStatus errorStatus) {
    when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(AuthResponseDTO.class)))
        .thenReturn(new ResponseEntity<>(null, errorStatus));

    assertThrows(HttpClientErrorException.class, () -> ascensciaClient.authenticate(connectDTO, user));
  }

  @Test
  public void whenGeneralExceptionDuringAuthentication_thenThrow500() {
    when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(AuthResponseDTO.class)))
        .thenThrow(new RestClientException("Connection error"));

    HttpClientErrorException exception =
        assertThrows(
            HttpClientErrorException.class, () -> ascensciaClient.authenticate(connectDTO, user));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getStatusCode());
    assertEquals("500 Error connecting to Ascenscia API", exception.getMessage());
  }
}
