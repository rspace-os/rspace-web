package com.researchspace.service.resourceaccess;

import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.collection.AccessDocumentation;
import com.researchspace.model.collection.AccessFunction;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.ResourceRoleMembershipConstraint;
import com.researchspace.model.resourceaccess.ResourceGranteeKind;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Database-enforceable collection read policy for one registered resource role scheme. */
public final class ResourceRoleReadAccess implements AccessFunction {

  private final ResourceRoleScheme scheme;
  private final String resourceAccessIdPath;
  private final Set<String> readableRoles;
  private final boolean supportsAllUsers;

  public ResourceRoleReadAccess(
      ResourceRoleSchemeRegistry schemes, String schemeKey, String resourceAccessIdPath) {
    this.scheme = Objects.requireNonNull(schemes, "schemes").getRequired(schemeKey);
    this.resourceAccessIdPath =
        Objects.requireNonNull(resourceAccessIdPath, "resourceAccessIdPath");
    readableRoles =
        scheme.roles().stream()
            .map(ResourceRole::key)
            .filter(
                role ->
                    scheme.capabilities(role).contains(ResourceRoleScheme.READ_RESOURCE_CAPABILITY))
            .collect(Collectors.toUnmodifiableSet());
    supportsAllUsers =
        readableRoles.stream()
            .anyMatch(
                role -> scheme.allowedGranteeKinds(role).contains(ResourceGranteeKind.AUDIENCE));
    if (readableRoles.isEmpty()) {
      throw new IllegalArgumentException("Resource role scheme has no readable role: " + schemeKey);
    }
  }

  @Override
  public AccessResult check(com.researchspace.model.collection.AccessContext context) {
    User subject = context.user();
    if (subject == null || !subject.isEnabled() || subject.getId() == null) {
      return AccessResult.denied(AccessPolicy.AUTHENTICATION_REQUIRED);
    }
    if (scheme
        .implicitRole(subject)
        .filter(
            role -> scheme.capabilities(role).contains(ResourceRoleScheme.READ_RESOURCE_CAPABILITY))
        .isPresent()) {
      return AccessResult.allowed();
    }
    Set<Long> groupIds =
        subject.getGroups().stream()
            .filter(group -> group.getId() != null && group.getEnabledMemberSize() > 0)
            .map(Group::getId)
            .collect(Collectors.toUnmodifiableSet());
    return AccessResult.allowedWhere(
        new ResourceRoleMembershipConstraint(
            resourceAccessIdPath, subject.getId(), groupIds, readableRoles, supportsAllUsers));
  }

  @Override
  public java.util.Optional<AccessDocumentation> documentation() {
    return java.util.Optional.of(
        new AccessDocumentation(
            "A logged-in user with a current readable resource role is required.",
            Set.of(AccessPolicy.AUTHENTICATION_REQUIRED, AccessPolicy.FORBIDDEN)));
  }
}
