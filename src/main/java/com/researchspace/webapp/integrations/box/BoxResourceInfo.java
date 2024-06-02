package com.researchspace.webapp.integrations.box;

import java.util.Date;

/** DTO class for returning details of Box Resource to client */
public class BoxResourceInfo {

  // common properties
  private String id;
  private String name;
  private String description;
  private String sharedLinkUrl;
  private String owner;

  // file specific properties
  private String sha1;
  private long size;
  private Date createdAt;
  private String versionNumber;
  private String versionID;

  // custom
  private boolean folder;

  // constructor for Box file
  public BoxResourceInfo(com.box.sdk.BoxFile.Info info) {

    id = info.getID();
    name = info.getName();
    description = info.getDescription();
    sharedLinkUrl = info.getSharedLink().getURL();
    owner = info.getOwnedBy().getName();

    sha1 = info.getSha1();
    size = info.getSize();
    createdAt = info.getCreatedAt();
    versionNumber = info.getVersionNumber(); // is always null
    versionID = info.getVersion().getID();

    folder = false;
  }

  // constructor for Box folder
  public BoxResourceInfo(com.box.sdk.BoxFolder.Info info) {

    id = info.getID();
    name = info.getName();
    description = info.getDescription();
    sharedLinkUrl = info.getSharedLink().getURL();
    owner = info.getOwnedBy().getName();

    folder = true;
  }

  // default constructor (for JSON serializer)
  public BoxResourceInfo() {}

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public String getSharedLinkUrl() {
    return sharedLinkUrl;
  }

  public String getOwner() {
    return owner;
  }

  public String getSha1() {
    return sha1;
  }

  public long getSize() {
    return size;
  }

  public Date getCreatedAt() {
    return createdAt;
  }

  public String getVersionNumber() {
    return versionNumber;
  }

  public String getVersionID() {
    return versionID;
  }

  public boolean isFolder() {
    return folder;
  }
}
