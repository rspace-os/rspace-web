package com.researchspace.service.resourceaccess;

/** Typed resource-access failure translated by the REST API without exposing hidden resources. */
public class ResourceAccessException extends RuntimeException {

  public enum Reason {
    NOT_FOUND,
    FORBIDDEN,
    OWNER_REQUIRED,
    STALE,
    INVALID_GRANTEE,
    INVALID_ROLE,
    DUPLICATE_GRANTEE,
    ASSIGNMENT_LIMIT,
    SELF_REMOVAL_REQUIRES_LEAVE
  }

  private final Reason reason;

  public ResourceAccessException(Reason reason) {
    super(reason.name());
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
