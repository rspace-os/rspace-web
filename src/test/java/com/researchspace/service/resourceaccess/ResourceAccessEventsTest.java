package com.researchspace.service.resourceaccess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.dao.resourceaccess.ResourceAccessDao;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.resourceaccess.ResourceAccess;
import com.researchspace.model.resourceaccess.ResourceAudience;
import com.researchspace.model.resourceaccess.ResourceGranteeKind;
import com.researchspace.model.resourceaccess.ResourceRoleAssignment;
import com.researchspace.service.MessageSourceUtils;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ResourceAccessEventsTest {
  @ParameterizedTest
  @ValueSource(strings = {"add", "remove", "change", "on", "off", "transfer", "leave"})
  void publishesExactDeltas(String operation) {
    User owner = user(12L, "owner");
    User other = user(13L, "other");
    var scheme = new ResourceAccessTestScheme();
    var registry = new ResourceRoleSchemeRegistry(List.of(scheme));
    var dao = mock(ResourceAccessDao.class);
    var directory = mock(ResourceAccessDirectoryPolicy.class);
    var messages = mock(MessageSourceUtils.class);
    var events = mock(ApplicationEventPublisher.class);
    var now = Instant.parse("2026-08-31T08:00:00Z");
    var manager =
        new ResourceAccessManagerImpl(
            dao,
            registry,
            new ResourceAccessResolver(registry),
            directory,
            Clock.fixed(now, ZoneOffset.UTC),
            messages,
            events);
    var access = new ResourceAccess(scheme.key(), owner, new Date());
    access.setId(5L);
    access.addAssignment(ResourceRoleAssignment.forUser("OWNER", owner));
    boolean audience = operation.equals("on") || operation.equals("off");
    String key = audience ? "audience:all-users" : "user:13";
    String oldRole =
        switch (operation) {
          case "add" -> null;
          case "on" -> "NO_ACCESS";
          case "transfer", "leave" -> "OWNER";
          default -> "EDITOR";
        };
    String newRole =
        switch (operation) {
          case "remove", "leave" -> null;
          case "change" -> "VIEWER";
          case "off" -> "NO_ACCESS";
          case "transfer" -> "OWNER";
          default -> "EDITOR";
        };
    access.addAssignment(
        ResourceRoleAssignment.forAudience(
            audience ? oldRole : "EDITOR", ResourceAudience.ALL_USERS, "All users"));
    if (oldRole != null && !audience && !operation.equals("transfer")) {
      access.addAssignment(ResourceRoleAssignment.forUser(oldRole, other));
    }
    var facts = new ResourceAccessDao.AuthorizationFacts(owner, true, Set.of(), Set.of());
    when(dao.lockAuthorizationFacts(access, owner)).thenReturn(facts);
    ProtectedResourceAccess<ResourceAccess, Long> resource =
        new ProtectedResourceAccess<>() {
          public Optional<ResourceAccess> find(Long id) {
            return Optional.of(access);
          }

          public Optional<ResourceAccess> lock(Long id) {
            return find(id);
          }

          public ResourceAccess access(ResourceAccess value) {
            return value;
          }

          public String viewAccessCapability() {
            return "READ_RESOURCE";
          }

          public String manageAssignmentsCapability() {
            return "MANAGE_ASSIGNMENTS";
          }

          public String manageOwnersCapability() {
            return "MANAGE_OWNERS";
          }
        };
    ResourceAccessAuditReason reason;
    if (operation.equals("leave")) {
      manager.removeSelf(resource, new RemoveSelfResourceAccess<>(5L), owner, other);
      assertFalse(
          access.getAssignments().stream().anyMatch(row -> row.getGranteeKey().equals("user:12")));
      verify(dao).flush();
      key = "user:12";
      reason = ResourceAccessAuditReason.DIRECT_LEAVE;
    } else if (operation.equals("transfer")) {
      when(dao.resolveAvailable("user:13", "OWNER", null))
          .thenReturn(ResourceRoleAssignment.forUser("OWNER", other));
      manager.transferDirectOwnership(resource, 5L, owner, other, owner, other);
      reason = ResourceAccessAuditReason.OWNERSHIP_TRANSFER;
    } else {
      List<ResourceAccessGrant> grants = new ArrayList<>();
      grants.add(new ResourceAccessGrant("user:12", "OWNER"));
      if (!audience) grants.add(new ResourceAccessGrant("audience:all-users", "EDITOR"));
      if (newRole != null) {
        grants.add(new ResourceAccessGrant(key, newRole));
        if (audience) {
          when(messages.getMessage(ResourceAudience.ALL_USERS.messageKey()))
              .thenReturn("All users");
        } else {
          when(directory.resolveAssignable(Set.of(key), facts))
              .thenReturn(
                  Map.of(
                      key,
                      new ResourceGranteeDirectoryEntry(
                          ResourceGranteeKind.USER,
                          13L,
                          key,
                          other.getDisplayName(),
                          other.getUsername())));
        }
        when(dao.resolveAvailable(key, newRole, audience ? "All users" : null))
            .thenReturn(
                audience
                    ? ResourceRoleAssignment.forAudience(
                        newRole, ResourceAudience.ALL_USERS, "All users")
                    : ResourceRoleAssignment.forUser(newRole, other));
      }
      manager.replace(resource, new ReplaceResourceAccess<>(5L, 0L, grants), owner, other);
      reason =
          switch (operation) {
            case "add" -> ResourceAccessAuditReason.ASSIGNMENT_ADD;
            case "remove" -> ResourceAccessAuditReason.ASSIGNMENT_REMOVE;
            case "change" -> ResourceAccessAuditReason.ASSIGNMENT_CHANGE;
            case "on" -> ResourceAccessAuditReason.ALL_USERS_ON;
            default -> ResourceAccessAuditReason.ALL_USERS_OFF;
          };
    }
    var captured = ArgumentCaptor.forClass(ResourceAccessAuditDelta.class);
    verify(events, times(operation.equals("transfer") ? 2 : 1)).publishEvent(captured.capture());
    if (operation.equals("transfer")) {
      assertEquals(
          new ResourceAccessAuditDelta(
              access,
              other,
              owner,
              AuditAction.UNSHARE,
              "user:12",
              owner.getDisplayName(),
              "OWNER",
              null,
              now,
              reason),
          captured.getAllValues().get(0));
      oldRole = null;
    }
    AuditAction action =
        oldRole == null
            ? AuditAction.SHARE
            : newRole == null ? AuditAction.UNSHARE : AuditAction.WRITE;
    String name =
        audience
            ? "All users"
            : operation.equals("leave") ? owner.getDisplayName() : other.getDisplayName();
    assertEquals(
        new ResourceAccessAuditDelta(
            access, other, owner, action, key, name, oldRole, newRole, now, reason),
        captured.getValue());
  }

  private User user(Long id, String name) {
    User user = new User();
    user.setId(id);
    user.setUsername(name);
    user.setFirstName(name);
    user.setLastName("User");
    user.setEnabled(true);
    return user;
  }
}
