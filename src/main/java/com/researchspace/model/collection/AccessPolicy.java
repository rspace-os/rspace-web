package com.researchspace.model.collection;

/**
 * Collection-level access, one check per operation, after PayloadCMS's {@code access} config.
 *
 * <p>The default is {@link #authenticated()} rather than public. Registering a collection
 * previously made its reads anonymous, because the generic controller treated them as public. A
 * collection must now opt into anonymous reads explicitly, so forgetting to think about access
 * fails closed.
 */
public record AccessPolicy(
    /** Reading rows and resolving relationship targets. */
    AccessFunction readAccess,
    /** Creating one or many resources; parsed candidates are available from the context. */
    AccessFunction createAccess,
    /** Updating existing resources. */
    AccessFunction updateAccess,
    /** Permanently removing resources. */
    AccessFunction deleteAccess,
    /** Transitioning resources to their domain-defined soft-deleted state. */
    AccessFunction softDeleteAccess) {

  public static final String AUTHENTICATION_REQUIRED = "errors.api.v2.authenticationRequired";
  public static final String FORBIDDEN = "errors.api.v2.forbidden";

  public AccessPolicy {
    readAccess = AccessFunction.requireDocumented(readAccess);
    createAccess = AccessFunction.requireDocumented(createAccess);
    updateAccess = AccessFunction.requireDocumented(updateAccess);
    deleteAccess = AccessFunction.requireDocumented(deleteAccess);
    softDeleteAccess = AccessFunction.requireDocumented(softDeleteAccess);
  }

  /**
   * Every operation requires an authenticated caller: the default for a collection that has not
   * declared a policy.
   */
  public static AccessPolicy authenticated() {
    return new AccessPolicy(
        AccessFunction.authenticated(),
        AccessFunction.authenticated(),
        AccessFunction.authenticated(),
        AccessFunction.authenticated(),
        AccessFunction.authenticated());
  }

  /** Anonymous reads and system-administrator writes. */
  public static AccessPolicy publicReadsSysadminWrites() {
    return new AccessPolicy(
        AccessFunction.anyone(),
        AccessFunction.sysadmin(),
        AccessFunction.sysadmin(),
        AccessFunction.sysadmin(),
        AccessFunction.sysadmin());
  }

  /** Authenticated reads and system-administrator writes. */
  public static AccessPolicy authenticatedReadsSysadminWrites() {
    return new AccessPolicy(
        AccessFunction.authenticated(),
        AccessFunction.sysadmin(),
        AccessFunction.sysadmin(),
        AccessFunction.sysadmin(),
        AccessFunction.sysadmin());
  }

  /** Readable subject to {@code read}, with every mutation refused. */
  public static AccessPolicy readOnly(AccessFunction read) {
    return new AccessPolicy(
        read,
        AccessFunction.never(),
        AccessFunction.never(),
        AccessFunction.never(),
        AccessFunction.never());
  }

  public AccessFunction forOperation(AccessContext.Operation operation) {
    return switch (operation) {
      case READ -> readAccess;
      case CREATE -> createAccess;
      case UPDATE -> updateAccess;
      case DELETE -> deleteAccess;
      case SOFT_DELETE -> softDeleteAccess;
    };
  }
}
