package com.researchspace.files.service;

import com.researchspace.model.oauth.UserConnection;
import lombok.Value;

/**
 * Wrapper class that contains a an ExternalFileStore and a user's credentials for accessing that
 * store.
 */
@Value
public class ExternalFileStoreWithCredentials {

  private ExternalFileStore extFileStore;
  private UserConnection userConnection;
}
