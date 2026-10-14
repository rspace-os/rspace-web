package com.researchspace.service.resourceaccess;

import com.researchspace.model.User;
import com.researchspace.model.resourceaccess.ResourceGranteeKind;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

class TestScheme implements ResourceRoleScheme {

  static final Set<ResourceGranteeKind> PEOPLE =
      Set.of(ResourceGranteeKind.USER, ResourceGranteeKind.GROUP);

  private static final Set<ResourceGranteeKind> ALL_GRANTEES =
      Set.of(ResourceGranteeKind.USER, ResourceGranteeKind.GROUP, ResourceGranteeKind.AUDIENCE);

  private final List<ResourceRole> roles;
  private final Map<String, Set<String>> capabilities;
  private final Set<String> requiredPersistedRoles;
  private final Set<ResourceGranteeKind> allowedGranteeKinds;
  private final Function<User, Optional<String>> implicitRole;

  TestScheme(
      List<ResourceRole> roles,
      Map<String, Set<String>> capabilities,
      Set<String> requiredPersistedRoles) {
    this(roles, capabilities, requiredPersistedRoles, PEOPLE, ignored -> Optional.empty());
  }

  private TestScheme(
      List<ResourceRole> roles,
      Map<String, Set<String>> capabilities,
      Set<String> requiredPersistedRoles,
      Set<ResourceGranteeKind> allowedGranteeKinds,
      Function<User, Optional<String>> implicitRole) {
    this.roles = roles;
    this.capabilities = capabilities;
    this.requiredPersistedRoles = requiredPersistedRoles;
    this.allowedGranteeKinds = allowedGranteeKinds;
    this.implicitRole = implicitRole;
  }

  static TestScheme resolverFixture() {
    List<ResourceRole> roles =
        List.of(
            new ResourceRole("OWNER", 40),
            new ResourceRole("MANAGER", 30),
            new ResourceRole("CONTRIBUTOR", 20),
            new ResourceRole("READER", 10));
    Map<String, Set<String>> capabilities =
        Map.of(
            "OWNER", Set.of("READ_RESOURCE", "CONTRIBUTE", "MANAGE", "OWN"),
            "MANAGER", Set.of("READ_RESOURCE", "CONTRIBUTE", "MANAGE"),
            "CONTRIBUTOR", Set.of("READ_RESOURCE", "CONTRIBUTE"),
            "READER", Set.of("READ_RESOURCE"));
    return new TestScheme(
        roles,
        capabilities,
        Set.of("OWNER"),
        ALL_GRANTEES,
        subject -> subject.hasSysadminRole() ? Optional.of("OWNER") : Optional.empty());
  }

  @Override
  public String key() {
    return "test-resources";
  }

  @Override
  public List<ResourceRole> roles() {
    return roles;
  }

  @Override
  public Set<String> capabilities(String roleKey) {
    return capabilities.get(roleKey);
  }

  @Override
  public Set<ResourceGranteeKind> allowedGranteeKinds(String roleKey) {
    return allowedGranteeKinds;
  }

  @Override
  public Set<String> requiredPersistedRoles() {
    return requiredPersistedRoles;
  }

  @Override
  public Optional<String> implicitRole(User subject) {
    return implicitRole.apply(subject);
  }
}
