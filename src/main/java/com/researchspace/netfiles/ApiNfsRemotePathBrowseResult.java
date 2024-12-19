package com.researchspace.netfiles;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.model.netfiles.NfsFileSystemInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;

/** Model of network filestore resource content response for API. */
@Data
@JsonPropertyOrder(
    value = {
      "remotePath",
      "filesystemInfo",
      "loggedUser",
      "showExtraDirs",
      "showCurrentDir",
      "content"
    })
public class ApiNfsRemotePathBrowseResult {

  private String remotePath;
  private String loggedUser;
  private boolean showExtraDirs;
  private boolean showCurrentDir;
  private NfsFileSystemInfo filesystemInfo;

  private List<ApiNfsRemoteResource> content = new ArrayList<>();

  public ApiNfsRemotePathBrowseResult(
      NfsFileTreeNode treeNode,
      NfsFileSystemInfo filesystemInfo,
      boolean showExtraDirs,
      boolean showCurrentDir,
      String loggedUser) {

    remotePath = treeNode.getNodePath();
    this.loggedUser = loggedUser;
    this.showExtraDirs = showExtraDirs;
    this.showCurrentDir = showCurrentDir;
    this.filesystemInfo = filesystemInfo;

    if (treeNode.getNodes() != null) {
      content =
          treeNode.getNodes().stream().map(ApiNfsRemoteResource::new).collect(Collectors.toList());
    }
  }
}
