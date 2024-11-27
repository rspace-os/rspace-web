package com.researchspace.service.impl;

import com.researchspace.api.v1.model.ApiExternalStorageOperationResult;
import com.researchspace.dao.NfsDao;
import com.researchspace.files.service.FileStore;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.User;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.netfiles.NfsFileStoreInfo;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.model.netfiles.NfsFileSystemInfo;
import com.researchspace.netfiles.NfsAuthException;
import com.researchspace.netfiles.NfsAuthentication;
import com.researchspace.netfiles.NfsClient;
import com.researchspace.netfiles.NfsException;
import com.researchspace.netfiles.NfsFactory;
import com.researchspace.service.NfsManager;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** Implementation of NfsManager interface. */
@Service("nfsManager")
public class NfsManagerImpl implements NfsManager {

  protected final Logger log = LoggerFactory.getLogger(NfsManagerImpl.class);

  private @Autowired NfsDao nfsDao;
  private @Autowired NfsFactory nfsFactory;

  @Autowired
  private @Qualifier("compositeFileStore") FileStore fileStore;

  @Override
  public NfsFileStore getNfsFileStore(Long id) {
    return nfsDao.getNfsFileStore(id);
  }

  @Override
  public void saveNfsFileStore(NfsFileStore fileStore) {
    if (fileStore.getFileSystem() == null) {
      throw new IllegalArgumentException("file system can't be null");
    }
    nfsDao.saveNfsFileStore(fileStore);
  }

  @Override
  public void markFileStoreAsDeleted(NfsFileStore fileStore) {
    fileStore.setDeleted(true);
    nfsDao.saveNfsFileStore(fileStore);
  }

  @Override
  public List<NfsFileStoreInfo> getFileStoreInfosForUser(User user) {
    List<NfsFileStore> userStores = nfsDao.getUserFileStores(user.getId());
    List<NfsFileStoreInfo> userStoreInfos = new ArrayList<>();
    for (NfsFileStore us : userStores) {
      userStoreInfos.add(us.toFileStoreInfo());
    }
    return userStoreInfos;
  }

  @Override
  public List<NfsFileSystemInfo> getActiveFileSystemInfos() {
    List<NfsFileSystemInfo> activeSystemInfos = new ArrayList<>();
    for (NfsFileSystem as : getActiveFileSystems()) {
      activeSystemInfos.add(as.toFileSystemInfo());
    }
    return activeSystemInfos;
  }

  /*
   * Methods for managing network file systems created by sysadmin
   */
  @Override
  public NfsFileSystem getFileSystem(Long id) {
    NfsFileSystem fileSystem = nfsDao.getNfsFileSystem(id);
    if (fileSystem == null || fileSystem.isDisabled()) {
      throw new IllegalStateException("no active filesystem for id: " + id);
    }
    return fileSystem;
  }

  @Override
  public List<NfsFileSystem> getFileSystems() {
    return nfsDao.getFileSystems();
  }

  @Override
  public List<NfsFileSystem> getActiveFileSystems() {
    return nfsDao.getActiveFileSystems();
  }

  @Override
  public void saveNfsFileSystem(NfsFileSystem fileSystem) {
    nfsDao.saveNfsFileSystem(fileSystem);
  }

  @Override
  public boolean deleteNfsFileSystem(Long id) {
    return nfsDao.deleteNfsFileSystem(id);
  }

  /*
   * Methods for logging in/out of file systems
   */

  @Override
  public String loginToNfs(
      Long fileSystemId,
      String nfsusername,
      String nfspassword,
      Map<Long, NfsClient> nfsClients,
      User user,
      String targetDir) {

    // check if required credentials provided
    NfsAuthentication nfsAuthentication =
        nfsFactory.getNfsAuthentication(getFileSystem(fileSystemId));
    String errorCode = nfsAuthentication.validateCredentials(nfsusername, nfspassword, user);
    if (errorCode != null) {
      return errorCode;
    }

    // try to log in with provided credentials
    NfsClient nfsClient =
        nfsAuthentication.login(nfsusername, nfspassword, getFileSystem(fileSystemId), user);
    nfsClients.put(fileSystemId, nfsClient);

    String connectionResult = null;
    if (checkIfUserLoggedIn(fileSystemId, nfsClients, user)) {
      connectionResult =
          testConnectionToTarget(targetDir != null ? targetDir : "", fileSystemId, nfsClient);
    }
    if (connectionResult == null || !connectionResult.startsWith(NfsManager.LOGGED_AS_MSG)) {
      logoutFromNfs(fileSystemId, nfsClients);
    }
    return connectionResult;
  }

