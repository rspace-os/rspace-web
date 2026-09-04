package com.researchspace.model.dto;

import com.researchspace.model.Group;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GroupInfo {
  private String groupName;
  private Long groupId;
  private String roleInGroup;

  public GroupInfo(Group g, String roleInGroup) {
    this.groupName = g.getDisplayName();
    this.groupId = g.getId();
    this.roleInGroup = roleInGroup;
  }
}
