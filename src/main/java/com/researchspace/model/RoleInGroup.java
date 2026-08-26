package com.researchspace.model;

/**
 * Roles for a user within a group. These roles are additive; Admin role implies default role, and
 * PI role implies Admin role.
 */
public enum RoleInGroup {

  /** No special roles, a regular group member */
  DEFAULT("groups.roles.user"), // 0 in DB

  /** A principal investigator /lab head role, can see all records in the group. */
  PI("groups.roles.pi"), // 1 in DB

  /**
   * Can handle group admin roles (e.g., add/remove members, etc) without necessarily being able to
   * see in all record contents.
   */
  RS_LAB_ADMIN("groups.roles.labAdmin"), // 2 in DB

  /** Leads a user collaboration group. */
  GROUP_OWNER("groups.roles.groupOwner");

  /**
   * A display string for display to end-users
   *
   * @return
   */
  public String getLabelKey() {
    return labelKey;
  }

  private final String labelKey;

  RoleInGroup(String labelKey) {
    this.labelKey = labelKey;
  }

  public static RoleInGroup getRoleFromString(String role) {
    if (RoleInGroup.GROUP_OWNER.toString().equalsIgnoreCase(role)) {
      return RoleInGroup.GROUP_OWNER;
    } else if (RoleInGroup.RS_LAB_ADMIN.toString().equalsIgnoreCase(role)) {
      return RoleInGroup.RS_LAB_ADMIN;
    } else if (RoleInGroup.PI.toString().equalsIgnoreCase(role)) {
      return RoleInGroup.PI;
    } else if (RoleInGroup.DEFAULT.toString().equalsIgnoreCase(role)) {
      return RoleInGroup.DEFAULT;
    }
    return null;
  }
}
