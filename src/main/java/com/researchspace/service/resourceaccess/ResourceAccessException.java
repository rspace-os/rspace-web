package com.researchspace.service.resourceaccess;

/** Typed resource-access failure translated by the REST API without exposing hidden resources. */
public class ResourceAccessException extends RuntimeException {

  public enum Reason {
    NOT_FOUND("errors.api.v2.notFound"),
    FORBIDDEN("errors.api.v2.resourceAccess.forbidden"),
    INHERITED_READ_ONLY("errors.api.v2.resourceAccess.inheritedReadOnly"),
    OWNER_REQUIRED("errors.api.v2.resourceAccess.ownerRequired"),
    STALE("errors.api.v2.resourceAccess.stale"),
    INVALID_GRANTEE("errors.api.v2.resourceAccess.invalidGrantee"),
    INVALID_ROLE("errors.api.v2.resourceAccess.invalidRole"),
    DUPLICATE_GRANTEE("errors.api.v2.resourceAccess.duplicateGrantee"),
    ASSIGNMENT_LIMIT("errors.api.v2.resourceAccess.assignmentLimit"),
    SELF_REMOVAL_REQUIRES_LEAVE("errors.api.v2.resourceAccess.selfRemovalRequiresLeave");

    private final String messageKey;

    Reason(String messageKey) {
      this.messageKey = messageKey;
    }

    /** The user-facing phrase for this reason; the enum name stays the machine code. */
    public String messageKey() {
      return messageKey;
    }
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
