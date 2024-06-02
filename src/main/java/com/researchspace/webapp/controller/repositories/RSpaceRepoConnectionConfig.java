package com.researchspace.webapp.controller.repositories;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;

/** Adaptor translating from RSpace settings for repository connection. */
public interface RSpaceRepoConnectionConfig {

  /**
   * The repoURL. This may not be required if the repository only has one URL (e.g. Figshare)
   *
   * @return
   * @throws MalformedURLException
   */
  default Optional<URL> getRepositoryURL() throws MalformedURLException {
    return Optional.empty();
  }

  /**
   * Access token or API Key
   *
   * @return
   */
  String getApiKey();

  /**
   * Optional password for acquiring an API token
   *
   * @return
   */
  default Optional<String> getPassword() {
    return Optional.empty();
  }

  /**
   * Optional specifier for a sub-repo identifier or name
   *
   * @return
   */
  default Optional<String> getRepoName() {
    return Optional.empty();
  }
}
