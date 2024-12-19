package com.researchspace.netfiles;

import com.researchspace.model.netfiles.NfsFileStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang.StringUtils;

/** Model of tree node for network file store tree view. */
public class NfsFileTreeNode {

  private String name; /* a node name, displayed as a label and used in links for opening subtree */
  private boolean isFolder;
  @Getter private final List<NfsFileTreeNode> nodes = new ArrayList<>();
  @Getter @Setter private NfsFileTreeOrderType orderType;
  @Getter @Setter private String nodePath; /* a path to be saved as user folder */
  @Getter private String logicPath; /* nfsFileStoreId:relativePath */
  @Getter @Setter private String fileDate; /* modification date, to displayed in JSP tree-view UI */
  @Getter @Setter private Long modificationDateMillis; /* used by API */
  @Getter @Setter private String fileSize = "0"; /* file size as human-readable string */
  @Getter @Setter private Long fileSizeBytes = 0L;
  @Getter @Setter private Long nfsId;

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

  public boolean getIsFolder() {
    return isFolder;
  }

  public void setIsFolder(boolean isFolder) {
    this.isFolder = isFolder;
  }
}
