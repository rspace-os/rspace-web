package com.researchspace.model.collection;

/**
 * Collection-level access, with one check per operation.
 *
 * <p>The default is {@link #authenticated()} rather than public. A collection must opt into
 * anonymous reads explicitly, so forgetting to think about access fails closed.
 */
public record AccessPolicy(
    /** Reading rows and resolving relationship targets. */
    AccessFunction readAccess,
    /** Creating one or many resources; parsed candidates are available from the context. */
    AccessFunction createAccess,
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
    return all(AccessFunction.authenticated());
  }

  /** Readable subject to {@code read}, with every mutation refused. */
  public static AccessPolicy readOnly(AccessFunction read) {
    return withWrites(read, AccessFunction.never());
  }

  private static AccessPolicy all(AccessFunction function) {
    return new AccessPolicy(function, function, function, function, function);
  }

  private static AccessPolicy withWrites(AccessFunction read, AccessFunction write) {
    return new AccessPolicy(read, write, write, write, write);
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
