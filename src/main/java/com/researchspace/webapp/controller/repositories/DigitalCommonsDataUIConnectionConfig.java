package com.researchspace.webapp.controller.repositories;

import static com.researchspace.service.IntegrationsHandler.DIGITAL_COMMONS_DATA_APP_NAME;

import com.researchspace.model.User;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.webapp.integrations.digitalcommonsdata.DigitalCommonsDataController;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.Principal;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.support.BindingAwareModelMap;

@Slf4j
public class DigitalCommonsDataUIConnectionConfig implements RSpaceRepoConnectionConfig {

  private User subject;
  private UserConnectionManager userConnectionManager;
  private DigitalCommonsDataController digitalCommonsDataController;
  private IPropertyHolder propertyHolder;

  public DigitalCommonsDataUIConnectionConfig(
      DigitalCommonsDataController digitalCommonsDataController,
      UserConnectionManager source,
      User subject,
      IPropertyHolder propertyHolder) {
    this.subject = subject;
    this.userConnectionManager = source;
    this.propertyHolder = propertyHolder;
    this.digitalCommonsDataController = digitalCommonsDataController;
  }

  @Override
  public Optional<URL> getRepositoryURL() {
    try {
      return Optional.of(new URL(this.propertyHolder.getDigitalCommonsDataBaseUrl()));
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException(
          "Couldn't create Digital Commons Data repositoryURL: " + e.getMessage());
    }
  }

  @Override
  public String getApiKey() {
    Optional<UserConnection> optUserConnection =
        userConnectionManager.findByUserNameProviderName(
            subject.getUsername(), DIGITAL_COMMONS_DATA_APP_NAME);
    Principal principal = () -> subject.getUsername();
    if (optUserConnection.isEmpty()) {
      throw new IllegalArgumentException(
          "No UserConnection exists for: " + DIGITAL_COMMONS_DATA_APP_NAME);
    }
    String accessToken = optUserConnection.get().getAccessToken();

    if (!digitalCommonsDataController.isConnectionAlive(principal)) {
      digitalCommonsDataController.refreshToken(new BindingAwareModelMap(), principal);
      accessToken =
          userConnectionManager
              .findByUserNameProviderName(subject.getUsername(), DIGITAL_COMMONS_DATA_APP_NAME)
              .get()
              .getAccessToken();
    }
    return accessToken;
  }
}
