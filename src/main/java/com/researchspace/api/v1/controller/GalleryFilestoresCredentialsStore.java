package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.model.NfsUserFileSystem;
import com.researchspace.model.User;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.netfiles.ApiNfsCredentials;
import com.researchspace.netfiles.NfsAuthentication;
import com.researchspace.netfiles.NfsClient;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;

@Service
public class GalleryFilestoresCredentialsStore {

  @Getter(AccessLevel.PROTECTED)
  private Map<NfsUserFileSystem, ApiNfsCredentials> credentialsMapCache = new LinkedHashMap<>();

  @Setter(AccessLevel.PROTECTED)
  private NfsAuthentication nfsAuthentication;

  public GalleryFilestoresCredentialsStore(
      @Autowired @Qualifier("nfsUserPasswordAuthentication") NfsAuthentication nfsAuthentication) {
    this.nfsAuthentication = nfsAuthentication;
  }

  @NotNull
  public NfsClient validateCredentialsAndLoginNfs(
      ApiNfsCredentials credentials,
      BindingResult errors,
      User user,
      NfsFileSystem currentFileSystem)
      throws BindException {

    // add login for the filestore into the Map
    NfsUserFileSystem currentUserFilesystemPair =
        new NfsUserFileSystem(user, currentFileSystem.getId());
    if (!credentialsMapCache.containsKey(currentUserFilesystemPair)
        || (credentials != null
            && StringUtils.isNotBlank(credentials.getUsername())
            && StringUtils.isNotBlank(credentials.getPassword()))) {
      credentialsMapCache.put(
          currentUserFilesystemPair,
          new ApiNfsCredentials(user, credentials.getUsername(), credentials.getPassword()));
    }

    String credentialValidationError =
        nfsAuthentication.validateCredentials(
            credentialsMapCache.get(currentUserFilesystemPair).getUsername(),
            credentialsMapCache.get(currentUserFilesystemPair).getPassword(),
            credentialsMapCache.get(currentUserFilesystemPair).getUser());

    if (credentialValidationError != null) {
      errors.addError(new ObjectError("credentials", credentialValidationError));
      throwBindExceptionIfErrors(errors);
    }

    NfsClient nfsClient = getNfsClientWithStoredCredentials(user, currentFileSystem);
    if (!nfsClient.isUserLoggedIn()) {
      errors.addError(new ObjectError("nfsClient", "User is not logged in"));
      throwBindExceptionIfErrors(errors);
    }
    return nfsClient;
  }

  public NfsClient getNfsClientWithStoredCredentials(User user, NfsFileSystem filesystem) {

    NfsUserFileSystem currentUserFilesystemPair = new NfsUserFileSystem(user, filesystem.getId());
    if (!credentialsMapCache.containsKey(currentUserFilesystemPair)) {
      throw new ExternalApiAuthorizationException(
          "User not logged to filesystem ["
              + filesystem.getName()
              + "]. Call '/login' endpoint first?");
    }
    return nfsAuthentication.login(
        credentialsMapCache.get(currentUserFilesystemPair).getUsername(),
        credentialsMapCache.get(currentUserFilesystemPair).getPassword(),
        filesystem,
        user);
  }

  void throwBindExceptionIfErrors(BindingResult errors) throws BindException {
    if (errors.hasErrors()) {
      throw new BindException(errors);
    }
  }

  public void removeUserCredentialsForFilesystem(User user, Long fileSystemId) {
    credentialsMapCache.remove(new NfsUserFileSystem(user, fileSystemId));
  }
}
