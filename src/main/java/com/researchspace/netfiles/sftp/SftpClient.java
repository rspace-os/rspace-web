package com.researchspace.netfiles.sftp;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.ChannelSftp.LsEntry;
import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import com.researchspace.model.UserKeyPair;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.netfiles.NfsAbstractClient;
import com.researchspace.netfiles.NfsAuthException;
import com.researchspace.netfiles.NfsClient;
import com.researchspace.netfiles.NfsException;
import com.researchspace.netfiles.NfsFileDetails;
import com.researchspace.netfiles.NfsFileTreeNode;
import com.researchspace.netfiles.NfsFileTreeOrderType;
import com.researchspace.netfiles.NfsFolderDetails;
import com.researchspace.netfiles.NfsResourceDetails;
import com.researchspace.netfiles.NfsTarget;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Network File Store client connecting through sftp. */
public class SftpClient extends NfsAbstractClient implements NfsClient {

  protected static final int DEFAULT_SERVER_PORT = 22;

  private static final String SFTP_PATH_SEPARATOR = "/";
  private static final String CURRENT_DIR_PATH = ".";

  private static final long serialVersionUID = -4932548273017942508L;
  private static final Logger log = LoggerFactory.getLogger(SftpClient.class);
  private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

  private transient JSch jschClient;
  private transient Session session;
  private transient ChannelSftp channel;

  private String host;
  private int port;

  private String password; /* will be null for public key authentication */

  public SftpClient(String username, String password, String serverUrl, String serverPublicKey) {
    super(username);
    this.password = password;

    createJSchClient(serverUrl, serverPublicKey);
  }

  public SftpClient(
      UserKeyPair userKeyPair, String passphrase, String serverUrl, String serverPublicKey) {
    super(userKeyPair.getUser().getUsername());

    createJSchClient(serverUrl, serverPublicKey);
    addPublicKeyIdentity(userKeyPair, passphrase);
  }

  private void createJSchClient(String serverUrl, String serverPublicKey) {

    setHostAndPortFromServerUrl(serverUrl);
    jschClient = new JSch();

    if (!StringUtils.isEmpty(serverPublicKey)) {
      try {
        HostKey hostkey = new HostKey(host, Base64.decodeBase64(serverPublicKey));
        jschClient.getHostKeyRepository().add(hostkey, null);

      } catch (JSchException e) {
        throw new IllegalStateException("couldn't set HostKey " + serverPublicKey, e);
      }
    }
  }

  private void addPublicKeyIdentity(UserKeyPair userKeyPair, String passphrase) {
    String privateKey = userKeyPair.getPrivateKey();
    String publicKey = userKeyPair.getPublicKey();
    if (passphrase == null) {
      passphrase = "";
    }
    try {
      jschClient.addIdentity(
          username,
          privateKey.getBytes(StandardCharsets.UTF_8),
          publicKey.getBytes(StandardCharsets.UTF_8),
          passphrase.getBytes(StandardCharsets.UTF_8));
    } catch (JSchException e) {
      log.warn("problem when adding identity for " + username, e);
      throw new IllegalArgumentException("wrong arguments for setting identity", e);
    }
  }

  protected void setHostAndPortFromServerUrl(String serverUrl) {
    String newHost = serverUrl;
    int newPort = DEFAULT_SERVER_PORT;

    if (newHost.startsWith("sftp://")) {
      newHost = newHost.substring("sftp://".length());
    }

    if (newHost.contains(":")) {
      String[] split = serverUrl.split(":");
      newHost = split[0];
      newPort = Integer.parseInt(split[1]);
    }

    this.host = newHost;
    this.port = newPort;
  }

  @Override
  public boolean isUserLoggedIn() {
    return jschClient != null;
  }

  @Override
  public void tryConnectAndReadTarget(String target) throws NfsException {
    if (!isUserLoggedIn()) {
      throw new IllegalStateException("user not logged into sftp system");
    }

    String safePath = sanitiseLsPath(target);
    getLsResult(safePath);
  }

  @Override
  public NfsFileTreeNode createFileTree(
      String target, String nfsOrder, NfsFileStore activeUserFolder) throws NfsException {

    NfsFileTreeNode rootNode = new NfsFileTreeNode();
    NfsFileTreeOrderType order = NfsFileTreeOrderType.parseOrderTypeString(nfsOrder);
    rootNode.setOrderType(order);

    String lsPath = sanitiseLsPath(target);
    List<LsEntry> lsResult = getLsResult(lsPath);
    if (lsResult != null) {
      for (LsEntry lsEntry : lsResult) {
        rootNode.addNode(getNodeFromLsEntry(lsEntry, lsPath, order, activeUserFolder));
      }
    }

    String rootNodePath = CURRENT_DIR_PATH.equals(lsPath) ? getCurrentDirectory() : lsPath;
    rootNode.setNodePath(rootNodePath);

    return rootNode;
  }

