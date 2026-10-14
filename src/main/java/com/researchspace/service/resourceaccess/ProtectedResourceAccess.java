package com.researchspace.service.resourceaccess;

import com.researchspace.model.User;
import com.researchspace.model.resourceaccess.ResourceAccess;
import java.util.Optional;

/** Service-level adapter from a protected domain resource to its access aggregate. */
public interface ProtectedResourceAccess<T, ID> {

  /** Whether the resource family is currently available to this subject. */
  default boolean featureEnabled(User subject) {
    return true;
  }

  Optional<T> find(ID id);

  Optional<T> lock(ID id);

  ResourceAccess access(T resource);

  String viewAccessCapability();

  String manageAssignmentsCapability();

  String manageOwnersCapability();

  /** Runs domain-specific mutation validation after the caller has been authorized. */
  default void beforeAccessMutation(T resource) {}

  /** Capability required by the registered generic audit route. */
  default String viewAuditCapability() {
    return viewAccessCapability();
  }
}
