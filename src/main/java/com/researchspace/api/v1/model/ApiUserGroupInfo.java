package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.model.UserGroup;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Information about user's role in a group */
@Data
@NoArgsConstructor
@JsonPropertyOrder(value = {"id", "username", "role"})
public class ApiUserGroupInfo {

  @JsonProperty("id")
  private Long id = null;

  @JsonProperty("username")
  private String username = null;

  @JsonProperty("role")
  private String role = null;

  public ApiUserGroupInfo(UserGroup userGroup) {
    this.id = userGroup.getUser().getId();
    this.username = userGroup.getUser().getUsername();
    String roleString = "";
    switch (userGroup.getRoleInGroup()) {
      case RS_LAB_ADMIN:
        roleString = "LAB_ADMIN";
        break;
      case PI:
        roleString = "PI";
        break;
      case GROUP_OWNER:
        roleString = "GROUP_OWNER";
        break;
      default:
        roleString = "USER";
    }
    this.role = roleString;
  }

  /**
   * PI should be the 1st listed user
   *
   * @param i1
   * @param i2
   * @return
   */
  public static int orderByRoleAndName(ApiUserGroupInfo i1, ApiUserGroupInfo i2) {
    int byRole = orderingValue(i1.getRole()).compareTo(orderingValue(i2.getRole()));
    return byRole != 0 ? byRole : i1.getUsername().compareTo(i2.getUsername());
  }

  static Integer orderingValue(String role) {
    switch (role) {
      case "PI":
        return 1;
      case "LAB_ADMIN":
        return 2;
      default:
        return 10;
    }
  }
}
