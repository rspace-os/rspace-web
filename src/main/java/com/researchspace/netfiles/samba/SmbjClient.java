package com.researchspace.netfiles.samba;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileAllInformation;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMBApiException;
import com.hierynomus.protocol.commons.EnumWithValue;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.File;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.netfiles.NfsAbstractClient;
import com.researchspace.netfiles.NfsClient;
import com.researchspace.netfiles.NfsException;
import com.researchspace.netfiles.NfsFileDetails;
import com.researchspace.netfiles.NfsFileTreeNode;
import com.researchspace.netfiles.NfsFileTreeOrderType;
import com.researchspace.netfiles.NfsFolderDetails;
import com.researchspace.netfiles.NfsResourceDetails;
import com.researchspace.netfiles.NfsTarget;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.Security;
import java.text.SimpleDateFormat;
import java.util.Date;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang.StringUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/** NfsClient fully based on smbj library, to provide smb2/3 support. */
@Slf4j
public class SmbjClient extends NfsAbstractClient implements NfsClient {

  private static final long serialVersionUID = -362934103902354196L;
  private boolean sambaNameMustMatchPath = false;

  private String sambaHost;
  private String shareName;
  private String afterShareNamePath;
  private boolean withDfsEnabled;

  // marked transient as not serializable
  private transient SMBClient client;
  private transient AuthenticationContext authContext;
  private transient Connection connection;
  private transient Session session;

