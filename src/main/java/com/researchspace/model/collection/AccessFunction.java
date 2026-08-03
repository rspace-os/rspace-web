package com.researchspace.model.collection;

import com.researchspace.model.Role;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * One access-control function, reusable at collection, row, or field scope.
 *
 * <p>At collection scope a function may return an {@link AccessResult.AllowedWhere} to constrain
 * the rows affected by an operation. At row scope it can inspect {@link AccessContext#targetId()}.
 * At field scope it is a boolean gate: {@link #allowsField(AccessContext)} fails closed if a row
 * constraint is returned where it cannot be enforced.
 *
 * <p>Implementations must be pure and must not perform I/O per invocation; use {@link
 * AccessContext#computeOnce} for anything derived that costs a lookup.
 */
@FunctionalInterface
public interface AccessFunction {

  AccessResult check(AccessContext context);

  default Optional<AccessDocumentation> documentation() {
    return Optional.empty();
  }

  static AccessFunction requireDocumented(AccessFunction function) {
    Objects.requireNonNull(function, "Access function");
    if (function.documentation().isEmpty()) {
      throw new IllegalArgumentException("Access function must be documented");
    }
    return function;
  }

  /** Applies this function as a field gate, where row constraints are not meaningful. */
  default boolean allowsField(AccessContext context) {
    AccessResult result = check(context);
    if (result.constraintOrEmpty().isPresent()) {
      throw new IllegalStateException("A field access function cannot return a row constraint");
    }
    return !result.isDenied();
  }

  /** Adapts a boolean predicate for field or row-specific access. */
  static AccessFunction when(Predicate<AccessContext> predicate) {
    Objects.requireNonNull(predicate, "Access predicate");
    return documented(
        "Access depends on the caller and target resource.",
        Set.of(AccessPolicy.FORBIDDEN),
        context ->
            predicate.test(context)
                ? AccessResult.allowed()
                : AccessResult.denied(AccessPolicy.FORBIDDEN));
  }

  static AccessFunction documented(
      String description, Set<String> denialReasonCodes, AccessFunction delegate) {
    return documented(new AccessDocumentation(description, denialReasonCodes), delegate);
  }

  static AccessFunction documented(AccessDocumentation documentation, AccessFunction delegate) {
    Objects.requireNonNull(documentation, "Access documentation");
    Objects.requireNonNull(delegate, "Access function");
    return new AccessFunction() {
      @Override
      public AccessResult check(AccessContext context) {
        return delegate.check(context);
      }

      @Override
      public Optional<AccessDocumentation> documentation() {
        return Optional.of(documentation);
      }
    };
  }

  /** Anonymous callers included. */
  static AccessFunction anyone() {
    return documented(
        new AccessDocumentation(
            "Anyone may perform this action, including anonymous callers.",
            Set.of(),
            AccessDocumentation.AuthenticationRequirement.PUBLIC),
        context -> AccessResult.allowed());
  }

  /** Any authenticated caller. */
  static AccessFunction authenticated() {
    return documented(
        "A logged-in session is required.",
        Set.of(AccessPolicy.AUTHENTICATION_REQUIRED),
        context ->
            context.isAuthenticated()
                ? AccessResult.allowed()
                : AccessResult.denied(AccessPolicy.AUTHENTICATION_REQUIRED));
  }

  /** System administrators only. */
  static AccessFunction sysadmin() {
    return documented(
        "A logged-in system administrator is required.",
        Set.of(AccessPolicy.AUTHENTICATION_REQUIRED, AccessPolicy.FORBIDDEN),
        context -> {
          if (!context.isAuthenticated()) {
            return AccessResult.denied(AccessPolicy.AUTHENTICATION_REQUIRED);
          }
          return context.user().hasRole(Role.SYSTEM_ROLE)
              ? AccessResult.allowed()
              : AccessResult.denied(AccessPolicy.FORBIDDEN);
        });
  }

  /**
   * Restricts a collection to the row whose identifier is the caller's user id.
   *
   * <p>This function returns a row constraint and is therefore intended for collection or row
   * access, not field access. A non-matching row remains indistinguishable from a missing row.
   */
  static AccessFunction selfOnly(String idField) {
    if (idField == null || idField.isBlank()) {
      throw new IllegalArgumentException("Self-access ID field must not be blank");
    }
    return documented(
        "A logged-in user may access only the resource identified by their own user id.",
        Set.of(AccessPolicy.AUTHENTICATION_REQUIRED),
        context -> {
          if (!context.isAuthenticated()) {
            return AccessResult.denied(AccessPolicy.AUTHENTICATION_REQUIRED);
          }
          return AccessResult.allowedWhere(
              new FilterExpression.Comparison(
                  idField,
                  CollectionDescription.Operator.EQUAL,
                  List.of(context.user().getId()),
                  false));
        });
  }

  /** Restricts a collection by its conventional {@code id} field to the caller's user id. */
  static AccessFunction selfOnly() {
    return selfOnly("id");
  }

  /** System administrators may access every row; other callers are restricted to themselves. */
  static AccessFunction sysadminOrSelf(String idField) {
    AccessFunction self = selfOnly(idField);
    return documented(
        "A logged-in system administrator may access every resource; other users may access only "
            + "the resource identified by their own user id.",
        Set.of(AccessPolicy.AUTHENTICATION_REQUIRED),
        context ->
            context.isAuthenticated() && context.user().hasRole(Role.SYSTEM_ROLE)
                ? AccessResult.allowed()
                : self.check(context));
  }

  /** Applies {@link #sysadminOrSelf(String)} using the conventional {@code id} field. */
  static AccessFunction sysadminOrSelf() {
    return sysadminOrSelf("id");
  }

  /** Never permitted, whoever asks. */
  static AccessFunction never() {
    return documented(
        "This action is not permitted.",
        Set.of(AccessPolicy.FORBIDDEN),
        context -> AccessResult.denied(AccessPolicy.FORBIDDEN));
  }
}