  private NfsFileTreeNode getNodeFromLsEntry(
      LsEntry lsEntry, String lsPath, NfsFileTreeOrderType order, NfsFileStore activeUserFolder) {

    NfsFileTreeNode node = new NfsFileTreeNode();
    node.setOrderType(order);

    String fullPathToTarget = getCanonicalPathToTarget(lsEntry.getFilename(), lsPath);
    SftpATTRS attrs = lsEntry.getAttrs();

    node.calculateFileName(lsEntry.getFilename());
    node.setNodePath(fullPathToTarget);
    node.setIsFolder(attrs.isDir() || attrs.isLink());
    Date mTimeDate = getDateFromMTime(attrs.getMTime());
    node.setFileDate(sdf.format(mTimeDate));
    node.setFileDateMillis(mTimeDate.getTime());
    node.calculateLogicPath(fullPathToTarget, activeUserFolder);

    if (!attrs.isDir()) {
      node.setFileSize("" + attrs.getSize());
      node.setFileSizeBytes(attrs.getSize());
    }

    return node;
  }

  private Date getDateFromMTime(int mtime) {
    return new Date((long) mtime * 1000);
  }

  /* operates on path that is already sanitised */
  protected String getCanonicalPathToTarget(String filename, String currentSafePath) {
    String result = "";
    if (!CURRENT_DIR_PATH.equals(currentSafePath)) {
      result += currentSafePath;
      if (!currentSafePath.endsWith(SFTP_PATH_SEPARATOR)) {
        result += SFTP_PATH_SEPARATOR;
      }
    }
    result += filename;
    return result;
  }

  @Override
  public NfsFileDetails queryForNfsFile(NfsTarget nfsTarget) {

    NfsFileDetails nfsFileDetails = null;
    String lsPath = sanitiseLsPath(nfsTarget.getPath());
    try {
      List<LsEntry> lsResult = getLsResult(lsPath);
      if (lsResult.size() == 1) {
        LsEntry lsEntry = lsResult.get(0);
        SftpATTRS attrs = lsEntry.getAttrs();

        nfsFileDetails = new NfsFileDetails(lsEntry.getFilename());
        if (!attrs.isDir()) {
          nfsFileDetails.setSize(attrs.getSize());
        }
        nfsFileDetails.setFileSystemFullPath(lsPath);
        nfsFileDetails.setFileSystemParentPath(getParentPathFromFullPath(lsPath));

      } else {
        log.warn(
            "unexpected number of ls results: "
                + lsResult.size()
                + " when querying path: "
                + lsPath);
      }

    } catch (NfsException e) {
      log.warn("NfsException when querying '" + nfsTarget + "': " + e.getMessage());
    }
    return nfsFileDetails;
  }

  @Override
  public NfsFileDetails queryNfsFileForDownload(NfsTarget target) throws NfsException {
    log.debug("file download request for: " + target.getPath());

    InputStream inputStream = null;
    try {
      synchronized (this) {
        inputStream = getSftpChannel().get(target.getPath());
      }
    } catch (SftpException e) {
      throw new NfsException("couldn't download file: " + target, e);
    }

    NfsFileDetails nfsDetails = new NfsFileDetails(getFileNameFromFullPath(target.getPath()));
    nfsDetails.setFileSystemParentPath(getParentPathFromFullPath(target.getPath()));
    nfsDetails.setFileSystemFullPath(target.getPath());
    nfsDetails.setRemoteInputStream(inputStream);
    return nfsDetails;
  }

  protected String getFileNameFromFullPath(String resolvedPath) {
    if (StringUtils.isEmpty(resolvedPath)) {
      return "";
    }
    if (resolvedPath.contains(SFTP_PATH_SEPARATOR)) {
      return resolvedPath.substring(resolvedPath.lastIndexOf(SFTP_PATH_SEPARATOR) + 1);
    }
    return resolvedPath;
  }

  private String getParentPathFromFullPath(String resolvedPath) {
    String filename = getFileNameFromFullPath(resolvedPath);
    String parentPath = resolvedPath;
    if (resolvedPath.endsWith(filename)) {
      parentPath = resolvedPath.substring(0, resolvedPath.length() - filename.length());
      if (parentPath.endsWith(SFTP_PATH_SEPARATOR)) {
        parentPath = parentPath.substring(0, parentPath.length() - SFTP_PATH_SEPARATOR.length());
      }
    }
    return parentPath;
  }