  @Override
  public String testConnectionToTarget(String target, Long fileSystemId, NfsClient nfsClient) {

    String result;
    try {
      // Some data in NFSFileStore table was html encoded by Jquery
      // eg & encoded to &amp;
      // this will unencode that data. Data that was not html encoded
      // by JQuery will be untouched.
      nfsClient.tryConnectAndReadTarget(Jsoup.parse(target).text());
      result = NfsManager.LOGGED_AS_MSG + nfsClient.getUsername();

    } catch (MalformedURLException e) {
      result = "net.filestores.error.invalidUrl";
      log.warn(result, e);
    } catch (NfsAuthException authException) {
      NfsAuthentication nfsAuthentication =
          nfsFactory.getNfsAuthentication(getFileSystem(fileSystemId));
      String errorMsgCode = nfsAuthentication.getMessageCodeForAuthException(authException);
      result = errorMsgCode;
      log.warn(result);
    } catch (NfsException e) {
      result = "net.filestores.error.connection";
      log.warn(result, e);
    }
    return result;
  }

  @Override
  public boolean checkIfUserLoggedIn(
      Long fileSystemId, Map<Long, NfsClient> nfsClients, User user) {

    NfsClient nfsClient = getNfsClientForFileSystemAndUser(fileSystemId, user, nfsClients);
    if (nfsClient != null && nfsClient.isUserLoggedIn()) {
      /* authentication principal is marked as transient so will not be present after serialization.
       * user should be informed that their credentials expired and they need to re-log */
      return nfsClient.isUserLoggedIn();
    }
    return false;
  }

  @Override
  public String getLoggedAsUsernameIfUserLoggedIn(
      Long fileSystemId, Map<Long, NfsClient> nfsClients, User user) {
    NfsClient nfsClient = getNfsClientForFileSystemAndUser(fileSystemId, user, nfsClients);
    if (nfsClient != null && nfsClient.isUserLoggedIn()) {
      String loggedInUsername = nfsClient.getUsername();
      return loggedInUsername != null ? loggedInUsername : "(unknown)";
    }
    return null;
  }

  private NfsClient getNfsClientForFileSystemAndUser(
      Long fileSystemId, User user, Map<Long, NfsClient> nfsClients) {
    NfsClient nfsClient = nfsClients.get(fileSystemId);

    // it may be possible to auto-login, without credentials
    if (nfsClient == null) {
      log.debug(
          "user " + user.getUsername() + " doesn't have nfsClient for system: " + fileSystemId);

      try {
        String autoLoginResult = loginToNfs(fileSystemId, null, null, nfsClients, user, null);
        if (autoLoginResult.startsWith(NfsManager.LOGGED_AS_MSG)) {
          log.debug("auto login success for user: " + user.getUsername());
          nfsClient = nfsClients.get(fileSystemId);
        }
      } catch (RuntimeException e) {
        /* auto-login is a helper functionality so let's not rethrow */
        log.info("auto login failed with exception: " + e.getMessage());
      }
    }
    return nfsClient;
  }

  @Override
  public void logoutFromNfs(Long fileSystemId, Map<Long, NfsClient> nfsClients) {
    NfsClient client = nfsClients.get(fileSystemId);
    if (client != null) {
      client.closeSession();
      nfsClients.remove(fileSystemId);
    }
  }

  @Override
  public ApiExternalStorageOperationResult uploadFilesToNfs(
      Collection<EcatMediaFile> listRecordToMove, String path, NfsClient nfsClient)
      throws IOException, UnsupportedOperationException {
    // do it for all the files in the list
    Map<Long, File> mapRecordIdToFile = new LinkedHashMap<>();
    for (EcatMediaFile currentRecordToUpload : listRecordToMove) {
      mapRecordIdToFile.put(
          currentRecordToUpload.getId(),
          fileStore.findFile(currentRecordToUpload.getFileProperty()));
    }
    // once built the subset of files to transfer then upload them all
    return nfsClient.uploadFilesToNfs(path, mapRecordIdToFile);
  }

  /*
   * ==========================
   * for tests
   * ==========================
   */
  public void setNfsFactory(NfsFactory nfsFactory) {
    this.nfsFactory = nfsFactory;
  }

  public void setFileStore(FileStore fileStore) {
    this.fileStore = fileStore;
  }
}
