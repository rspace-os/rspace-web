package com.researchspace.netfiles.samba;

import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.netfiles.NfsAbstractClient;
import com.researchspace.netfiles.NfsAuthException;
import com.researchspace.netfiles.NfsClient;
import com.researchspace.netfiles.NfsException;
import com.researchspace.netfiles.NfsFileDetails;
import com.researchspace.netfiles.NfsFileTreeNode;
import com.researchspace.netfiles.NfsFolderDetails;
import com.researchspace.netfiles.NfsResourceDetails;
import com.researchspace.netfiles.NfsTarget;
import java.io.IOException;
import java.net.MalformedURLException;
import jcifs.smb.NtlmAuthenticator;
import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbAuthException;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

/** Jcifs Client: client side to access network drive */
@Slf4j
public class JcifsClient extends NfsAbstractClient implements NfsClient {

  private static final long serialVersionUID = -2676508538861346947L;

  private static final int SAMBA_READ_TIMEOUT_MS = 10000;
  private static final int SAMBA_RESPONSE_TIMEOUT_MS = 15000;
  private static final int SAMBA_CONNECTION_TIMEOUT_MS = 20000;

  private final transient NtlmPasswordAuthentication authenticationPrincipal;

  private final String sambaServerUrl;

  static {
    NtlmAuthenticator rsNtlmAuthenticator =
        new NtlmAuthenticator() {
          /* this method is called internally by jcifs on connection error.
           * the object returned (if not null) will be used to resubmit the request */
          @Override
          protected NtlmPasswordAuthentication getNtlmPasswordAuthentication() {
            log.warn(
                "ntlm exception: "
                    + getRequestingException().getMessage()
                    + " for "
                    + getRequestingURL());
            return null; // don't retry
          }
        };
    NtlmAuthenticator.setDefault(rsNtlmAuthenticator);

    System.setProperty("jcifs.smb.client.responseTimeout", "" + SAMBA_RESPONSE_TIMEOUT_MS);
    System.setProperty("jcifs.smb.client.soTimeout", "" + SAMBA_CONNECTION_TIMEOUT_MS);
    System.setProperty("jcifs.smb.client.connTimeout", "" + SAMBA_CONNECTION_TIMEOUT_MS);
  }

  public JcifsClient(String username, String password, String domain, String serverUrl) {
    super(username);
    authenticationPrincipal = new NtlmPasswordAuthentication(domain, username, password);
    this.sambaServerUrl = serverUrl;
  }

  @Override
  public boolean isUserLoggedIn() {
    return authenticationPrincipal != null;
  }

  /**
   * tries to read current connection target, throws exception if something wrong
   *
   * @throws MalformedURLException
   */
  @Override
  public void tryConnectAndReadTarget(String pth) throws NfsException, MalformedURLException {

    SmbFile connectionTarget = getConnectionTarget(pth);
    if (connectionTarget == null) {
      throw new IllegalStateException("connection target is null");
    }

    try {
      connectionTarget.listFiles();
      log.debug("connected to: " + connectionTarget.getPath());
    } catch (SmbAuthException auth) {
      if (getAuthenticationPrincipal() != null) {
        log.warn(
            "smb authorisation exception for user: "
                + getAuthenticationPrincipal().getUsername()
                + " and domain: "
                + getAuthenticationPrincipal().getDomain());
      }
      throw new NfsAuthException(auth.getMessage(), auth);
    } catch (SmbException e) {
      throw new NfsException(
          "reading: " + connectionTarget.getCanonicalPath() + " resulted in: " + e.getMessage(), e);
    }
  }

  @Override
  public NfsFileTreeNode createFileTree(String path, String orderType, NfsFileStore smbUserFolder)
      throws IOException {

    SmbFile connectionTarget = getConnectionTarget(path);
    JcifsFileTreeCreator treeCreator = new JcifsFileTreeCreator(orderType, smbUserFolder);
    return treeCreator.createTree(connectionTarget, getAuthenticationPrincipal());
  }

  @Override
  public NfsFileDetails queryForNfsFile(NfsTarget nfsTarget) {
    NfsFileDetails nfsFileDetails = null;
    String pathWithDomain = getPathWithDomainFromFilePath(nfsTarget.getPath());
    try {
      SmbFile smbFile = new SmbFile(pathWithDomain, getAuthenticationPrincipal());
      nfsFileDetails = new NfsFileDetails(smbFile.getName());
      nfsFileDetails.setSize((long) smbFile.getContentLength());
      nfsFileDetails.setFileSystemFullPath(nfsTarget.getPath());
      nfsFileDetails.setFileSystemParentPath(
          getParentPathFromFullPath(nfsTarget.getPath(), smbFile.getName()));
    } catch (MalformedURLException e) {
      log.warn("MalformedURLException for " + pathWithDomain);
    }
    return nfsFileDetails;
  }

