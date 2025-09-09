package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.model.RecordGroupSharing;
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
    value = {"id", "sharedItemId", "shareItemName", "sharedTargetType", "permission", "_links"})
public class ApiShareInfo extends LinkableApiObject implements IdentifiableObject {

  private Long id, sharedItemId;
  private String sharedTargetType, permission, shareItemName;

  public ApiShareInfo(RecordGroupSharing rgs) {
    this.id = rgs.getId();
    this.sharedItemId = rgs.getShared().getId();
    this.shareItemName = rgs.getShared().getName();
    this.permission = PermissionType.WRITE.equals(rgs.getPermType()) ? "EDIT" : "READ";
    this.sharedTargetType = rgs.getSharee().isUser() ? "USER" : "GROUP";
  }
}
