package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.model.Group;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.User;
import com.researchspace.model.permissions.PermissionType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Information about resource generated after sharing a single item with a single sharee (user or
 * group)
 */
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@JsonPropertyOrder(
    value = {
      "id",
      "sharedItemId",
      "shareItemName",
      "sharedTargetType",
      "permission",
      "sharedTargetId",
      "sharedTargetName",
      "sharedTargetDisplayName",
      "_links"
    })
public class ApiShareInfo extends LinkableApiObject implements IdentifiableObject {

  private Long id, sharedItemId;
  private String sharedTargetType, permission, shareItemName;
  private Long sharedTargetId;
  private String sharedTargetName, sharedTargetDisplayName;

  public ApiShareInfo(RecordGroupSharing rgs) {
    this.id = rgs.getId();
    this.sharedItemId = rgs.getShared().getId();
    this.shareItemName = rgs.getShared().getName();
    this.permission = PermissionType.WRITE.equals(rgs.getPermType()) ? "EDIT" : "READ";
    this.sharedTargetType = rgs.getSharee().isUser() ? "USER" : "GROUP";
    this.sharedTargetId = rgs.getSharee().getId();
    this.sharedTargetDisplayName = rgs.getSharee().getDisplayName();

    // Set username for users and unique name for groups
    if (rgs.getSharee().isUser()) {
      User user = (User) rgs.getSharee();
      this.sharedTargetName = user.getUsername();
    } else {
      Group group = (Group) rgs.getSharee();
      this.sharedTargetName = group.getUniqueName();
    }
  }
}
