package com.researchspace.model;

public enum GroupType {
  /** A regular lab group */
  LAB_GROUP("groups.types.labGroup"),

  /** A user-created and managed collaboration group. */
  COLLABORATION_GROUP("groups.types.collaborationGroup"),

  /** A user-created group that has no PI */
  PROJECT_GROUP("groups.types.projectGroup");

  private final String labelKey;

  GroupType(String labelKey) {
    this.labelKey = labelKey;
  }

  public String getLabelKey() {
    return labelKey;
  }
}
