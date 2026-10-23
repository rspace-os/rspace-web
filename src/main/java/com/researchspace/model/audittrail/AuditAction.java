package com.researchspace.model.audittrail;

/** The type of operation performed by auditable event. */
public enum AuditAction {
  /*
   * Declare these in alphabetical order to facilitate ordering and searching
   */
  CREATE,
  DELETE,
  DOWNLOAD,
  DUPLICATE,
  EXPORT,
  MOVE,
  READ,
  RENAME,
  REQUEST_APPROVED,
  REQUEST_CANCELLED,
  REQUEST_FULFILLED,
  REQUEST_REJECTED,
  REQUEST_SENT,
  RESTORE,
  SEARCH,
  SHARE,
  SIGN,
  TRANSFER,
  UNSHARE,
  VIEW,
  WITNESSED,
  WRITE
}
