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

  ResourceAccess access(T resource);

  /** Domain object whose existing identifier is used for access-change audit entries. */
  default Object auditTarget(T resource) {
    return resource;
  }

  String viewAccessCapability();

  String manageAssignmentsCapability();

  String manageOwnersCapability();

  /** Capability required by the registered generic audit route. */
  default String viewAuditCapability() {
    return viewAccessCapability();
  }

  /** Runs any domain-specific leave validation while the resource is locked. */
  default void beforeSelfRemoval(T resource, User subject, User actor) {}
}
