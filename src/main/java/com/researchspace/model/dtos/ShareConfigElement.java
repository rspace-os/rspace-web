package com.researchspace.model.dtos;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.researchspace.model.RecordGroupSharing;
import java.util.Arrays;
import java.util.List;

/**
 * Command object to handle record sharing with groups or users. One of group or user Id is set, not
 * both.
 */
public class ShareConfigElement {

  private Long userId;
  private Long groupid;
  private Long groupFolderId;

  private String email;
  private Long externalGroupId;

  private String operation;
  private List<String> allowedOps = Arrays.asList(new String[] {"read", "write"});

  private boolean isAutoshare = false;
  private String publicationSummary;
  private boolean displayContactDetails;
  private boolean publishOnInternet;

  /**
   * Assumes 'id' is a group Id. Set UserId explicitly if the object identified by id is a user, not
   * a group.
   *
   * @param id
   * @param operation
   */
  public ShareConfigElement(Long id, String operation) {
    this.groupid = id;
    this.operation = operation;
  }

  /**
   * ALternate constructor that will set user or group id properly depending on the
   *
   * @param id
   * @param operation
   */
  public ShareConfigElement(RecordGroupSharing rgs, String operation) {
    if (rgs.getSharee().isGroup()) {
      setGroupid(rgs.getSharee().getId());
    } else {
      setUserId(rgs.getSharee().getId());
    }
    this.operation = operation;
  }

  /** Noargs constructor for Spring, don't use in application code. */
  public ShareConfigElement() {}

  /**
   * The folder to be shared into, this is optional, can be <code>null</code>. Should only be
   * non-null if we're sharing into a Group, not with a user. <be
   *
   * @return
   */
  public Long getGroupFolderId() {
    return groupFolderId;
  }

  public void setGroupFolderId(Long groupFolderId) {
    this.groupFolderId = groupFolderId;
  }

  public List<String> getAllowedOps() {
    return allowedOps;
  }

  public void setAllowedOps(List<String> allowedOps) {
    this.allowedOps = allowedOps;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
    this.groupid = null;
    this.email = null;
    this.externalGroupId = null;
  }

  /**
   * Gets whichever id has been set.
   *
   * @return
   */
  @JsonIgnore
  public Long getId() {
    if (groupid != null) {
      return groupid;
    }
    if (userId != null) {
      return userId;
    }
    return null;
  }

  public Long getGroupid() {
    return groupid;
  }

  public void setGroupid(Long groupid) {
    this.groupid = groupid;
    this.userId = null;
    this.email = null;
    this.externalGroupId = null;
  }

  public String getOperation() {
    return operation;
  }

  /**
   * @param operation : one of 'read' or 'write'
   */
  public void setOperation(String operation) {
    if (!allowedOps.contains(operation)) {
      throw new IllegalArgumentException("Argument must be an allowed operation");
    }
    this.operation = operation;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
    this.userId = null;
    this.groupid = null;
    this.externalGroupId = null;
  }

  public Long getExternalGroupId() {
    return externalGroupId;
  }

  public void setExternalGroupId(Long externalGroupId) {
    this.externalGroupId = externalGroupId;
    this.email = null;
    this.userId = null;
    this.groupid = null;
  }

  /**
   * Flag indicating whether or not this share configuration is an autoshare (true) or a regular,
   * invoked share (false, default)
   *
   * @return
   */
  public boolean isAutoshare() {
    return isAutoshare;
  }

  public void setAutoshare(boolean isAutoshare) {
    this.isAutoshare = isAutoshare;
  }

  @Override
  public String toString() {
    return "ShareConfigElement [userId="
        + userId
        + ", groupid="
        + groupid
        + ", groupFolderId="
        + groupFolderId
        + ", email="
        + email
        + ", externalGroupId="
        + externalGroupId
        + ", operation="
        + operation
        + ", allowedOps="
        + allowedOps
        + "]";
  }

  public String getPublicationSummary() {
    return publicationSummary;
  }

  public void setPublicationSummary(String publicationSummary) {
    this.publicationSummary = publicationSummary;
  }

  public boolean isDisplayContactDetails() {
    return displayContactDetails;
  }

  public void setDisplayContactDetails(boolean displayContactDetails) {
    this.displayContactDetails = displayContactDetails;
  }

  public boolean isPublishOnInternet() {
    return publishOnInternet;
  }

  public void setPublishOnInternet(boolean publishOnInternet) {
    this.publishOnInternet = publishOnInternet;
  }
}
