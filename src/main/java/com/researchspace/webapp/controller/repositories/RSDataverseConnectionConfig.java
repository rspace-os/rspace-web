package com.researchspace.webapp.controller.repositories;

import com.researchspace.model.apps.AppConfigElementSet;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;

public class RSDataverseConnectionConfig implements RSpaceRepoConnectionConfig {

  AppConfigElementSet source;

  public RSDataverseConnectionConfig(AppConfigElementSet source) {
    this.source = source;
  }

  @Override
  public Optional<URL> getRepositoryURL() throws MalformedURLException {
    String dataverse_url = source.findElementByPropertyName("DATAVERSE_URL").getValue();
    URL repoURL = new URL(dataverse_url);
    return Optional.of(repoURL);
  }

  @Override
  public String getApiKey() {
    return source.findElementByPropertyName("DATAVERSE_APIKEY").getValue();
  }

  @Override
  public Optional<String> getRepoName() {
    return Optional.of(source.findElementByPropertyName("DATAVERSE_ALIAS").getValue());
  }
}
