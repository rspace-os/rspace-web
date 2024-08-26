package com.researchspace.webapp.controller.repositories;

import static com.researchspace.service.IntegrationsHandler.DIGITAL_COMMONS_DATA_APP_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.webapp.integrations.digitalcommonsdata.DigitalCommonsDataController;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.client.RestTemplate;

class DigitalCommonsDataUIConnectionConfigTest {

  @Mock private User subject;
  @Mock private UserConnectionManager userConnectionManager;
  @Mock private DigitalCommonsDataController digitalCommonsDataController;
  @Mock private IPropertyHolder propertyHolder;
  @Mock private RestTemplate restTemplate;
  private DigitalCommonsDataUIConnectionConfig underTest;
  private static final String ACCESS_TOKEN = "<ACCESS_TOKEN>";
  private static final String USERNAME = "user1";

  @BeforeEach
  void init() {
    MockitoAnnotations.openMocks(this);
    when(subject.getUsername()).thenReturn(USERNAME);
    underTest =
        new DigitalCommonsDataUIConnectionConfig(
            digitalCommonsDataController, userConnectionManager, subject, propertyHolder);
  }

  @Test
  void getRepositoryURLSucceed() throws MalformedURLException {
    String VALID_URL = "https://valid.url";
    when(propertyHolder.getDigitalCommonsDataBaseUrl()).thenReturn(VALID_URL);
    assertEquals(new URL(VALID_URL), underTest.getRepositoryURL().get());
  }

  @Test
  void getRepositoryURLFails() {
    when(propertyHolder.getDigitalCommonsDataBaseUrl()).thenReturn("ht://invalid.url");

    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> underTest.getRepositoryURL());

    assertEquals(
        "Couldn't create Digital Commons Data repositoryURL: unknown protocol: ht",
        thrown.getMessage());
  }

  @Test
  void getApiKeySucceedWithoutRefreshingToken() {
    UserConnection validUserConnection = new UserConnection();
    validUserConnection.setAccessToken(ACCESS_TOKEN);

    when(userConnectionManager.findByUserNameProviderName(
            eq(USERNAME), eq(DIGITAL_COMMONS_DATA_APP_NAME)))
        .thenReturn(Optional.of(validUserConnection));
    when(digitalCommonsDataController.isConnectionAlive(any())).thenReturn(true);

    String actualApiKey = underTest.getApiKey();

    verify(userConnectionManager)
        .findByUserNameProviderName(eq(USERNAME), eq(DIGITAL_COMMONS_DATA_APP_NAME));
    verify(digitalCommonsDataController).isConnectionAlive(any());
    verifyNoMoreInteractions(digitalCommonsDataController);
    assertEquals(ACCESS_TOKEN, actualApiKey);
  }

  @Test
  void getApiKeySucceedByRefreshingToken() {
    UserConnection validUserConnection = new UserConnection();
    validUserConnection.setAccessToken(ACCESS_TOKEN);

    when(userConnectionManager.findByUserNameProviderName(
            eq(USERNAME), eq(DIGITAL_COMMONS_DATA_APP_NAME)))
        .thenReturn(Optional.of(validUserConnection));
    when(digitalCommonsDataController.isConnectionAlive(any())).thenReturn(false);

    String actualApiKey = underTest.getApiKey();

    verify(userConnectionManager, times(2))
        .findByUserNameProviderName(eq(USERNAME), eq(DIGITAL_COMMONS_DATA_APP_NAME));
    verify(digitalCommonsDataController).isConnectionAlive(any());
    verify(digitalCommonsDataController).refreshToken(any(), any());

    assertEquals(ACCESS_TOKEN, actualApiKey);
  }

  @Test
  void getApiKeyFailsBecauseNoInitialToken() {
    when(userConnectionManager.findByUserNameProviderName(
            eq(USERNAME), eq(DIGITAL_COMMONS_DATA_APP_NAME)))
        .thenReturn(Optional.empty());

    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> underTest.getApiKey());

    assertEquals(
        "No UserConnection exists for: " + DIGITAL_COMMONS_DATA_APP_NAME, thrown.getMessage());
  }
}
