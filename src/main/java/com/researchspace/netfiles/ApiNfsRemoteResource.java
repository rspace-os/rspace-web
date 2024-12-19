package com.researchspace.netfiles;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeDeserialiser;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeSerialiser;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Model of network filestore resource content node for API. */
@Data
@NoArgsConstructor
@JsonPropertyOrder(
    value = {"name", "isFolder", "remotePath", "logicPath", "fileDateMillis", "fileSize", "nfsId"})
public class ApiNfsRemoteResource {

  private String name; /* a node name, displayed as a label and used in links for opening subtree */
  private boolean isFolder;
  private String logicPath; /* nfsFileStoreId:relativePath */

  @JsonProperty("modificationDate")
  @JsonSerialize(using = ISO8601DateTimeSerialiser.class)
  @JsonDeserialize(using = ISO8601DateTimeDeserialiser.class)
  private Long modificationDateMillis;

  private Long fileSize = 0L;
  private Long nfsId;

  public ApiNfsRemoteResource(NfsFileTreeNode treeNode) {
    name = treeNode.getFileName();
    isFolder = treeNode.getIsFolder();
    logicPath = treeNode.getLogicPath();
    modificationDateMillis = treeNode.getModificationDateMillis();
    fileSize = treeNode.getFileSizeBytes();
    nfsId = treeNode.getNfsId();
  }
}
