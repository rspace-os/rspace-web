package com.researchspace.model.dto;

import com.researchspace.model.Group;
import com.researchspace.model.RoleInGroup;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GroupInfo {
  private String groupName;
  private Long groupId;
  private RoleInGroup roleInGroup;

  public GroupInfo(Group g, RoleInGroup roleInGroup) {
    this.groupName = g.getDisplayName();
    this.groupId = g.getId();
    this.roleInGroup = roleInGroup;
  }
}
