package com.researchspace.files.service;

import com.researchspace.model.oauth.UserConnection;
import com.researchspace.service.UserConnectionManager;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class ExternalFileStoreLocatorImpl implements ExternalFileStoreLocator {

  private ExternalFileStoreProvider externalProvider;
  private @Autowired UserConnectionManager userConnectionMgr;
  private ExternalFileStore extFileStore;

  public ExternalFileStoreLocatorImpl(
      ExternalFileStoreProvider externalProvider, ExternalFileStore extFileStore) {
    super();
    this.externalProvider = externalProvider;
    this.extFileStore = extFileStore;
  }

  @Override
  public Optional<ExternalFileStoreWithCredentials> getExternalFileStoreForUser(
      String rspaceUserName) {
    Optional<UserConnection> rc =
        userConnectionMgr.findByUserNameProviderName(rspaceUserName, externalProvider.name());
    return rc.map(this::extFs).orElse(logNoFs(rspaceUserName));
  }

  private Optional<ExternalFileStoreWithCredentials> extFs(UserConnection uc) {
    return Optional.of(new ExternalFileStoreWithCredentials(extFileStore, uc));
  }

  Optional<ExternalFileStoreWithCredentials> logNoFs(String rspaceUserName) {
    log.warn("No UserConnection set for {}", rspaceUserName);
    return Optional.empty();
  }

  void setUserConnectionMgr(UserConnectionManager userConnectionMgr) {
    this.userConnectionMgr = userConnectionMgr;
  }

  public ExternalFileStoreProvider getExternalProvider() {
    return externalProvider;
  }
}