  static {
    if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
      log.info("adding BouncyCastleProvider as a security provider");
      Security.addProvider(new BouncyCastleProvider());
    }
  }

  public SmbjClient(
      String username,
      String password,
      String domain,
      String serverUrl,
      String shareName,
      boolean withDfsEnabled) {
    this(username, password, domain, serverUrl, shareName, withDfsEnabled, false);
  }

  public SmbjClient(
      String username,
      String password,
      String domain,
      String serverUrl,
      String shareName,
      boolean withDfsEnabled,
      boolean sambaNameMustMatchPath) {
    super(username);

    log.info("SmbjClient initialising...");
    try {
      URL serverURL = SambaUtils.parseSambaUrl(serverUrl);
      sambaHost = serverURL.getHost();
      this.shareName = shareName == null ? "" : removeLeadingPathSeparator(shareName);
      this.withDfsEnabled = withDfsEnabled;
      afterShareNamePath = calculateAfterShareNamePath(serverURL.getPath());
      this.sambaNameMustMatchPath = sambaNameMustMatchPath;
    } catch (MalformedURLException e) {
      log.error("couldn't parse SMB serverUrl: " + serverUrl, e);
    }

    authContext =
        new AuthenticationContext(
            username, password == null ? new char[0] : password.toCharArray(), domain);
    log.info(
        "Initialised with {}, {}, {}, {}, {}, {}",
        sambaHost,
        username,
        domain,
        shareName,
        afterShareNamePath,
        withDfsEnabled);
  }

  @Override
  public boolean isUserLoggedIn() {
    return authContext != null;
  }

  @Override
  public void tryConnectAndReadTarget(String target) throws NfsException {
    if (!isUserLoggedIn()) {
      throw new IllegalStateException("user not logged into smbj system");
    }

    String path = removeLeadingPathSeparator(sanitisePath(target));
    try {
      DiskShare share = getConnectedDiskShare();
      share.folderExists(path);
    } catch (IOException | SMBApiException e) {
      log.warn("error on connecting to share and listing path: " + e.getMessage());
      throw new NfsException("couldn't retrieve path: " + path, e);
    }
  }

  @Override
  public NfsFileTreeNode createFileTree(
      String target, String nfsorder, NfsFileStore selectedUserFolder) throws IOException {

    NfsFileTreeNode rootNode = new NfsFileTreeNode();
    NfsFileTreeOrderType order = NfsFileTreeOrderType.parseOrderTypeString(nfsorder);
    rootNode.setOrderType(order);

    String path = removeLeadingPathSeparator(sanitisePath(target));
    try {
      DiskShare share = getConnectedDiskShare();
      FileAllInformation rootFileInfo = share.getFileInformation(path);
      setRootNodeProperties(rootNode, rootFileInfo, path);
      for (FileIdBothDirectoryInformation f : share.list(path)) {
        if (!StringUtils.startsWith(f.getFileName(), ".")) {
          rootNode.addNode(
              getNodeFromSmbjInfo(f, rootNode.getNodePath(), order, selectedUserFolder));
        }
      }
    } catch (SMBApiException sae) {
      throw new IOException("Problem with listing directory content for: " + path, sae);
    }

    return rootNode;
  }

  private void setRootNodeProperties(
      NfsFileTreeNode rootNode, FileAllInformation smbjFileInfo, String rootPath) {
    String name = getNameInformationFromSmbjFileInfo(smbjFileInfo, rootPath);
    boolean isDirectory = smbjFileInfo.getStandardInformation().isDirectory();
    rootNode.calculateFileName(name);
    rootNode.setNodePath(getFileTreeNodeCanonicalPathToTarget(name, "", isDirectory));
    rootNode.setIsFolder(isDirectory);
  }

  private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

  private NfsFileTreeNode getNodeFromSmbjInfo(
      FileIdBothDirectoryInformation f,
      String parentPath,
      NfsFileTreeOrderType order,
      NfsFileStore activeUserFolder) {

    System.out.println("File : " + f.getFileName());
    boolean isFolder = isFolder(f);

    NfsFileTreeNode node = new NfsFileTreeNode();
    node.setOrderType(order);

    String fullPathToTarget =
        getFileTreeNodeCanonicalPathToTarget(f.getFileName(), parentPath, isFolder);

    node.calculateFileName(f.getFileName());
    node.setNodePath(fullPathToTarget);
    node.calculateLogicPath(fullPathToTarget, activeUserFolder);

    Date dt = f.getLastWriteTime().toDate();
    node.setFileDate(sdf.format(dt));

    node.setIsFolder(isFolder);
    if (!isFolder) {
      node.setFileSize("" + f.getEndOfFile());
    }

    return node;
  }

  private boolean isFolder(FileIdBothDirectoryInformation f) {
    return EnumWithValue.EnumUtils.isSet(
        f.getFileAttributes(), FileAttributes.FILE_ATTRIBUTE_DIRECTORY);
  }

  @Override
  public NfsFileDetails queryForNfsFile(NfsTarget nfsTarget) {
    NfsFileDetails nfsFileDetails = null;
    try {
      DiskShare share = getConnectedDiskShare();
      String remotePath = getRemotePathWithoutShareName(nfsTarget.getPath());
      FileAllInformation smbjFileInfo = share.getFileInformation(remotePath);
      String fileName = getFileNameFromSmbjFileInfo(smbjFileInfo, remotePath);
      nfsFileDetails = new NfsFileDetails(fileName);
      nfsFileDetails.setSize(smbjFileInfo.getStandardInformation().getEndOfFile());
      nfsFileDetails.setFileSystemFullPath(nfsTarget.getPath());
      nfsFileDetails.setFileSystemParentPath(
          getParentPathFromFullPath(nfsTarget.getPath(), fileName));
    } catch (IOException | SMBApiException e) {
      log.warn(
          "Unexpected exception when querying share for '" + nfsTarget + "': " + e.getMessage());
    }
    return nfsFileDetails;
  }

  private String getFileNameFromSmbjFileInfo(FileAllInformation smbjFileInfo, String remotePath) {
    String fileName = null;
    if (smbjFileInfo != null) {
      String nameInfo = getNameInformationFromSmbjFileInfo(smbjFileInfo, remotePath);
      fileName = nameInfo.substring(nameInfo.lastIndexOf("\\") + 1);
    }
    return fileName;
  }

  private String getNameInformationFromSmbjFileInfo(FileAllInformation smbjFileInfo, String path) {
    return new SambaUtils()
        .getNameInformationFromSmbjFileInfo(smbjFileInfo, path, sambaNameMustMatchPath);
  }

  @Override
  public NfsFolderDetails queryForNfsFolder(NfsTarget nfsTarget) throws IOException {

    NfsFolderDetails folderDetails = null;
    try {
      DiskShare share = getConnectedDiskShare();
      String remotePath = getRemotePathWithoutShareName(nfsTarget.getPath());
      FileAllInformation smbjFileInfo = share.getFileInformation(remotePath);
      String fileName = getFileNameFromSmbjFileInfo(smbjFileInfo, remotePath);
      folderDetails = new NfsFolderDetails(fileName);
      folderDetails.setFileSystemFullPath(nfsTarget.getPath());
      folderDetails.setFileSystemParentPath(
          getParentPathFromFullPath(nfsTarget.getPath(), fileName));

      for (FileIdBothDirectoryInformation f : share.list(remotePath)) {
        if (!StringUtils.startsWith(f.getFileName(), ".")) {

          NfsResourceDetails childResource = null;
          boolean isFolder = isFolder(f);
          if (isFolder) {
            childResource = new NfsFolderDetails(f.getFileName());
          } else {
            childResource = new NfsFileDetails(f.getFileName());
            childResource.setSize(f.getEndOfFile());
          }
          childResource.setFileSystemFullPath(
              getCanonicalPathToTarget(f.getFileName(), nfsTarget.getPath()));
          childResource.setFileSystemParentPath(nfsTarget.getPath());
          folderDetails.getContent().add(childResource);
        }
      }

    } catch (IOException | SMBApiException e) {
      log.warn(
          "Unexpected exception when querying share for '" + nfsTarget + "': " + e.getMessage());
    }

    return folderDetails;
  }

  @Override
  public NfsFileDetails queryNfsFileForDownload(NfsTarget target) throws IOException {
    try {
      DiskShare share = getConnectedDiskShare();
      String remotePath = getRemotePathWithoutShareName(target.getPath());
      String fileName = remotePath.substring(remotePath.lastIndexOf("\\") + 1);
      log.debug("retrieving file info of {} from remote path {} ", fileName, remotePath);

      com.hierynomus.smbj.share.File file =
          share.openFile(
              remotePath,
              SetUtils.hashSet(AccessMask.GENERIC_READ),
              null,
              null,
              SMB2CreateDisposition.FILE_OPEN,
              null);
      NfsFileDetails fileDetails = new SmbjFileDetails(fileName, file);
      fileDetails.setFileSystemParentPath(getParentPathFromFullPath(target.getPath(), fileName));
      fileDetails.setFileSystemFullPath(target.getPath());
      return fileDetails;
    } catch (SMBApiException sae) {
      throw new IOException("Problem with downloading file for: " + target, sae);
    }
  }

  private DiskShare getConnectedDiskShare() throws IOException {
    // reuse connection if available, re-connect if not
    if (client == null || connection == null || !connection.isConnected()) {
      log.info("connecting smbj client");

      SmbConfig config = SmbConfig.builder().withDfsEnabled(withDfsEnabled).build();
      client = new SMBClient(config);
      connection = client.connect(sambaHost);
      session = null; // after resetting connection let's reset the session too
    }
    if (session == null) {
      log.info("authenticating smbj client for session");
      session = connection.authenticate(authContext);
    }
    DiskShare share = (DiskShare) session.connectShare(shareName);
    return share;
  }

  @Override
  public void releaseNfsFileAfterDownload(NfsFileDetails nfsFileDetails) {
    if (nfsFileDetails != null) {
      File smbjFile = ((SmbjFileDetails) nfsFileDetails).getSmbjFile();
      if (smbjFile != null) {
        log.info("closing remote file {} after download ", smbjFile.toString());
        smbjFile.closeSilently();
      }
    }
  }

  @Override
  public boolean supportsCurrentDir() {
    return true;
  }

  @Override
  public void closeSession() {
    if (client != null) {
      client.close();
      client = null;
      connection = null;
      session = null;
    }
  }

  /*
   * ==================================
   *  Path manipulation helper methods
   * ==================================
   */

  private static final String SMBJ_PATH_SEPARATOR = "/";

  private String calculateAfterShareNamePath(String serverPath) {
    String path = serverPath;
    if (path != null && !StringUtils.isEmpty(shareName)) {
      int shareNameIndexOf = path.indexOf(shareName);
      if (shareNameIndexOf == 0 || shareNameIndexOf == 1) {
        path = serverPath.substring(shareName.length() + shareNameIndexOf);
      }
    }
    return changeCannonicalPathToFilenamePath(path);
  }

  protected String getParentPathFromFullPath(String fpath, String name) {
    if (StringUtils.isEmpty(fpath)) {
      return "";
    }
    String parentPath = fpath;
    if (fpath.endsWith(name)) {
      parentPath = fpath.substring(0, fpath.length() - name.length());
      if (parentPath.endsWith(SMBJ_PATH_SEPARATOR)) {
        parentPath = parentPath.substring(0, parentPath.length() - SMBJ_PATH_SEPARATOR.length());
      }
    }
    return parentPath;
  }

  protected String getRemotePathWithoutShareName(String fpath) {
    /*
     * path passed from file tree navigation (SambaClient) may have different
     * format, depending on serverURL. it may be absolute path (including share
     * name) if serverURL is just a hostname, or be relative path if serverURL
     * contains path part
     */
    String path = changeCannonicalPathToFilenamePath(fpath);
    if (path.startsWith(shareName + afterShareNamePath)) {
      path = path.substring(shareName.length()); // removing share bit
    } else if (path.startsWith("\\" + shareName + afterShareNamePath)) {
      path = path.substring(("\\" + shareName).length()); // removing share bit
    } else {
      path = afterShareNamePath + path;
    }
    if (path.startsWith("\\")) {
      path = path.substring(1);
    }
    return path;
  }

  /* operates on path that is already sanitised */
  protected String getCanonicalPathToTarget(String filename, String currentSafePath) {
    String result = currentSafePath;
    result = addPathSeparatorSuffixIfNotPresent(result);
    result += changeFilenamePathToCanonicalPath(filename);
    return result;
  }

  protected String getFileTreeNodeCanonicalPathToTarget(
      String filename, String currentSafePath, boolean isDirectory) {
    String result = getCanonicalPathToTarget(filename, currentSafePath);
    if (isDirectory) {
      result = addPathSeparatorSuffixIfNotPresent(result);
    }
    return result;
  }

  private String addPathSeparatorSuffixIfNotPresent(String path) {
    if (!StringUtils.isEmpty(path) && !path.endsWith(SMBJ_PATH_SEPARATOR)) {
      return path + SMBJ_PATH_SEPARATOR;
    }
    return path;
  }

  private String changeCannonicalPathToFilenamePath(String path) {
    return path == null ? "" : path.replace(SMBJ_PATH_SEPARATOR, "\\");
  }

  private String changeFilenamePathToCanonicalPath(String filename) {
    return filename == null ? "" : filename.replaceAll("\\\\", SMBJ_PATH_SEPARATOR);
  }

  private String removeLeadingPathSeparator(String path) {
    if (path != null && path.startsWith(SMBJ_PATH_SEPARATOR)) {
      return path.substring(SMBJ_PATH_SEPARATOR.length());
    }
    return path;
  }

  private String sanitisePath(String target) {
    return StringUtils.isEmpty(target) ? "" : target;
  }

  /*
   * ==================== for tests ====================
   */
  public String getSambaHost() {
    return sambaHost;
  }

  public String getShareName() {
    return shareName;
  }

  public String getAfterShareNamePath() {
    return afterShareNamePath;
  }

  public AuthenticationContext getAuthContext() {
    return authContext;
  }
}
