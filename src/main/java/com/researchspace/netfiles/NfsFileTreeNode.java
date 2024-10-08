package com.researchspace.netfiles;

import com.researchspace.model.netfiles.NfsFileStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang.StringUtils;

/** Model of tree node for network file store tree view. */
public class NfsFileTreeNode {

  private final List<NfsFileTreeNode> nodes = new ArrayList<>();
  private NfsFileTreeOrderType orderType;

  private boolean isFolder;
  private String name; /* a node name, displayed as a label and used in links for opening subtree */
  private String nodePath; /* a path to be saved as user folder */
  private String logicPath; /* nfsFileStoreId:relativePath */
  private String fileDate; /* date displayed to user */
  private String fileMetadata; /* metadata displayed to user if any */
  private String fileSize = "0";

  private Long nfsId;

  /** file of folder name */
  public String getFileName() {
    return name;
  }

  /** sets node name, removes slashes from beginning and end */
  public void calculateFileName(String fileName) {
    String newName = fileName;
    if (StringUtils.startsWith(newName, "/")) {
      newName = newName.replaceAll("^/+", "");
    }
    if (StringUtils.endsWith(newName, "/")) {
      newName = newName.replaceAll("/+$", "");
    }
    name = newName;
  }

  public boolean getIsFolder() {
    return isFolder;
  }

  public void setIsFolder(boolean isFolder) {
    this.isFolder = isFolder;
  }

  public String getNodePath() {
    return nodePath;
  }

  public void setNodePath(String s) {
    nodePath = s;
  }

  public List<NfsFileTreeNode> getNodes() {
    return nodes;
  }

  // add child node, put it in right place according to ordering type
  public void addNode(NfsFileTreeNode nd) {
    nodes.add(nd);
    Collections.sort(
        nodes,
        (nd1, nd2) -> {
          if (orderType == NfsFileTreeOrderType.BY_DATE) {
            return nd2.fileDate.compareTo(nd1.fileDate);
          }
          return nd1.name.compareToIgnoreCase(nd2.name);
        });
  }

  public String getFileDate() {
    return fileDate;
  }

  public void setFileDate(String fileDate) {
    this.fileDate = fileDate;
  }

  public String getFileMetadata() {
    return fileMetadata;
  }

  public void setFileMetadata(String fileMetadata) {
    this.fileMetadata = fileMetadata;
  }

  public NfsFileTreeOrderType getOrderType() {
    return orderType;
  }

  public void setOrderType(NfsFileTreeOrderType orderType) {
    this.orderType = orderType;
  }

  public String getLogicPath() {
    return logicPath;
  }

  /*
   *  for samba fullPath starts with smb://, which is probably never
   *  a proper logical path, yet it is returned in some cases
   */
  public void calculateLogicPath(String fullPath, NfsFileStore userFolder) {
    if (userFolder == null) {
      logicPath = fullPath;
      return;
    }

    String folderPath = userFolder.getPath();
    String folderId = userFolder.getId().toString();
    int pos = fullPath.indexOf(folderPath);
    if (pos >= 0) {
      pos = pos + folderPath.length();
      if (pos >= fullPath.length()) {
        logicPath = fullPath;
      } else {
        String relativePath = fullPath.substring(pos);
        logicPath = folderId + ":" + relativePath;
      }
    } else {
      logicPath = fullPath;
    }
  }

  public String getFileSize() {
    return fileSize;
  }

  public void setFileSize(String fileSize) {
    this.fileSize = fileSize;
  }

  public Long getNfsId() {
    return nfsId;
  }

  public void setNfsId(Long nfsId) {
    this.nfsId = nfsId;
  }
}
