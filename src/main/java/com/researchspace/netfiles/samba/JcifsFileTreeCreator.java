package com.researchspace.netfiles.samba;

import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.netfiles.NfsFileTreeNode;
import com.researchspace.netfiles.NfsFileTreeOrderType;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Help class to retrieve files and generate a tree for sambaClient */
public class JcifsFileTreeCreator {

  private static final Logger log = LoggerFactory.getLogger(JcifsFileTreeCreator.class);

  private final NfsFileTreeNode rootNode = new NfsFileTreeNode();

  private final NfsFileTreeOrderType orderType;
  private final NfsFileStore userFolder;

  private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

  public JcifsFileTreeCreator(String orderType, NfsFileStore userFolder) {
    this.orderType = NfsFileTreeOrderType.parseOrderTypeString(orderType);
    this.userFolder = userFolder;
  }

  public NfsFileTreeNode createTree(SmbFile target, NtlmPasswordAuthentication auth)
      throws IOException {
    convert(target, auth);
    return rootNode;
  }

  // ------------ support methods ----------------------------

  private void convert(SmbFile rootSmbFile, NtlmPasswordAuthentication authenticator)
      throws IOException {

    if (rootSmbFile == null) {
      return;
    }
    setNodeProperties(rootNode, rootSmbFile);

    /* find children */
    SmbFile[] smbFileList = listContentOfSambaDirectory(rootSmbFile);

    /* add children to root node */
    if (ArrayUtils.isNotEmpty(smbFileList)) {
      for (SmbFile smbFile : smbFileList) {
        addSmbFileToRootNode(rootSmbFile, smbFile);
      }
    }
  }

  private SmbFile[] listContentOfSambaDirectory(SmbFile smbDir) throws IOException {
    SmbFile[] smbFileList = null;
    try {
      smbFileList = smbDir.listFiles();
    } catch (SmbException e) {
      throw new IOException(
          "Problem with listing directory content for: " + smbDir.getCanonicalPath(), e);
    }
    return smbFileList;
  }

  private void addSmbFileToRootNode(SmbFile rootSmbFile, SmbFile smbFile) {
    try {
      if (smbFile.isFile() && isHiddenFile(smbFile)) {
        return;
      }
      NfsFileTreeNode newNode = new NfsFileTreeNode();
      setNodeProperties(newNode, smbFile);
      rootNode.addNode(newNode);
    } catch (SmbException e) {
      log.warn(
          "Problem with retrieving details of element: "
              + smbFile.getName()
              + " for node: "
              + rootSmbFile.getCanonicalPath()
              + ". Error message: "
              + e.getMessage());
      log.debug("SmbException details", e);
    }
  }

  private boolean isHiddenFile(SmbFile smbFile) {
    return smbFile.getName().trim().charAt(0) == '.';
  }

  private void setNodeProperties(NfsFileTreeNode node, SmbFile smbFile) throws SmbException {

    node.calculateFileName(smbFile.getName());
    node.setNodePath(smbFile.getCanonicalPath());
    node.setOrderType(orderType);

    Date dt = new Date(smbFile.getDate());
    node.setFileDate(sdf.format(dt));

    try {
      boolean isFolder = smbFile.isDirectory();
      node.calculateLogicPath(smbFile.getCanonicalPath(), userFolder);
      if (!isFolder) {
        int fileLength = smbFile.getContentLength();
        node.setFileSize(Integer.toString(fileLength));
      }
      node.setIsFolder(isFolder);
    } catch (SmbException smbe) {
      log.warn(
          "exception when checking if node " + smbFile.getCanonicalPath() + " is a folder", smbe);
    }
  }
}