  @Override
  public NfsFolderDetails queryForNfsFolder(NfsTarget nfsTarget) throws NfsException {

    NfsFolderDetails folderDetails = null;
    String lsPath = sanitiseLsPath(nfsTarget.getPath());
    List<LsEntry> lsResult = getLsResult(lsPath);

    folderDetails = new NfsFolderDetails(getResourceNameFromLsPath(lsPath));
    folderDetails.setFileSystemFullPath(nfsTarget.getPath());
    folderDetails.setFileSystemParentPath(getParentPathFromFullPath(nfsTarget.getPath()));
    if (lsResult != null) {
      for (LsEntry lsEntry : lsResult) {
        SftpATTRS attrs = lsEntry.getAttrs();
        NfsResourceDetails resource = null;
        if (attrs.isDir()) {
          resource = new NfsFolderDetails(lsEntry.getFilename());
        } else {
          resource = new NfsFileDetails(lsEntry.getFilename());
          resource.setSize(attrs.getSize());
        }
        resource.setFileSystemFullPath(nfsTarget.getPath() + "/" + lsEntry.getFilename());
        resource.setFileSystemParentPath(nfsTarget.getPath());
        folderDetails.getContent().add(resource);
      }
    }
    return folderDetails;
  }

  @Override
  public void closeSession() {
    if (session != null) {
      if (channel != null) {
        channel.exit();
      }
      session.disconnect();
    }
  }

  @Override
  public boolean supportsCurrentDir() {
    return true;
  }

  @Override
  public boolean supportsExtraDirs() {
    return true;
  }

  /* Note: ChannelSftp is not thread-safe, so any class using it should be synchronized */
  protected ChannelSftp getSftpChannel() throws NfsException {

    if (session == null || !session.isConnected() || channel == null || !channel.isConnected()) {
      try {
        if (session == null || !session.isConnected()) {
          session = jschClient.getSession(username, host, port);
          session.setPassword(password);
          session.connect();
        }
        channel = (ChannelSftp) session.openChannel("sftp");
        channel.connect();
      } catch (JSchException e) {
        log.info("failed to open channel with host: " + this.host + " and port: " + this.port);
        if (StringUtils.contains(e.getMessage(), "Auth fail")) {
          throw new NfsAuthException(e.getMessage(), e);
        }
        throw new NfsException("couldn't open channel", e);
      }
    }
    return channel;
  }

  @SuppressWarnings("rawtypes")
  private List<LsEntry> getLsResult(String safePath) throws NfsException {
    log.debug("sftp ls on: " + safePath);

    Vector lsVector = null;
    try {
      synchronized (this) {
        lsVector = getSftpChannel().ls(safePath);
      }
    } catch (SftpException e) {
      log.warn("error on sftp channel ls: " + e.getMessage());
      throw new NfsException("couldn't retrieve path: " + safePath, e);
    }

    List<LsEntry> lsEntryList = new ArrayList<>();
    for (int i = 0; i < lsVector.size(); i++) {
      Object lsElement = lsVector.elementAt(i);
      if (lsElement instanceof LsEntry) {
        lsEntryList.add((LsEntry) lsElement);
      } else {
        log.warn("got ls result of unknown type: " + lsElement);
      }
    }

    filterLsEntries(lsEntryList);
    return lsEntryList;
  }

  private List<LsEntry> filterLsEntries(List<LsEntry> lsEntryList) {
    for (Iterator<LsEntry> iterator = lsEntryList.iterator(); iterator.hasNext(); ) {
      LsEntry entry = iterator.next();
      if (StringUtils.startsWith(entry.getFilename(), CURRENT_DIR_PATH)) {
        // removes '.', '..' and hidden files
        iterator.remove();
      }
    }
    return lsEntryList;
  }

  protected String sanitiseLsPath(String target) {
    if (StringUtils.isEmpty(target)) {
      // if empty path get user's current directory
      return CURRENT_DIR_PATH;
    }
    return target.replaceAll("//", "/");
  }

  private String getResourceNameFromLsPath(String lsPath) {
    if (lsPath.contains("/")) {
      return lsPath.substring(lsPath.lastIndexOf("/"));
    }
    return lsPath;
  }

  private synchronized String getCurrentDirectory() throws NfsException {
    try {
      return getSftpChannel().pwd();
    } catch (SftpException e) {
      throw new NfsException("couldn't retrieve current directory", e);
    }
  }
}
