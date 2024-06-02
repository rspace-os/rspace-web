package com.researchspace.files.service;

import java.util.Optional;

/** Gets an external file store, if one is available and accessible by the user */
public interface ExternalFileStoreLocator {

  /**
   * Gets an external filestore if one is configured. <br>
   * Else returns Optional.empty() .
   *
   * @param rspaceUserName
   * @return
   */
  Optional<ExternalFileStoreWithCredentials> getExternalFileStoreForUser(String rspaceUserName);

  ExternalFileStoreProvider getExternalProvider();

  /** No-op implementation for when there is no external file store configured. */
  public static final ExternalFileStoreLocator NOOP_ExternalFileStoreLocator =
      new ExternalFileStoreLocator() {

        @Override
        public Optional<ExternalFileStoreWithCredentials> getExternalFileStoreForUser(
            String rspaceUserName) {
          return Optional.empty();
        }

        @Override
        public ExternalFileStoreProvider getExternalProvider() {
          return ExternalFileStoreProvider.NO_OP;
        }
      };
}
