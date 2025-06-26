package com.researchspace.netfiles;

import com.researchspace.api.v1.model.ApiExternalStorageOperationResult;
import com.researchspace.model.netfiles.NfsFileStore;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.util.Map;
import java.util.Set;

/** Interface for clients providing connection to Network File Store */
public interface NfsClient extends Serializable {

  /**
   * Checks whether authentication details were provided by the user
   *
   * @return
   */
  boolean isUserLoggedIn();

  /**
   * @return the username that nfs client is using for authentication
   */
  String getUsername();

  /**
   * Test if client can read target path.
   *
   * @param target path to connect, relative to host
   * @throws MalformedURLException if url is wrong
   * @throws NfsAuthException on authentication problem
   * @throws NfsException on connection problem
   */
  void tryConnectAndReadTarget(String target) throws NfsException, MalformedURLException;

  /**
   * Creates file tree node starting on connection target, with nodes listed in given order, and
   * links relative to selected user folder.
   *
   * @param target path to connect, relative to host
   * @param nfsorder "byname" or "bydate" string
   * @param selectedUserFolder current user folder (or null), used for calculating relative paths
   *     for links
   * @return file tree node starting at connection target with child nodes
   * @throws IOException
   */
  NfsFileTreeNode createFileTree(String target, String nfsorder, NfsFileStore selectedUserFolder)
      throws IOException;

  /**
   * Retrieve file details from the net file store
   *
   * @param nfsTarget the path to the file on the net file store
   * @return NfsFileDetails or null if file cannot be retrieved
   */
  NfsFileDetails queryForNfsFile(NfsTarget nfsTarget);

  /**
   * retrieve folder details
   *
   * @throws NfsException if folder can't be retrieved or doesn't exist
   */
  NfsFolderDetails queryForNfsFolder(NfsTarget nfsTarget) throws IOException;

  /*** DEFAULT methods ***/

  /**
   * called after a file is downloaded, in case there is some cleanup to be done. default
   * implementation does nothing.
   */
  default void releaseNfsFileAfterDownload(NfsFileDetails nfsFileDetails) {
    // do nothing
  }

  /**
   * retrieve folder details
   *
   * @throws NfsException if folder can't be retrieved or doesn't exist
   */
  default NfsFileDetails queryNfsFileForDownload(NfsTarget target) throws IOException {
    return null;
  }

  /** called after user is logged out to close nfs session. default implementation does nothing. */
  default void closeSession() {
    // do nothing
  }

  /**
   * flag stating if the client supports non-standard dir paths, like home folder & parent folders
   */
  default boolean supportsExtraDirs() {
    return false;
  }

  /**
   * flag stating if the client supports empty filestore path (pointing to the current/default
   * folder)
   */
  default boolean supportsCurrentDir() {
    return false;
  }

  /**
   * flag stating if the client supports write operations on the filestore (mainly file creation and
   * delete)
   */
  default boolean supportWritePermission() {
    return false;
  }

  /***
   * Stores a list of files into a specific path of an external storage file system.
   *
   * @param destinationPath the path where you want to save the files
   * @param mapRecordIdToFile the Map[recordId, File] of the files you want to upload
   * @return the map of the file descriptors of the files saved
   * @throws UnsupportedOperationException if any of the file cannot be deleted
   */
  default ApiExternalStorageOperationResult uploadFilesToNfs(
      String destinationPath, Map<Long, File> mapRecordIdToFile)
      throws UnsupportedOperationException {
    if (!this.supportWritePermission()) {
      throw new UnsupportedOperationException(
          "The Operation is not supported by the NfsClient in use");
    }
    return null;
  }

  /***
   * Delete a list of files from an external file system storage
   *
   * @param absolutePathFilenames the absolute path and filename to the external storage files that need to be deleted
   * @return true when all the files are deleted
   * @throws UnsupportedOperationException if one of the files cannot be deleted
   */
  default boolean deleteFilesFromNfs(Set<String> absolutePathFilenames)
      throws UnsupportedOperationException {
    if (!this.supportWritePermission()) {
      throw new UnsupportedOperationException(
          "The Operation is not supported by the NfsClient in use");
    }
    return false;
  }
}
