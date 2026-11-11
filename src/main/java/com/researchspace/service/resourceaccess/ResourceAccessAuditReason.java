package com.researchspace.service.resourceaccess;

/** Semantic reason for one resource-access audit delta. */
public enum ResourceAccessAuditReason {
  ASSIGNMENT_ADD,
  ASSIGNMENT_REMOVE,
  ASSIGNMENT_CHANGE,
  ALL_USERS_ON,
  ALL_USERS_OFF,
  DIRECT_LEAVE,
  OWNERSHIP_TRANSFER
}
