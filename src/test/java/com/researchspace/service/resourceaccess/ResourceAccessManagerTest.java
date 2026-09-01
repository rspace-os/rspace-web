package com.researchspace.service.resourceaccess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.booking.service.BookingResourceRoleScheme;
import com.researchspace.dao.resourceaccess.ResourceAccessDao;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.resourceaccess.ResourceAccess;
import com.researchspace.model.resourceaccess.ResourceAudience;
import com.researchspace.model.resourceaccess.ResourceRoleAssignment;
import com.researchspace.model.resourceaccess.ResourceRoleSource;
import com.researchspace.model.resourceaccess.ResourceRoleSourceKind;
import com.researchspace.service.MessageSourceUtils;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResourceAccessManagerTest {

  private final BookingResourceRoleScheme scheme = new BookingResourceRoleScheme();
  private final ResourceRoleSchemeRegistry schemes =
      new ResourceRoleSchemeRegistry(List.of(scheme));
  private final ResourceAccessResolver resolver = new ResourceAccessResolver(schemes);
  private final ResourceAccessDao gateway = mock(ResourceAccessDao.class);
  private final ResourceAccessDirectoryPolicy directory = mock(ResourceAccessDirectoryPolicy.class);
  private final MessageSourceUtils messages = mock(MessageSourceUtils.class);

  @SuppressWarnings("unchecked")
  private final ProtectedResourceAccess<Object, Long> resource =
      mock(ProtectedResourceAccess.class);

  private final ResourceAccessManager manager =
      new ResourceAccessManagerImpl(
          gateway,
          schemes,
          resolver,
          directory,
          Clock.fixed(Instant.parse("2026-08-31T08:00:00Z"), ZoneOffset.UTC),
          messages);

  private final Object protectedEntity = new Object();
  private User owner;
  private User other;
  private ResourceAccess access;

  @BeforeEach
  void setUp() {
    when(messages.getMessage(ResourceAudience.ALL_USERS.messageKey())).thenReturn("All users");
    owner = user(12L, "Owner");
    other = user(13L, "Other");
    access = new ResourceAccess(scheme.key(), owner, new Date(1_000));
    access.setId(5L);
    access.setVersion(7L);
    access.addAssignment(ResourceRoleAssignment.forUser(BookingResourceRoleScheme.OWNER, owner));
    access.addAssignment(
        ResourceRoleAssignment.forAudience(
            BookingResourceRoleScheme.BOOKER, ResourceAudience.ALL_USERS, "All users"));
    when(resource.lock(9L)).thenReturn(Optional.of(protectedEntity));
    when(resource.find(9L)).thenReturn(Optional.of(protectedEntity));
    when(resource.featureEnabled(any())).thenReturn(true);
    when(resource.access(protectedEntity)).thenReturn(access);
    when(resource.viewAccessCapability()).thenReturn(BookingResourceRoleScheme.MANAGE_ASSIGNMENTS);
    when(resource.manageAssignmentsCapability())
        .thenReturn(BookingResourceRoleScheme.MANAGE_ASSIGNMENTS);
    when(resource.manageOwnersCapability()).thenReturn(BookingResourceRoleScheme.MANAGE_OWNERS);
    when(gateway.lockAuthorizationFacts(access, owner)).thenReturn(owner);
    ResourceRoleAssignment otherBooker =
        ResourceRoleAssignment.forUser(BookingResourceRoleScheme.BOOKER, other);
    when(gateway.resolveAvailable("user:13", BookingResourceRoleScheme.BOOKER))
        .thenReturn(otherBooker);
    ResourceGranteeDirectoryEntry assignableOther = directoryEntry(other);
    when(directory.resolveAssignable(Set.of("user:13"), owner))
        .thenReturn(Map.of("user:13", assignableOther));
  }

  @Test
  void replacementAddsResolvedPrincipalAndFlushesBetweenRemovalsAndInserts() {
    ReplaceResourceAccess<Long> command =
        new ReplaceResourceAccess<>(
            9L,
            7L,
            grants(
                new ResourceAccessGrant("user:12", BookingResourceRoleScheme.OWNER),
                new ResourceAccessGrant("user:13", BookingResourceRoleScheme.BOOKER)));

    manager.replace(resource, command, owner, owner);

    assertEquals(
        Set.of("user:12", "user:13", "audience:all-users"),
        access.getAssignments().stream()
            .map(ResourceRoleAssignment::getGranteeKey)
            .collect(java.util.stream.Collectors.toSet()));
    // Twice: once after the removals so a re-used grantee key is free before its replacement row is
    // inserted, then once for the whole change.
    verify(gateway, times(2)).flush();
  }

  @Test
  void noOpKeepsVersionAndEmitsNoWrite() {
    ReplaceResourceAccess<Long> command =
        new ReplaceResourceAccess<>(
            9L, 7L, grants(new ResourceAccessGrant("user:12", BookingResourceRoleScheme.OWNER)));

    ResourceAccessDocument document = manager.replace(resource, command, owner, owner);

    assertEquals(7L, document.version());
    verify(gateway, never()).flush();
  }

  @Test
  void allUsersCannotBeRemovedOrAssignedAnUnsupportedRole() {
    ReplaceResourceAccess<Long> removed =
        new ReplaceResourceAccess<>(
            9L, 7L, List.of(new ResourceAccessGrant("user:12", BookingResourceRoleScheme.OWNER)));

    ResourceAccessException removalError =
        assertThrows(
            ResourceAccessException.class, () -> manager.replace(resource, removed, owner, owner));

    assertEquals(ResourceAccessException.Reason.INVALID_GRANTEE, removalError.reason());

    ReplaceResourceAccess<Long> viewer =
        new ReplaceResourceAccess<>(
            9L,
            7L,
            List.of(
                new ResourceAccessGrant("user:12", BookingResourceRoleScheme.OWNER),
                new ResourceAccessGrant("audience:all-users", BookingResourceRoleScheme.VIEWER)));
    ResourceAccessException roleError =
        assertThrows(
            ResourceAccessException.class, () -> manager.replace(resource, viewer, owner, owner));

    assertEquals(ResourceAccessException.Reason.INVALID_GRANTEE, roleError.reason());
    verify(gateway, never()).flush();
  }

  @Test
  void allUsersCanToggleToNoAccessWithoutDenyingDirectAssignments() {
    when(gateway.resolveAvailable(
            "audience:all-users", BookingResourceRoleScheme.NO_ACCESS, "All users"))
        .thenReturn(
            ResourceRoleAssignment.forAudience(
                BookingResourceRoleScheme.NO_ACCESS, ResourceAudience.ALL_USERS, "All users"));
    ReplaceResourceAccess<Long> command =
        new ReplaceResourceAccess<>(
            9L,
            7L,
            List.of(
                new ResourceAccessGrant("user:12", BookingResourceRoleScheme.OWNER),
                new ResourceAccessGrant(
                    "audience:all-users", BookingResourceRoleScheme.NO_ACCESS)));

    ResourceAccessDocument document = manager.replace(resource, command, owner, owner);

    assertEquals(BookingResourceRoleScheme.OWNER, document.caller().effectiveRole().orElseThrow());
    assertEquals(
        BookingResourceRoleScheme.NO_ACCESS,
        access.getAssignments().stream()
            .filter(assignment -> assignment.getGranteeKey().equals("audience:all-users"))
            .findFirst()
            .orElseThrow()
            .getRoleKey());
  }

  @Test
  void managerCannotChangeOwnerAssignments() {
    User managerUser = user(14L, "Manager");
    access.addAssignment(
        ResourceRoleAssignment.forUser(BookingResourceRoleScheme.MANAGER, managerUser));
    when(gateway.lockAuthorizationFacts(access, managerUser)).thenReturn(managerUser);
    ResourceRoleAssignment otherOwner =
        ResourceRoleAssignment.forUser(BookingResourceRoleScheme.OWNER, other);
    when(gateway.resolveAvailable("user:13", BookingResourceRoleScheme.OWNER))
        .thenReturn(otherOwner);
    ResourceGranteeDirectoryEntry assignableOther = directoryEntry(other);
    when(directory.resolveAssignable(Set.of("user:13"), managerUser))
        .thenReturn(Map.of("user:13", assignableOther));
    ReplaceResourceAccess<Long> command =
        new ReplaceResourceAccess<>(
            9L,
            7L,
            grants(
                new ResourceAccessGrant("user:13", BookingResourceRoleScheme.OWNER),
                new ResourceAccessGrant("user:14", BookingResourceRoleScheme.MANAGER)));

    ResourceAccessException error =
        assertThrows(
            ResourceAccessException.class,
            () -> manager.replace(resource, command, managerUser, managerUser));

    assertEquals(ResourceAccessException.Reason.FORBIDDEN, error.reason());
    verify(gateway, never()).flush();
  }

  @Test
  void replacementCannotRemoveLastOwner() {
    ReplaceResourceAccess<Long> command =
        new ReplaceResourceAccess<>(
            9L, 7L, grants(new ResourceAccessGrant("user:12", BookingResourceRoleScheme.BOOKER)));
    ResourceRoleAssignment ownerBooker =
        ResourceRoleAssignment.forUser(BookingResourceRoleScheme.BOOKER, owner);
    when(gateway.resolveAvailable("user:12", BookingResourceRoleScheme.BOOKER))
        .thenReturn(ownerBooker);
    ResourceGranteeDirectoryEntry assignableOwner = directoryEntry(owner);
    when(directory.resolveAssignable(Set.of("user:12"), owner))
        .thenReturn(Map.of("user:12", assignableOwner));

    ResourceAccessException error =
        assertThrows(
            ResourceAccessException.class, () -> manager.replace(resource, command, owner, owner));

    assertEquals(ResourceAccessException.Reason.OWNER_REQUIRED, error.reason());
  }

  @Test
  void replacementRejectsAnOutOfDirectoryKeyBeforeResolvingOrWriting() {
    when(directory.resolveAssignable(Set.of("user:13"), owner)).thenReturn(Map.of());
    ReplaceResourceAccess<Long> command =
        new ReplaceResourceAccess<>(
            9L,
            7L,
            grants(
                new ResourceAccessGrant("user:12", BookingResourceRoleScheme.OWNER),
                new ResourceAccessGrant("user:13", BookingResourceRoleScheme.BOOKER)));

    ResourceAccessException error =
        assertThrows(
            ResourceAccessException.class, () -> manager.replace(resource, command, owner, owner));

    assertEquals(ResourceAccessException.Reason.FORBIDDEN, error.reason());
    assertEquals(7L, access.getVersion());
    verify(gateway, never()).resolveAvailable("user:13", BookingResourceRoleScheme.BOOKER);
    verify(gateway, never()).flush();
  }

  @Test
  void replacementCannotRemoveCallersDirectRow() {
    ReplaceResourceAccess<Long> command = new ReplaceResourceAccess<>(9L, 7L, grants());

    ResourceAccessException error =
        assertThrows(
            ResourceAccessException.class, () -> manager.replace(resource, command, owner, owner));

    assertEquals(ResourceAccessException.Reason.SELF_REMOVAL_REQUIRES_LEAVE, error.reason());
  }

  @Test
  void ownershipTransferPromotesIncomingUserAndRemovesOutgoingOwner() {
    ResourceRoleAssignment incomingOwner =
        ResourceRoleAssignment.forUser(BookingResourceRoleScheme.OWNER, other);
    when(gateway.resolveAvailable("user:13", BookingResourceRoleScheme.OWNER))
        .thenReturn(incomingOwner);

    manager.transferDirectOwnership(resource, 9L, owner, other, owner, owner);

    assertEquals(
        Map.of(
            "user:13", BookingResourceRoleScheme.OWNER,
            "audience:all-users", BookingResourceRoleScheme.BOOKER),
        access.getAssignments().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    ResourceRoleAssignment::getGranteeKey, ResourceRoleAssignment::getRoleKey)));
    verify(gateway, times(2)).flush();
  }

  @Test
  void ownershipTransferIsForbiddenWhenTheResourceFeatureIsDisabled() {
    when(resource.featureEnabled(owner)).thenReturn(false);

    ResourceAccessException error =
        assertThrows(
            ResourceAccessException.class,
            () -> manager.transferDirectOwnership(resource, 9L, owner, other, owner, owner));

    assertEquals(ResourceAccessException.Reason.FORBIDDEN, error.reason());
    verify(resource, never()).lock(9L);
    verify(gateway, never()).flush();
  }

  @Test
  void ownershipTransferPreservesOutgoingLowerRoleAndUnrelatedAssignments() {
    User existingOwner = user(14L, "Existing owner");
    ResourceAccess accessWithLowerOutgoing =
        new ResourceAccess(scheme.key(), owner, new Date(1_000));
    accessWithLowerOutgoing.setId(6L);
    accessWithLowerOutgoing.addAssignment(
        ResourceRoleAssignment.forUser(BookingResourceRoleScheme.OWNER, existingOwner));
    accessWithLowerOutgoing.addAssignment(
        ResourceRoleAssignment.forUser(BookingResourceRoleScheme.BOOKER, owner));
    accessWithLowerOutgoing.addAssignment(
        ResourceRoleAssignment.forAudience(
            BookingResourceRoleScheme.BOOKER, ResourceAudience.ALL_USERS, "All users"));
    when(resource.access(protectedEntity)).thenReturn(accessWithLowerOutgoing);
    when(gateway.lockAuthorizationFacts(accessWithLowerOutgoing, existingOwner))
        .thenReturn(existingOwner);
    ResourceRoleAssignment incomingOwner =
        ResourceRoleAssignment.forUser(BookingResourceRoleScheme.OWNER, other);
    when(gateway.resolveAvailable("user:13", BookingResourceRoleScheme.OWNER))
        .thenReturn(incomingOwner);

    manager.transferDirectOwnership(resource, 9L, owner, other, existingOwner, existingOwner);

    assertEquals(
        Map.of(
            "user:12", BookingResourceRoleScheme.BOOKER,
            "user:13", BookingResourceRoleScheme.OWNER,
            "user:14", BookingResourceRoleScheme.OWNER,
            "audience:all-users", BookingResourceRoleScheme.BOOKER),
        accessWithLowerOutgoing.getAssignments().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    ResourceRoleAssignment::getGranteeKey, ResourceRoleAssignment::getRoleKey)));
  }

  @Test
  void staleVersionIsCheckedAfterLockedAuthorization() {
    ReplaceResourceAccess<Long> command =
        new ReplaceResourceAccess<>(
            9L, 6L, grants(new ResourceAccessGrant("user:12", BookingResourceRoleScheme.OWNER)));

    ResourceAccessException error =
        assertThrows(
            ResourceAccessException.class, () -> manager.replace(resource, command, owner, owner));

    assertEquals(ResourceAccessException.Reason.STALE, error.reason());
    verify(gateway).lockAuthorizationFacts(access, owner);
  }

  @Test
  void accessDocumentExplainsAssignedUsersOnlyFromAssignmentsInThisAggregate() {
    Group assignedGroup = group(41L, "Imaging");
    access.addAssignment(
        ResourceRoleAssignment.forGroup(BookingResourceRoleScheme.MANAGER, assignedGroup));
    access.addAssignment(ResourceRoleAssignment.forUser(BookingResourceRoleScheme.VIEWER, other));
    when(other.hasSysadminRole()).thenReturn(true);
    when(gateway.assignedUserGroupIds(access)).thenReturn(Map.of(13L, Set.of(41L)));

    ResourceAccessDocument document = manager.get(resource, 9L, owner);

    ResourceAccessGranteeDocument otherDocument =
        document.assignments().stream()
            .map(ResourceAccessAssignmentDocument::grantee)
            .filter(grantee -> grantee.key().equals("user:13"))
            .findFirst()
            .orElseThrow();
    assertEquals(Optional.of(BookingResourceRoleScheme.MANAGER), otherDocument.effectiveRole());
    assertEquals(
        Set.of(
            ResourceRoleSourceKind.DIRECT,
            ResourceRoleSourceKind.GROUP,
            ResourceRoleSourceKind.AUDIENCE),
        otherDocument.roleSources().stream()
            .map(ResourceRoleSource::kind)
            .collect(java.util.stream.Collectors.toSet()));
    assertFalse(
        otherDocument.roleSources().stream()
            .anyMatch(source -> source.kind() == ResourceRoleSourceKind.IMPLICIT));
    assertFalse(document.caller().capabilities().canLeave());
    assertTrue(document.caller().capabilities().canManageAssignments());
  }

  @Test
  void removeSelfIsIdempotentForAReadableCallerWithoutADirectAssignment() {
    when(gateway.lockAuthorizationFacts(access, other)).thenReturn(other);

    manager.removeSelf(resource, new RemoveSelfResourceAccess<>(9L), other, other);

    verify(gateway, never()).flush();
  }

  @Test
  void finalOwnerCannotLeave() {
    ResourceAccessException error =
        assertThrows(
            ResourceAccessException.class,
            () -> manager.removeSelf(resource, new RemoveSelfResourceAccess<>(9L), owner, owner));

    assertEquals(ResourceAccessException.Reason.OWNER_REQUIRED, error.reason());
    verify(gateway, never()).flush();
  }

  private static User user(long id, String name) {
    User user = mock(User.class);
    when(user.getId()).thenReturn(id);
    when(user.isEnabled()).thenReturn(true);
    when(user.getDisplayName()).thenReturn(name);
    when(user.getUsername()).thenReturn(name.toLowerCase());
    when(user.getGroups()).thenReturn(Set.of());
    return user;
  }

  private static List<ResourceAccessGrant> grants(ResourceAccessGrant... named) {
    java.util.ArrayList<ResourceAccessGrant> grants =
        new java.util.ArrayList<>(java.util.Arrays.asList(named));
    grants.add(new ResourceAccessGrant("audience:all-users", BookingResourceRoleScheme.BOOKER));
    return List.copyOf(grants);
  }

  private static Group group(long id, String name) {
    Group group = mock(Group.class);
    when(group.getId()).thenReturn(id);
    when(group.getDisplayName()).thenReturn(name);
    when(group.getUniqueName()).thenReturn(name.toLowerCase());
    when(group.getEnabledMemberSize()).thenReturn(1);
    return group;
  }

  private static ResourceGranteeDirectoryEntry directoryEntry(User user) {
    return new ResourceGranteeDirectoryEntry(
        com.researchspace.model.resourceaccess.ResourceGranteeKind.USER,
        user.getId(),
        "user:" + user.getId(),
        user.getDisplayName(),
        user.getUsername());
  }
}
