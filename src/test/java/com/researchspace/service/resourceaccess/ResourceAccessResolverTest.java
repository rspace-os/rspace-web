package com.researchspace.service.resourceaccess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.resourceaccess.ResourceAccess;
import com.researchspace.model.resourceaccess.ResourceAudience;
import com.researchspace.model.resourceaccess.ResourceGranteeKind;
import com.researchspace.model.resourceaccess.ResourceRoleAssignment;
import com.researchspace.model.resourceaccess.ResourceRoleSource;
import com.researchspace.model.resourceaccess.ResourceRoleSourceKind;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResourceAccessResolverTest {

  private static final String OWNER = "OWNER";
  private static final String MANAGER = "MANAGER";
  private static final String CONTRIBUTOR = "CONTRIBUTOR";
  private static final String READER = "READER";

  private final TestScheme scheme = new TestScheme();
  private final ResourceAccessResolver resolver =
      new ResourceAccessResolver(new ResourceRoleSchemeRegistry(List.of(scheme)));

  private User subject;
  private ResourceAccess access;

  @BeforeEach
  void setUp() {
    subject = user(12L, "Alex", true);
    access = new ResourceAccess(scheme.key(), subject, new Date(1_000));
  }

  @Test
  void resolvesHighestRoleAndPreservesEveryApplicableSource() {
    Group contributorGroup = group(41L, "Imaging", 3);
    Group managerGroup = group(42L, "Core Managers", 2);
    when(subject.getGroups()).thenReturn(Set.of(contributorGroup, managerGroup));
    access.addAssignment(ResourceRoleAssignment.forUser(READER, subject));
    access.addAssignment(ResourceRoleAssignment.forGroup(CONTRIBUTOR, contributorGroup));
    access.addAssignment(ResourceRoleAssignment.forGroup(MANAGER, managerGroup));
    access.addAssignment(
        ResourceRoleAssignment.forAudience(READER, ResourceAudience.ALL_USERS, "All users"));

    ResolvedResourceAccess resolved = resolver.resolve(access, subject);

    assertEquals(Optional.of(MANAGER), resolved.effectiveRole());
    assertEquals(scheme.capabilities(MANAGER), resolved.capabilities());
    assertEquals(4, resolved.roleSources().size());
    assertEquals(
        Set.of(
            ResourceRoleSourceKind.DIRECT,
            ResourceRoleSourceKind.GROUP,
            ResourceRoleSourceKind.AUDIENCE),
        resolved.roleSources().stream()
            .map(ResourceRoleSource::kind)
            .collect(java.util.stream.Collectors.toSet()));
  }

  @Test
  void ignoresUnavailablePrincipalsAndUnrelatedGroups() {
    User disabledDirect = user(12L, "Old Alex", false);
    Group emptyGroup = group(41L, "Empty", 0);
    Group unrelatedGroup = group(42L, "Unrelated", 5);
    when(subject.getGroups()).thenReturn(Set.of(emptyGroup));
    access.addAssignment(ResourceRoleAssignment.forUser(OWNER, disabledDirect));
    access.addAssignment(ResourceRoleAssignment.forGroup(MANAGER, emptyGroup));
    access.addAssignment(ResourceRoleAssignment.forGroup(CONTRIBUTOR, unrelatedGroup));
    access.addAssignment(
        ResourceRoleAssignment.forAudience(READER, ResourceAudience.ALL_USERS, "All users"));

    ResolvedResourceAccess resolved = resolver.resolve(access, subject);

    assertEquals(Optional.of(READER), resolved.effectiveRole());
    assertEquals(1, resolved.roleSources().size());
    assertEquals(ResourceRoleSourceKind.AUDIENCE, resolved.roleSources().get(0).kind());
  }

  @Test
  void disabledSubjectReceivesNoDirectGroupAudienceOrImplicitAccess() {
    User disabledSysadmin = user(12L, "Disabled", false);
    when(disabledSysadmin.hasSysadminRole()).thenReturn(true);
    access.addAssignment(ResourceRoleAssignment.forUser(OWNER, disabledSysadmin));
    access.addAssignment(
        ResourceRoleAssignment.forAudience(READER, ResourceAudience.ALL_USERS, "All users"));

    ResolvedResourceAccess resolved = resolver.resolve(access, disabledSysadmin);

    assertTrue(resolved.effectiveRole().isEmpty());
    assertTrue(resolved.roleSources().isEmpty());
    assertTrue(resolved.capabilities().isEmpty());
  }

  @Test
  void representedSysadminReceivesImplicitOwnerWithoutPersistedAssignment() {
    when(subject.hasSysadminRole()).thenReturn(true);

    ResolvedResourceAccess resolved = resolver.resolve(access, subject);

    assertEquals(Optional.of(OWNER), resolved.effectiveRole());
    assertEquals(List.of(ResourceRoleSource.implicit(OWNER)), resolved.roleSources());
  }

  private static User user(long id, String name, boolean enabled) {
    User user = mock(User.class);
    when(user.getId()).thenReturn(id);
    when(user.getDisplayName()).thenReturn(name);
    when(user.getUsername()).thenReturn(name.toLowerCase().replace(' ', '.'));
    when(user.isEnabled()).thenReturn(enabled);
    when(user.getGroups()).thenReturn(Set.of());
    return user;
  }

  private static Group group(long id, String name, int enabledMembers) {
    Group group = mock(Group.class);
    when(group.getId()).thenReturn(id);
    when(group.getDisplayName()).thenReturn(name);
    when(group.getUniqueName()).thenReturn(name.toLowerCase().replace(' ', '-'));
    when(group.getEnabledMemberSize()).thenReturn(enabledMembers);
    return group;
  }

  private static final class TestScheme implements ResourceRoleScheme {

    private static final List<ResourceRole> ROLES =
        List.of(
            new ResourceRole(OWNER, 40),
            new ResourceRole(MANAGER, 30),
            new ResourceRole(CONTRIBUTOR, 20),
            new ResourceRole(READER, 10));
    private static final Map<String, Set<String>> CAPABILITIES =
        Map.of(
            OWNER, Set.of("READ_RESOURCE", "CONTRIBUTE", "MANAGE", "OWN"),
            MANAGER, Set.of("READ_RESOURCE", "CONTRIBUTE", "MANAGE"),
            CONTRIBUTOR, Set.of("READ_RESOURCE", "CONTRIBUTE"),
            READER, Set.of("READ_RESOURCE"));

    @Override
    public String key() {
      return "test-resources";
    }

    @Override
    public List<ResourceRole> roles() {
      return ROLES;
    }

    @Override
    public Set<String> capabilities(String roleKey) {
      return CAPABILITIES.get(roleKey);
    }

    @Override
    public Set<ResourceGranteeKind> allowedGranteeKinds(String roleKey) {
      return Set.of(
          ResourceGranteeKind.USER, ResourceGranteeKind.GROUP, ResourceGranteeKind.AUDIENCE);
    }

    @Override
    public Optional<String> implicitRole(User subject) {
      return subject.hasSysadminRole() ? Optional.of(OWNER) : Optional.empty();
    }
  }
}
