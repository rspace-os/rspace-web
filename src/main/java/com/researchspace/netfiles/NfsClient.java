package com.researchspace.netfiles;

import com.researchspace.model.netfiles.NfsFileStore;
import java.io.IOException;
import java.io.Serializable;
import java.net.MalformedURLException;

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
   * delete). Implementations that return {@code true} must also implement {@link
   * WritableNfsClient}.
   */
  default boolean supportsWrite() {
    return false;
  }
}
