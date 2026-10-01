package com.researchspace.service.resourceaccess;

import com.researchspace.model.User;
import com.researchspace.model.resourceaccess.ResourceAccess;
import java.util.Optional;

/** Service-level adapter from a protected domain resource to its access aggregate and hooks. */
public interface ProtectedResourceAccess<T, ID> {

  /** Whether the resource family is currently available to this subject. */
  default boolean featureEnabled(User subject) {
    return true;
  }

  Optional<T> find(ID id);

  Optional<T> lock(ID id);

  /** Returns the persisted access aggregate for independently managed resources. */
  ResourceAccess access(T resource);

  /** Whether this resource derives access from another permission source. */
  default boolean isInherited(T resource) {
    return false;
  }

  /** Resolves the effective access for an inherited resource and subject. */
  default ResolvedResourceAccess resolveInherited(T resource, User subject) {
    return ResolvedResourceAccess.none();
  }

  /**
   * Resolves inherited access for a write, allowing resources to refresh mutable permission facts.
   */
  default ResolvedResourceAccess resolveInheritedForMutation(T resource, User subject) {
    return resolveInherited(resource, subject);
  }

  /** Builds the access document for an inherited resource after it has been authorized. */
  default ResourceAccessDocument inheritedDocument(
      T resource, User subject, ResolvedResourceAccess resolved) {
    throw new UnsupportedOperationException(
        "Inherited access requires an inheritedDocument implementation");
  }

  /** Domain object whose existing identifier is used for access-change audit entries. */
  default Object auditTarget(T resource) {
    return resource;
  }

  String viewAccessCapability();

  String manageAssignmentsCapability();

  String manageOwnersCapability();

  /** Runs domain-specific mutation validation after the caller has been authorized. */
  default void beforeAccessMutation(T resource) {}

  /** Capability required by the registered generic audit route. */
  default String viewAuditCapability() {
    return viewAccessCapability();
  }

  /** Runs any domain-specific leave validation while the resource is locked. */
  default void beforeSelfRemoval(T resource, User subject, User actor) {}
}
