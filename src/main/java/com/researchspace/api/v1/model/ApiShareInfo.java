package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
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
      "shareeId",
      "shareeName",
      "sharedLocation",
      "_links"
    })
public class ApiShareInfo extends LinkableApiObject implements IdentifiableObject {

  private Long id, sharedItemId;
  private String sharedTargetType, permission, shareItemName;
  private Long shareeId, sharedLocation;
  private String shareeName;

  public ApiShareInfo(RecordGroupSharing rgs) {
    this.id = rgs.getId();
    this.sharedItemId = rgs.getShared().getId();
    this.shareItemName = rgs.getShared().getName();
    this.permission = PermissionType.WRITE.equals(rgs.getPermType()) ? "EDIT" : "READ";
    this.sharedTargetType = rgs.getSharee().isUser() ? "USER" : "GROUP";
  }

  /*
  Constructor used for creating direct and indirect shares.

  RecordGroupSharing in this case can refer to either the record which was shared (for a direct share)
  or the shared notebook the document is contained within (for an indirect share), hence the notebooks checks,
  where in the case of a notebook RecordGroupSharing the original record name and id are used.
   */
  public ApiShareInfo(BaseRecord orig, RecordGroupSharing rgs, Long sharedLocation) {
    boolean isNotebook = orig.isNotebook();
    this.id = rgs.getId();
    this.sharedItemId = rgs.getShared().getId();
    this.shareItemName = isNotebook ? orig.getName() : rgs.getShared().getName();
    this.permission = PermissionType.WRITE.equals(rgs.getPermType()) ? "EDIT" : "READ";
    this.sharedTargetType = rgs.getSharee().isUser() ? "USER" : "GROUP";
    this.shareeId = rgs.getSharee().getId();
    this.shareeName = rgs.getSharee().getDisplayName();
    this.sharedLocation = sharedLocation;
  }

  /*




  */
}
