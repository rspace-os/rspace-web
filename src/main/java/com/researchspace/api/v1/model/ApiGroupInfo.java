package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.model.Group;
import com.researchspace.model.UserGroup;
import com.researchspace.webapp.integrations.raid.RaIDReferenceDTO;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@JsonPropertyOrder(
    value = {
      "id",
      "globalId",
      "name",
      "type",
      "sharedFolderId",
      "sharedSnippetFolderId",
      "raid",
      "members"
    })
public class ApiGroupInfo extends ApiGroupBasicInfo {

  private String type;
  private Long sharedFolderId;
  private Long sharedSnippetFolderId;
  private RaIDReferenceDTO raid;
  private List<ApiUserGroupInfo> members = new ArrayList<>();

  public ApiGroupInfo(Group group) {
    super(group);
    this.type = group.getGroupType().name();

    this.sharedFolderId = group.getCommunalGroupFolderId();
    this.sharedSnippetFolderId = group.getSharedSnippetGroupFolderId();
    if (group.getRaid() != null) {
      this.raid = new RaIDReferenceDTO(
          group.getRaid().getId(), group.getRaid().getRaidServerAlias(),
          group.getRaid().getRaidTitle(), group.getRaid().getRaidIdentifier());
    }
    for (UserGroup ug : group.getUserGroups()) {
      members.add(new ApiUserGroupInfo(ug));
    }
    Collections.sort(members, ApiUserGroupInfo::orderByRoleAndName);
  }
}
