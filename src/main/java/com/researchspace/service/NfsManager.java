package com.researchspace.service;

import com.researchspace.api.v1.model.ApiExternalStorageOperationResult;
import com.researchspace.files.service.FileStore;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.User;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.netfiles.NfsFileStoreInfo;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.model.netfiles.NfsFileSystemInfo;
import com.researchspace.netfiles.NfsClient;
import com.researchspace.webapp.controller.IgnoreInServiceLoggerAspct;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Manager for handling net filestores functionality */
public interface NfsManager {

  String LOGGED_AS_MSG = "logged.as.";

  /*
   * Methods for managing network file stores saved by the user
   */

  NfsFileStore getNfsFileStore(Long id);

  void saveNfsFileStore(NfsFileStore fileStore);

  void markFileStoreAsDeleted(NfsFileStore fileStore);

  List<NfsFileStoreInfo> getFileStoreInfosForUser(User user);

  /*
   * Methods for managing network file systems created by sysadmin
   */

  NfsFileSystem getFileSystem(Long id);

  List<NfsFileSystem> getFileSystems();

  List<NfsFileSystem> getActiveFileSystems();

  List<NfsFileSystemInfo> getActiveFileSystemInfos();

  void saveNfsFileSystem(NfsFileSystem fileSystem);

  boolean deleteNfsFileSystem(Long id);

  /*
   * Methods for logging user in/out of file systems
   */

  /**
   * @return "logged.as.-username" if logged in, null if not, or error code if error
   */
  @IgnoreInServiceLoggerAspct(ignoreAllRequestParams = true)
  String loginToNfs(
      Long fileSystemId,
      String nfsusername,
      String nfspassword,
      Map<Long, NfsClient> nfsClients,
      User user,
      String targetDir);

  /**
   * Checks if user is authenticated to a particular file system.
   *
   * <p>Or maybe user can access file system without authentication through auto-login, in that case
   * auto-logged nfsClient is added to nfsClients map.
   *
   * @return true if user is logged in (or was as auto-logged-in just now)
   */
  boolean checkIfUserLoggedIn(Long fileSystemId, Map<Long, NfsClient> nfsClients, User user);

  /**
   * @return file system username used for authenticating into particular file system, or null, if
   *     user not logged into that file system
   */
  String getLoggedAsUsernameIfUserLoggedIn(
      Long fileSystemId, Map<Long, NfsClient> nfsClients, User user);

  /**
   * @return "logged.as.-username-" string or error message code
   */
  String testConnectionToTarget(String target, Long fileSystemId, NfsClient nfsClient);

  void logoutFromNfs(Long fileSystemId, Map<Long, NfsClient> nfsClients);

  ApiExternalStorageOperationResult uploadFilesToNfs(
      Collection<EcatMediaFile> fileDatabaseId, String path, NfsClient nfsClient)
      throws IOException, UnsupportedOperationException;

  /* for tests purposes */
  void setFileStore(FileStore fileStore);
}
