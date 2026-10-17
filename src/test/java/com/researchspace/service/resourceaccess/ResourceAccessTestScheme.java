package com.researchspace.service.resourceaccess;

import com.researchspace.Constants;
import com.researchspace.model.resourceaccess.ResourceAudience;
import com.researchspace.model.resourceaccess.ResourceGranteeKind;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Shared scheme for generic access tests, including fixed audiences and accountable owners. */
public final class ResourceAccessTestScheme implements ResourceRoleScheme {

  public static final String SCHEME_KEY = "test-resources";
  public static final String OWNER = OWNER_ROLE;
  public static final String MANAGER = MANAGER_ROLE;
  public static final String EDITOR = "EDITOR";
  public static final String VIEWER = "VIEWER";
  public static final String NO_ACCESS = "NO_ACCESS";

  public static final String READ_RESOURCE = READ_RESOURCE_CAPABILITY;
  public static final String EDIT_RESOURCE = "EDIT_RESOURCE";
  public static final String MANAGE_ASSIGNMENTS = "MANAGE_ASSIGNMENTS";
  public static final String MANAGE_OWNERS = "MANAGE_OWNERS";

  private static final Set<ResourceGranteeKind> PEOPLE =
      Set.of(ResourceGranteeKind.USER, ResourceGranteeKind.GROUP);
  private static final Map<String, Set<String>> CAPABILITIES =
      Map.of(
          OWNER, Set.of(READ_RESOURCE, EDIT_RESOURCE, MANAGE_ASSIGNMENTS, MANAGE_OWNERS),
          MANAGER, Set.of(READ_RESOURCE, EDIT_RESOURCE, MANAGE_ASSIGNMENTS),
          EDITOR, Set.of(READ_RESOURCE, EDIT_RESOURCE),
          VIEWER, Set.of(READ_RESOURCE),
          NO_ACCESS, Set.of());

  @Override
  public String key() {
    return SCHEME_KEY;
  }

  @Override
  public List<ResourceRole> roles() {
    return List.of(
        new ResourceRole(OWNER, 40),
        new ResourceRole(MANAGER, 30),
        new ResourceRole(EDITOR, 20),
        new ResourceRole(VIEWER, 10),
        new ResourceRole(NO_ACCESS, 0));
  }

  @Override
  public Set<String> capabilities(String roleKey) {
    Set<String> capabilities = CAPABILITIES.get(roleKey);
    if (capabilities == null) {
      throw new IllegalArgumentException("Unknown test role: " + roleKey);
    }
    return capabilities;
  }

  @Override
  public Set<ResourceGranteeKind> allowedGranteeKinds(String roleKey) {
    return switch (roleKey) {
      case OWNER, MANAGER, VIEWER -> PEOPLE;
      case EDITOR ->
          Set.of(ResourceGranteeKind.USER, ResourceGranteeKind.GROUP, ResourceGranteeKind.AUDIENCE);
      case NO_ACCESS -> Set.of(ResourceGranteeKind.AUDIENCE);
      default -> throw new IllegalArgumentException("Unknown test role: " + roleKey);
    };
  }

  @Override
  public Map<ResourceAudience, Set<String>> fixedAudienceRoles() {
    return Map.of(ResourceAudience.ALL_USERS, Set.of(EDITOR, NO_ACCESS));
  }

  @Override
  public Optional<String> implicitRole(Set<String> globalRoleNames) {
    return globalRoleNames.contains(Constants.SYSADMIN_ROLE)
        ? Optional.of(OWNER)
        : Optional.empty();
  }
}
