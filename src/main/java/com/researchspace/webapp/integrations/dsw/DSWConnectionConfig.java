package com.researchspace.webapp.integrations.dsw;

import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.webapp.controller.repositories.RSpaceRepoConnectionConfig;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;

public class DSWConnectionConfig implements RSpaceRepoConnectionConfig {

  AppConfigElementSet source;

  public DSWConnectionConfig(AppConfigElementSet source) {
    this.source = source;
  }

  @Override
  public Optional<URL> getRepositoryURL() throws MalformedURLException {
    String dswUrl = source.findElementByPropertyName("DSW_URL").getValue();
    URL repoURL = new URL(dswUrl);
    return Optional.of(repoURL);
  }

  // The API key is encrypted and is not stored in the AppConfigElement table.
  @Override
  public String getApiKey() {
    return null;
  }

  @Override
  public Optional<String> getRepoName() {
    return Optional.of(source.findElementByPropertyName("DSW_ALIAS").getValue());
  }
}
