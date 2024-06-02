package com.researchspace.webapp.controller.repositories;

import static com.researchspace.service.IntegrationsHandler.DRYAD_APP_NAME;

import com.researchspace.model.User;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.UserConnectionManager;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;

public class DryadUIConnectionConfig implements RSpaceRepoConnectionConfig {

  private User subject;
  private UserConnectionManager source;
  private IPropertyHolder propertyHolder;

  public DryadUIConnectionConfig(
      UserConnectionManager source, User subject, IPropertyHolder propertyHolder) {
    this.subject = subject;
    this.source = source;
    this.propertyHolder = propertyHolder;
  }

  @Override
  public Optional<URL> getRepositoryURL() throws MalformedURLException {
    try {
      return Optional.of(new URL(this.propertyHolder.getDryadBaseUrl() + "/api/v2"));
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException("Couldn't create Dryad repositoryURL " + e.getMessage());
    }
  }

  @Override
  public String getApiKey() {
    return source
        .findByUserNameProviderName(subject.getUsername(), DRYAD_APP_NAME)
        .orElseThrow(
            () -> new IllegalArgumentException("No UserConnection exits for: " + DRYAD_APP_NAME))
        .getAccessToken();
  }
}