  @Override
  public NfsFileDetails queryNfsFileForDownload(NfsTarget target) throws IOException {
    String pathWithDomain = getPathWithDomainFromFilePath(target.getPath());
    SmbFile smbFile = new SmbFile(pathWithDomain, getAuthenticationPrincipal());
    NfsFileDetails nfsFile = new NfsFileDetails(smbFile.getName());
    nfsFile.setFileSystemParentPath(getParentPathFromFullPath(target.getPath(), smbFile.getName()));
    nfsFile.setFileSystemFullPath(target.getPath());
    nfsFile.setRemoteInputStream(smbFile.getInputStream());
    return nfsFile;
  }

  protected String getParentPathFromFullPath(String fpath, String name) {
    if (StringUtils.isEmpty(fpath)) {
      return "";
    }
    String parentPath = fpath;
    if (fpath.endsWith(name)) {
      parentPath = fpath.substring(0, fpath.length() - name.length());
      if (parentPath.endsWith("/")) {
        parentPath = parentPath.substring(0, parentPath.length() - "/".length());
      }
    }
    return parentPath;
  }

  @Override
  public NfsFolderDetails queryForNfsFolder(NfsTarget nfsTarget) throws NfsException {

    NfsFolderDetails folderDetails = null;
    try {
      SmbFile targetFolder = getConnectionTarget(nfsTarget.getPath());
      if (targetFolder.isDirectory()) {
        folderDetails = new NfsFolderDetails(getNameOfSmbFolder(targetFolder));
        folderDetails.setFileSystemFullPath(nfsTarget.getPath());
        folderDetails.setFileSystemParentPath(
            getParentPathFromFullPath(nfsTarget.getPath(), folderDetails.getName()));
        SmbFile[] smbFileList = targetFolder.listFiles();
        for (SmbFile child : smbFileList) {

          NfsResourceDetails childResource = null;
          if (child.isDirectory()) {
            childResource = new NfsFolderDetails(getNameOfSmbFolder(child));
          } else {
            childResource = new NfsFileDetails(child.getName());
            childResource.setSize(Long.valueOf(child.getContentLength()));
          }
          childResource.setFileSystemFullPath(nfsTarget.getPath() + "/" + childResource.getName());
          childResource.setFileSystemParentPath(nfsTarget.getPath());
          folderDetails.getContent().add(childResource);
        }
      }
    } catch (SmbException | MalformedURLException e) {
      String msg = "couldn't retrieve smb folder details for: " + nfsTarget;
      log.warn(msg, e);
      throw new NfsException(msg, e);
    }

    return folderDetails;
  }

  private String getNameOfSmbFolder(SmbFile smbFolder) {
    String name = smbFolder.getName();
    if (name.endsWith("/")) {
      name = name.substring(0, name.length() - 1);
    }
    return name;
  }

  private String getPathWithDomainFromFilePath(String fpath) {
    String pathWithDomain = null;
    String fdomain = sambaServerUrl; // never endWith /
    if (fpath == null || fpath.trim().length() < 1) {
      pathWithDomain = fdomain;
    } else {
      String fpth = fpath;
      if (fpth.charAt(0) != '/') {
        fpth = "/" + fpth;
      }
      pathWithDomain = fdomain + fpth;
    }
    return pathWithDomain;
  }

  /** converts path into SmbFile pointing to the path url */
  protected SmbFile getConnectionTarget(String target) throws MalformedURLException {

    String connectionPath = SambaUtils.getSmbFilePathForTarget(sambaServerUrl, target);
    log.info("connecting to: " + connectionPath);
    SmbFile connectionTarget = new SmbFile(connectionPath, getAuthenticationPrincipal());
    connectionTarget.setReadTimeout(SAMBA_READ_TIMEOUT_MS);

    return connectionTarget;
  }

  protected NtlmPasswordAuthentication getAuthenticationPrincipal() {
    if (authenticationPrincipal == null) {
      throw new NullPointerException("Authentication Principal is null");
    }
    return authenticationPrincipal;
  }
}
