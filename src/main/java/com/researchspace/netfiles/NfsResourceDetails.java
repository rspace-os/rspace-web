package com.researchspace.netfiles;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import java.io.File;
import lombok.Data;

@JsonTypeInfo(use = Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
  @Type(value = NfsFileDetails.class, name = "file"),
  @Type(value = NfsFolderDetails.class, name = "folder"),
})
@Data
public abstract class NfsResourceDetails {

  public static final String TYPE_FILE = "file";
  public static final String TYPE_FOLDER = "folder";

  private String name;

  /** location on the filesystem */
  private Long fileSystemId;

  private String fileSystemFullPath;
  private String fileSystemParentPath;
  private Long nfsId; // Identifier on the net file store e.g. DATA_ID in iRODS

  /** path to the local copy, if the file exists on RSpace server */
  private File localFile;

  /** resource size, if available */
  private Long size;

  /* whether file or folder, seems required for jackson polymorphic serialization */
  private String type;

  @JsonIgnore
  public boolean isFile() {
    return TYPE_FILE.equals(type);
  }
  ;

  @JsonIgnore
  public boolean isFolder() {
    return TYPE_FOLDER.equals(type);
  }
  ;
}
