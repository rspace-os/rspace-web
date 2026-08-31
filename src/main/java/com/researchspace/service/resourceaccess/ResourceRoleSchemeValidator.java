package com.researchspace.service.resourceaccess;

import com.researchspace.model.resourceaccess.ResourceGranteeKind;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class ResourceRoleSchemeValidator {

  private ResourceRoleSchemeValidator() {}

  static void validate(ResourceRoleScheme scheme) {
    Objects.requireNonNull(scheme, "scheme");
    requireNonBlank(scheme.key(), "Resource role scheme key");

    List<ResourceRole> roles = Objects.requireNonNull(scheme.roles(), "roles");
    if (roles.size() < 2) {
      throw new IllegalArgumentException("A role scheme must define OWNER and MANAGER roles");
    }

    validateRoleOrdering(roles);
    validateRequiredPersistedRoles(scheme, roles);
    validateRoleDefinitions(scheme, roles);
    validateMonotonicCapabilities(scheme, roles);
  }

  private static void validateRoleOrdering(List<ResourceRole> roles) {
    Set<String> keys = new HashSet<>();
    Set<Integer> ranks = new HashSet<>();
    int previousRank = Integer.MAX_VALUE;

    for (ResourceRole role : roles) {
      Objects.requireNonNull(role, "role");
      if (!keys.add(role.key())) {
        throw new IllegalArgumentException("Duplicate role key: " + role.key());
      }
      if (!ranks.add(role.rank())) {
        throw new IllegalArgumentException("Duplicate role rank: " + role.rank());
      }
      if (role.rank() >= previousRank) {
        throw new IllegalArgumentException("Roles must be ordered from highest to lowest rank");
      }
      previousRank = role.rank();
    }

    if (!roles.get(0).key().equals(ResourceRoleScheme.OWNER_ROLE)) {
      throw new IllegalArgumentException("OWNER must be the highest role");
    }
    if (!roles.get(1).key().equals(ResourceRoleScheme.MANAGER_ROLE)) {
      throw new IllegalArgumentException("MANAGER must be immediately below OWNER");
    }
  }

  private static void validateRequiredPersistedRoles(
      ResourceRoleScheme scheme, List<ResourceRole> roles) {
    Set<String> roleKeys =
        roles.stream().map(ResourceRole::key).collect(java.util.stream.Collectors.toSet());
    Set<String> requiredRoles =
        Objects.requireNonNull(scheme.requiredPersistedRoles(), "requiredPersistedRoles");
    if (!requiredRoles.contains(ResourceRoleScheme.OWNER_ROLE)) {
      throw new IllegalArgumentException("OWNER must be a required persisted role");
    }
    for (String requiredRole : requiredRoles) {
      requireNonBlank(requiredRole, "Required persisted role key");
      if (!roleKeys.contains(requiredRole)) {
        throw new IllegalArgumentException(
            "Required persisted role is not declared: " + requiredRole);
      }
    }
  }

  private static void validateRoleDefinitions(ResourceRoleScheme scheme, List<ResourceRole> roles) {
    for (ResourceRole role : roles) {
      Set<String> capabilities =
          Objects.requireNonNull(scheme.capabilities(role.key()), "capabilities for " + role.key());
      for (String capability : capabilities) {
        requireNonBlank(capability, "Capability key for " + role.key());
      }

      Set<ResourceGranteeKind> kinds =
          Objects.requireNonNull(
              scheme.allowedGranteeKinds(role.key()), "allowed grantee kinds for " + role.key());
      if (kinds.isEmpty()) {
        throw new IllegalArgumentException(
            "Role " + role.key() + " must allow at least one grantee kind");
      }
      if (kinds.stream().anyMatch(Objects::isNull)) {
        throw new IllegalArgumentException("Role " + role.key() + " has a null grantee kind");
      }
    }

    requireReadCapability(scheme, ResourceRoleScheme.OWNER_ROLE);
    requireReadCapability(scheme, ResourceRoleScheme.MANAGER_ROLE);
  }

  private static void validateMonotonicCapabilities(
      ResourceRoleScheme scheme, List<ResourceRole> roles) {
    for (int lowerIndex = roles.size() - 1; lowerIndex > 0; lowerIndex--) {
      ResourceRole lower = roles.get(lowerIndex);
      ResourceRole higher = roles.get(lowerIndex - 1);
      Set<String> missing = new HashSet<>(scheme.capabilities(lower.key()));
      missing.removeAll(scheme.capabilities(higher.key()));
      if (!missing.isEmpty()) {
        throw new IllegalArgumentException(
            "Higher role "
                + higher.key()
                + " omits capabilities from "
                + lower.key()
                + ": "
                + missing);
      }
    }
  }

  private static void requireReadCapability(ResourceRoleScheme scheme, String roleKey) {
    if (!scheme.capabilities(roleKey).contains(ResourceRoleScheme.READ_RESOURCE_CAPABILITY)) {
      throw new IllegalArgumentException(roleKey + " must include READ_RESOURCE");
    }
  }

  private static void requireNonBlank(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }
}
