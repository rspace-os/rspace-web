package com.researchspace.model.collection;

/**
 * Collection-level access, one check per operation, after PayloadCMS's {@code access} config.
 *
 * <p>The default is {@link #authenticated()} rather than public. Registering a collection
 * previously made its reads anonymous, because the generic controller annotated them
 * {@code @PublicApiV2}; a collection must now opt into anonymous reads explicitly, so forgetting to
 * think about access fails closed.
 */
public record AccessPolicy(
    AccessFunction read, AccessFunction create, AccessFunction update, AccessFunction delete) {

  public static final String AUTHENTICATION_REQUIRED = "errors.api.v2.authenticationRequired";
  public static final String FORBIDDEN = "errors.api.v2.forbidden";

  public AccessPolicy {
    read = AccessFunction.requireDocumented(read);
    create = AccessFunction.requireDocumented(create);
    update = AccessFunction.requireDocumented(update);
    delete = AccessFunction.requireDocumented(delete);
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
        AccessFunction.authenticated());
  }

  /** Anonymous reads and system-administrator writes. */
  public static AccessPolicy publicReadsSysadminWrites() {
    return new AccessPolicy(
        AccessFunction.anyone(),
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
        AccessFunction.sysadmin());
  }

  /** Readable subject to {@code read}, with every mutation refused. */
  public static AccessPolicy readOnly(AccessFunction read) {
    return new AccessPolicy(
        read, AccessFunction.never(), AccessFunction.never(), AccessFunction.never());
  }

  public AccessFunction forOperation(AccessContext.Operation operation) {
    return switch (operation) {
      case READ -> read;
      case CREATE -> create;
      case UPDATE -> update;
      case DELETE -> delete;
    };
  }
}
