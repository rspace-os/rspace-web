package com.researchspace.service.resourceaccess;

import com.researchspace.model.User;
import com.researchspace.model.resourceaccess.ResourceGranteeKind;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Defines the ordered roles and capabilities for one protected resource type. */
public interface ResourceRoleScheme {

  String OWNER_ROLE = "OWNER";
  String MANAGER_ROLE = "MANAGER";
  String READ_RESOURCE_CAPABILITY = "READ_RESOURCE";

  String key();

  /** Returns roles in descending rank order. */
  List<ResourceRole> roles();

  Set<String> capabilities(String roleKey);

  Set<ResourceGranteeKind> allowedGranteeKinds(String roleKey);

  /** Roles for which every protected resource must retain a direct assignment. */
  default Set<String> requiredPersistedRoles() {
    return Set.of(OWNER_ROLE);
  }

  Optional<String> implicitRole(User subject);

  /** Fails fast when this scheme cannot be resolved safely as one highest effective role. */
  default void validate() {
    ResourceRoleSchemeValidator.validate(this);
  }
}
