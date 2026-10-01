package com.researchspace.service.resourceaccess;

import com.researchspace.dao.resourceaccess.ResourceAccessDao;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.resourceaccess.ResourceAccess;
import com.researchspace.model.resourceaccess.ResourceAudience;
import com.researchspace.model.resourceaccess.ResourceGranteeKeys;
import com.researchspace.model.resourceaccess.ResourceRoleAssignment;
import com.researchspace.service.MessageSourceUtils;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default transactional implementation of the generic resource-access module. */
@Service
@Transactional
public class ResourceAccessManagerImpl implements ResourceAccessManager {

  private static final Logger log = LoggerFactory.getLogger(ResourceAccessManagerImpl.class);
  private static final int RECORD_CHANGED_SINCE_READ = 1020;

  private final ResourceAccessDao gateway;
  private final ResourceRoleSchemeRegistry schemes;
  private final ResourceAccessResolver resolver;
  private final ResourceAccessDirectoryPolicy directory;
  private final Clock clock;
  private final ApplicationEventPublisher events;
  private final MessageSourceUtils messages;

  @Autowired
  public ResourceAccessManagerImpl(
      ResourceAccessDao gateway,
      ResourceRoleSchemeRegistry schemes,
      ResourceAccessResolver resolver,
      ResourceAccessDirectoryPolicy directory,
      MessageSourceUtils messages,
      ApplicationEventPublisher events) {
    this(gateway, schemes, resolver, directory, Clock.systemUTC(), messages, events);
  }

  public ResourceAccessManagerImpl(
      ResourceAccessDao gateway,
      ResourceRoleSchemeRegistry schemes,
      ResourceAccessResolver resolver,
      ResourceAccessDirectoryPolicy directory,
      Clock clock,
      MessageSourceUtils messages) {
    this(gateway, schemes, resolver, directory, clock, messages, event -> {});
  }

  public ResourceAccessManagerImpl(
      ResourceAccessDao gateway,
      ResourceRoleSchemeRegistry schemes,
      ResourceAccessResolver resolver,
      ResourceAccessDirectoryPolicy directory,
      Clock clock,
      MessageSourceUtils messages,
      ApplicationEventPublisher events) {
    this.gateway = gateway;
    this.schemes = schemes;
    this.resolver = resolver;
    this.directory = directory;
    this.clock = clock;
    this.messages = messages;
    this.events = events;
  }

  @Override
  public ResolvedResourceAccess resolve(ResourceAccess access, User subject) {
    return resolver.resolve(access, subject);
  }

  @Override
  public ResolvedResourceAccess resolveForMutation(ResourceAccess access, User subject) {
    ResourceAccessDao.AuthorizationFacts authorizationFacts =
        currentAuthorizationRead(() -> gateway.lockAuthorizationFacts(access, subject));
    return resolveLocked(access, authorizationFacts);
  }

  @Override
  public Map<Long, ResolvedResourceAccess> resolveAll(
      Collection<ResourceAccess> accesses, User subject) {
    if (accesses.stream().anyMatch(access -> access.getId() == null)) {
      throw new IllegalArgumentException("Persisted resource access id is required");
    }
    List<Long> accessIds = accesses.stream().map(ResourceAccess::getId).distinct().toList();
    gateway.loadAssignments(accessIds);
    Map<Long, ResolvedResourceAccess> result = new LinkedHashMap<>();
    for (ResourceAccess access : accesses) {
      result.put(access.getId(), resolver.resolve(access, subject));
    }
    return Map.copyOf(result);
  }

  @Override
  public boolean canLeave(ResourceAccess access, User subject) {
    if (subject == null || subject.getId() == null) {
      return false;
    }
    String subjectKey = ResourceGranteeKeys.user(subject.getId());
    boolean direct =
        access.getAssignments().stream()
            .anyMatch(assignment -> assignment.getGranteeKey().equals(subjectKey));
    return direct && removalPreservesRequiredRoles(access, subject);
  }

  @Override
  public <T, ID> ResourceAccessDocument get(
      ProtectedResourceAccess<T, ID> resource, ID id, User subject) {
    if (!resource.featureEnabled(subject)) {
      throw failure(ResourceAccessException.Reason.NOT_FOUND);
    }
    T protectedEntity =
        resource.find(id).orElseThrow(() -> failure(ResourceAccessException.Reason.NOT_FOUND));
    ResourceAccess access = resource.access(protectedEntity);
    ResolvedResourceAccess resolved = resolver.resolve(access, subject);
    requireRead(resolved);
    requireCapability(resolved, resource.viewAccessCapability());
    return document(resource, access, subject, resolved);
  }

  @Override
  public <T, ID> ResourceAccessDocument replace(
      ProtectedResourceAccess<T, ID> resource,
      ReplaceResourceAccess<ID> command,
      User subject,
      User actor) {
    if (!resource.featureEnabled(subject)) {
      throw failure(ResourceAccessException.Reason.FORBIDDEN);
    }
    Locked<T> locked = lockAndAuthorize(resource, command.resourceId(), subject);
    requireCapability(locked.resolved(), resource.manageAssignmentsCapability());
    resource.beforeAccessMutation(locked.resource());
    if (locked.access().getVersion() != command.expectedVersion()) {
      throw failure(ResourceAccessException.Reason.STALE);
    }

    ResourceRoleScheme scheme = schemes.getRequired(locked.access().getSchemeKey());
    Map<String, ResourceRoleAssignment> before = assignmentsByKey(locked.access());
    validateAssignableChanges(command.assignments(), before, locked.authorizationFacts());
    Map<String, ResourceRoleAssignment> after = normalize(command.assignments(), before, scheme);
    requireNamedAssignmentLimit(after.values());
    requireFixedAudiences(after, scheme);
    String subjectKey = ResourceGranteeKeys.user(locked.subject().getId());
    if (before.containsKey(subjectKey) && !after.containsKey(subjectKey)) {
      throw failure(ResourceAccessException.Reason.SELF_REMOVAL_REQUIRES_LEAVE);
    }
    validateOwnerMutation(resource, locked.resolved(), before, after);
    requirePersistedRoles(after.values(), scheme);
    if (semanticRoles(before).equals(semanticRoles(after))) {
      return document(resource, locked.access(), locked.subject(), locked.resolved());
    }

    applyReplacement(locked.access(), before, after);
    Instant changedAt = clock.instant();
    locked.access().touch(actor, Date.from(changedAt));
    gateway.flush();
    publishDeltas(
        locked.resource(), actor, locked.subject(), before, after, scheme, changedAt, null);
    return document(
        resource,
        locked.access(),
        locked.subject(),
        resolveLocked(locked.access(), locked.authorizationFacts()));
  }

  @Override
  public <T, ID> void transferDirectOwnership(
      ProtectedResourceAccess<T, ID> resource,
      ID id,
      User outgoingOwner,
      User incomingOwner,
      User subject,
      User actor) {
    Objects.requireNonNull(outgoingOwner, "outgoingOwner");
    Objects.requireNonNull(incomingOwner, "incomingOwner");
    if (!resource.featureEnabled(subject)) {
      throw failure(ResourceAccessException.Reason.FORBIDDEN);
    }
    Locked<T> locked = lockAndAuthorize(resource, id, subject);
    requireCapability(locked.resolved(), resource.manageOwnersCapability());
    resource.beforeAccessMutation(locked.resource());
    if (outgoingOwner.getId().equals(incomingOwner.getId())) {
      return;
    }

    ResourceRoleScheme scheme = schemes.getRequired(locked.access().getSchemeKey());
    Map<String, ResourceRoleAssignment> before = assignmentsByKey(locked.access());
    Map<String, ResourceRoleAssignment> after = new LinkedHashMap<>(before);
    String outgoingKey = ResourceGranteeKeys.user(outgoingOwner.getId());
    String incomingKey = ResourceGranteeKeys.user(incomingOwner.getId());
    ResourceRoleAssignment outgoing = after.get(outgoingKey);
    if (outgoing != null && ResourceRoleScheme.OWNER_ROLE.equals(outgoing.getRoleKey())) {
      after.remove(outgoingKey);
    }
    ResourceRoleAssignment incoming = after.get(incomingKey);
    if (incoming == null || !ResourceRoleScheme.OWNER_ROLE.equals(incoming.getRoleKey())) {
      incoming = resolveAvailable(incomingKey, ResourceRoleScheme.OWNER_ROLE);
      if (incoming == null
          || !scheme
              .allowedGranteeKinds(ResourceRoleScheme.OWNER_ROLE)
              .contains(incoming.getGranteeKind())) {
        throw failure(ResourceAccessException.Reason.INVALID_GRANTEE);
      }
      after.put(incomingKey, incoming);
    }
    requireNamedAssignmentLimit(after.values());
    requirePersistedRoles(after.values(), scheme);
    requireFixedAudiences(after, scheme);
    if (semanticRoles(before).equals(semanticRoles(after))) {
      return;
    }

    applyReplacement(locked.access(), before, after);
    Instant changedAt = clock.instant();
    locked.access().touch(actor, Date.from(changedAt));
    gateway.flush();
    publishDeltas(
        locked.resource(),
        actor,
        locked.subject(),
        before,
        after,
        scheme,
        changedAt,
        ResourceAccessAuditReason.OWNERSHIP_TRANSFER);
  }

  @Override
  public <T, ID> void removeSelf(
      ProtectedResourceAccess<T, ID> resource,
      RemoveSelfResourceAccess<ID> command,
      User subject,
      User actor) {
    if (!resource.featureEnabled(subject)) {
      throw failure(ResourceAccessException.Reason.FORBIDDEN);
    }
    T protectedEntity =
        resource
            .lock(command.resourceId())
            .orElseThrow(() -> failure(ResourceAccessException.Reason.NOT_FOUND));
    ResourceAccess access = resource.access(protectedEntity);
    ResourceAccessDao.AuthorizationFacts authorizationFacts =
        currentAuthorizationRead(() -> gateway.lockAuthorizationFacts(access, subject));
    User lockedSubject = authorizationFacts.subject();
    if (!authorizationFacts.enabled()) {
      throw failure(ResourceAccessException.Reason.FORBIDDEN);
    }
    ResolvedResourceAccess resolved = resolveLocked(access, authorizationFacts);
    String subjectKey = ResourceGranteeKeys.user(lockedSubject.getId());
    ResourceRoleAssignment direct = assignmentsByKey(access).get(subjectKey);
    if (direct == null) {
      requireRead(resolved);
      return;
    }
    resource.beforeAccessMutation(protectedEntity);
    Map<String, ResourceRoleAssignment> remaining = assignmentsByKey(access);
    remaining.remove(subjectKey);
    requirePersistedRoles(remaining.values(), schemes.getRequired(access.getSchemeKey()));
    access.removeAssignment(direct);
    Instant changedAt = clock.instant();
    access.touch(actor, Date.from(changedAt));
    gateway.flush();
    events.publishEvent(
        new ResourceAccessAuditDelta(
            protectedEntity,
            actor,
            lockedSubject,
            AuditAction.UNSHARE,
            direct.getGranteeKey(),
            direct.getNameSnapshot(),
            direct.getRoleKey(),
            null,
            changedAt,
            ResourceAccessAuditReason.DIRECT_LEAVE));
    events.publishEvent(new ResourceAccessChangedEvent(auditTarget(resource, protectedEntity)));
  }

  private void publishDeltas(
      Object auditTarget,
      User actor,
      User subject,
      Map<String, ResourceRoleAssignment> before,
      Map<String, ResourceRoleAssignment> after,
      ResourceRoleScheme scheme,
      Instant changedAt,
      ResourceAccessAuditReason reasonOverride) {
    Set<String> keys = new LinkedHashSet<>(before.keySet());
    keys.addAll(after.keySet());
    for (String key : keys) {
      ResourceRoleAssignment oldAssignment = before.get(key);
      ResourceRoleAssignment newAssignment = after.get(key);
      String oldRole = oldAssignment == null ? null : oldAssignment.getRoleKey();
      String newRole = newAssignment == null ? null : newAssignment.getRoleKey();
      if (Objects.equals(oldRole, newRole)) {
        continue;
      }
      ResourceRoleAssignment snapshot = newAssignment == null ? oldAssignment : newAssignment;
      boolean audience = ResourceGranteeKeys.audience(ResourceAudience.ALL_USERS).equals(key);
      ResourceAccessAuditReason reason = reasonOverride;
      if (reason == null) {
        if (audience && oldAssignment != null && newAssignment != null) {
          reason =
              scheme.capabilities(newRole).contains(ResourceRoleScheme.READ_RESOURCE_CAPABILITY)
                  ? ResourceAccessAuditReason.ALL_USERS_ON
                  : ResourceAccessAuditReason.ALL_USERS_OFF;
        } else if (oldAssignment == null) {
          reason =
              audience
                  ? ResourceAccessAuditReason.ALL_USERS_ON
                  : ResourceAccessAuditReason.ASSIGNMENT_ADD;
        } else if (newAssignment == null) {
          reason =
              audience
                  ? ResourceAccessAuditReason.ALL_USERS_OFF
                  : ResourceAccessAuditReason.ASSIGNMENT_REMOVE;
        } else {
          reason = ResourceAccessAuditReason.ASSIGNMENT_CHANGE;
        }
      }
      AuditAction action =
          oldAssignment == null
              ? AuditAction.SHARE
              : newAssignment == null ? AuditAction.UNSHARE : AuditAction.WRITE;
      events.publishEvent(
          new ResourceAccessAuditDelta(
              auditTarget,
              actor,
              subject,
              action,
              key,
              snapshot.getNameSnapshot(),
              oldRole,
              newRole,
              changedAt,
              reason));
    }
    events.publishEvent(new ResourceAccessChangedEvent(auditTarget));
  }

  private <T, ID> Locked<T> lockAndAuthorize(
      ProtectedResourceAccess<T, ID> resource, ID id, User subject) {
    T protectedEntity =
        resource.lock(id).orElseThrow(() -> failure(ResourceAccessException.Reason.NOT_FOUND));
    ResourceAccess access = resource.access(protectedEntity);
    ResourceAccessDao.AuthorizationFacts authorizationFacts =
        currentAuthorizationRead(() -> gateway.lockAuthorizationFacts(access, subject));
    User lockedSubject = authorizationFacts.subject();
    ResolvedResourceAccess resolved = resolveLocked(access, authorizationFacts);
    requireRead(resolved);
    return new Locked<>(protectedEntity, access, authorizationFacts, resolved);
  }

  private ResolvedResourceAccess resolveLocked(
      ResourceAccess access, ResourceAccessDao.AuthorizationFacts authorizationFacts) {
    return resolver.resolveWithFacts(
        access,
        authorizationFacts.subject(),
        authorizationFacts.enabled(),
        authorizationFacts.currentGroupIds(),
        authorizationFacts.globalRoleNames());
  }

  private Map<String, ResourceRoleAssignment> normalize(
      List<ResourceAccessGrant> requested,
      Map<String, ResourceRoleAssignment> existing,
      ResourceRoleScheme scheme) {
    Map<String, ResourceRoleAssignment> normalized = new LinkedHashMap<>();
    Set<String> roles = scheme.roles().stream().map(ResourceRole::key).collect(Collectors.toSet());
    for (ResourceAccessGrant grant : requested) {
      if (!roles.contains(grant.role())) {
        throw failure(ResourceAccessException.Reason.INVALID_ROLE);
      }
      if (normalized.containsKey(grant.granteeKey())) {
        throw failure(ResourceAccessException.Reason.DUPLICATE_GRANTEE);
      }
      ResourceRoleAssignment current = existing.get(grant.granteeKey());
      ResourceRoleAssignment assignment =
          current != null && current.getRoleKey().equals(grant.role())
              ? current
              : resolveAvailable(grant.granteeKey(), grant.role());
      if (assignment == null
          || !assignment.getGranteeKey().equals(grant.granteeKey())
          || !scheme.allowedGranteeKinds(grant.role()).contains(assignment.getGranteeKind())) {
        throw failure(ResourceAccessException.Reason.INVALID_GRANTEE);
      }
      normalized.put(grant.granteeKey(), assignment);
    }
    return normalized;
  }

  private void validateAssignableChanges(
      List<ResourceAccessGrant> requested,
      Map<String, ResourceRoleAssignment> existing,
      ResourceAccessDao.AuthorizationFacts authorizationFacts) {
    String audienceKey = ResourceGranteeKeys.audience(ResourceAudience.ALL_USERS);
    Set<String> keys =
        requested.stream()
            .filter(
                grant -> {
                  ResourceRoleAssignment current = existing.get(grant.granteeKey());
                  return current == null || !current.getRoleKey().equals(grant.role());
                })
            .map(ResourceAccessGrant::granteeKey)
            .filter(key -> !audienceKey.equals(key))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    Map<String, ResourceGranteeDirectoryEntry> assignable =
        currentAuthorizationRead(() -> directory.resolveAssignable(keys, authorizationFacts));
    if (!assignable.keySet().containsAll(keys)) {
      throw failure(ResourceAccessException.Reason.FORBIDDEN);
    }
  }

  private static <T, ID> void validateOwnerMutation(
      ProtectedResourceAccess<T, ID> resource,
      ResolvedResourceAccess caller,
      Map<String, ResourceRoleAssignment> before,
      Map<String, ResourceRoleAssignment> after) {
    if (caller.hasCapability(resource.manageOwnersCapability())) {
      return;
    }
    if (!ownerRoles(before).equals(ownerRoles(after))) {
      throw failure(ResourceAccessException.Reason.FORBIDDEN);
    }
  }

  private String audienceNameSnapshot() {
    return messages.getMessage(ResourceAudience.ALL_USERS.messageKey());
  }

  private ResourceRoleAssignment resolveAvailable(String granteeKey, String role) {
    return currentAuthorizationRead(
        () ->
            ResourceGranteeKeys.audience(ResourceAudience.ALL_USERS).equals(granteeKey)
                ? gateway.resolveAvailable(granteeKey, role, audienceNameSnapshot())
                : gateway.resolveAvailable(granteeKey, role, null));
  }

  private static <T> T currentAuthorizationRead(Supplier<T> read) {
    try {
      return read.get();
    } catch (DataAccessException exception) {
      if (recordChangedSinceRead(exception)) {
        log.warn(
            "Concurrent principal facts changed during a resource-access authorization read",
            exception);
        throw failure(ResourceAccessException.Reason.STALE);
      }
      throw exception;
    }
  }

  private static boolean recordChangedSinceRead(Throwable exception) {
    Throwable current = exception;
    while (current != null) {
      if (current instanceof SQLException sqlException
          && sqlException.getErrorCode() == RECORD_CHANGED_SINCE_READ) {
        return true;
      }
      Throwable cause = current.getCause();
      current = cause == current ? null : cause;
    }
    return false;
  }

  private static void requirePersistedRoles(
      Collection<ResourceRoleAssignment> assignments, ResourceRoleScheme scheme) {
    Set<String> present =
        assignments.stream().map(ResourceRoleAssignment::getRoleKey).collect(Collectors.toSet());
    if (!present.containsAll(scheme.requiredPersistedRoles())) {
      throw failure(ResourceAccessException.Reason.OWNER_REQUIRED);
    }
  }

  private static void requireFixedAudiences(
      Map<String, ResourceRoleAssignment> assignments, ResourceRoleScheme scheme) {
    scheme
        .fixedAudienceRoles()
        .forEach(
            (audience, allowedRoles) -> {
              ResourceRoleAssignment assignment =
                  assignments.get(ResourceGranteeKeys.audience(audience));
              if (assignment == null
                  || assignment.getAudienceKey() != audience
                  || !allowedRoles.contains(assignment.getRoleKey())) {
                throw failure(ResourceAccessException.Reason.INVALID_GRANTEE);
              }
            });
  }

  private static void requireNamedAssignmentLimit(Collection<ResourceRoleAssignment> assignments) {
    long count =
        assignments.stream()
            .filter(
                assignment ->
                    assignment.getGranteeKind()
                        != com.researchspace.model.resourceaccess.ResourceGranteeKind.AUDIENCE)
            .count();
    if (count > MAX_NAMED_ASSIGNMENTS) {
      throw failure(ResourceAccessException.Reason.ASSIGNMENT_LIMIT);
    }
  }

  private void applyReplacement(
      ResourceAccess access,
      Map<String, ResourceRoleAssignment> before,
      Map<String, ResourceRoleAssignment> after) {
    new ArrayList<>(access.getAssignments())
        .stream()
            .filter(
                assignment -> {
                  ResourceRoleAssignment wanted = after.get(assignment.getGranteeKey());
                  return wanted == null || wanted != assignment;
                })
            .forEach(access::removeAssignment);
    // A changed role is a new row for the same grantee key. Hibernate orders inserts before
    // deletes within one flush, so without this the replacement insert hits
    // UK_ResourceRoleAssignment_access_grantee before the old row is gone.
    gateway.flush();
    after.forEach(
        (key, assignment) -> {
          if (before.get(key) != assignment) {
            access.addAssignment(assignment);
          }
        });
  }

  private <T, ID> ResourceAccessDocument document(
      ProtectedResourceAccess<T, ID> resource,
      ResourceAccess access,
      User subject,
      ResolvedResourceAccess resolved) {
    Map<Long, Set<Long>> assignedGroupIdsByUser = gateway.assignedUserGroupIds(access);
    List<ResourceAccessAssignmentDocument> assignments =
        access.getAssignments().stream()
            .map(assignment -> assignmentDocument(access, assignment, assignedGroupIdsByUser))
            .toList();
    return new ResourceAccessDocument(
        access.getSchemeKey(),
        access.getVersion(),
        assignments,
        callerDocument(resource, access, subject, resolved));
  }

  private ResourceAccessAssignmentDocument assignmentDocument(
      ResourceAccess access,
      ResourceRoleAssignment assignment,
      Map<Long, Set<Long>> assignedGroupIdsByUser) {
    Object id =
        switch (assignment.getGranteeKind()) {
          case USER -> assignment.getUser() == null ? null : assignment.getUser().getId();
          case GROUP -> assignment.getGroup() == null ? null : assignment.getGroup().getId();
          case AUDIENCE -> assignment.getAudienceKey().name();
        };
    boolean available =
        switch (assignment.getGranteeKind()) {
          case USER -> assignment.getUser() != null && assignment.getUser().isEnabled();
          case GROUP ->
              assignment.getGroup() != null && assignment.getGroup().getEnabledMemberSize() > 0;
          case AUDIENCE -> true;
        };
    ResolvedResourceAccess granteeAccess = ResolvedResourceAccess.none();
    if (assignment.getGranteeKind()
            == com.researchspace.model.resourceaccess.ResourceGranteeKind.USER
        && assignment.getUser() != null) {
      granteeAccess =
          resolver.resolveBounded(
              access,
              assignment.getUser(),
              assignedGroupIdsByUser.getOrDefault(assignment.getUser().getId(), Set.of()));
    }
    return new ResourceAccessAssignmentDocument(
        new ResourceAccessGranteeDocument(
            assignment.getGranteeKind(),
            id,
            assignment.getGranteeKey(),
            assignment.getNameSnapshot(),
            assignment.getDetailSnapshot(),
            available,
            granteeAccess.effectiveRole(),
            granteeAccess.roleSources()),
        assignment.getRoleKey());
  }

  private <T, ID> ResourceAccessCallerDocument callerDocument(
      ProtectedResourceAccess<T, ID> resource,
      ResourceAccess access,
      User subject,
      ResolvedResourceAccess resolved) {
    String callerKey = ResourceGranteeKeys.user(subject.getId());
    boolean canLeave = canLeave(access, subject);
    return new ResourceAccessCallerDocument(
        resolved.effectiveRole(),
        resolved.roleSources(),
        new ResourceAccessCallerCapabilities(
            resolved.hasCapability(resource.manageAssignmentsCapability()),
            resolved.hasCapability(resource.manageOwnersCapability()),
            canLeave),
        callerKey);
  }

  private boolean removalPreservesRequiredRoles(ResourceAccess access, User subject) {
    String subjectKey = ResourceGranteeKeys.user(subject.getId());
    Set<String> remainingRoles =
        access.getAssignments().stream()
            .filter(assignment -> !assignment.getGranteeKey().equals(subjectKey))
            .map(ResourceRoleAssignment::getRoleKey)
            .collect(Collectors.toSet());
    return remainingRoles.containsAll(
        schemes.getRequired(access.getSchemeKey()).requiredPersistedRoles());
  }

  private static void requireRead(ResolvedResourceAccess resolved) {
    if (!resolved.hasCapability(ResourceRoleScheme.READ_RESOURCE_CAPABILITY)) {
      throw failure(ResourceAccessException.Reason.NOT_FOUND);
    }
  }

  private static void requireCapability(ResolvedResourceAccess resolved, String capability) {
    if (!resolved.hasCapability(capability)) {
      throw failure(ResourceAccessException.Reason.FORBIDDEN);
    }
  }

  private static Map<String, ResourceRoleAssignment> assignmentsByKey(ResourceAccess access) {
    return access.getAssignments().stream()
        .collect(
            Collectors.toMap(
                ResourceRoleAssignment::getGranteeKey,
                Function.identity(),
                (left, right) -> {
                  throw failure(ResourceAccessException.Reason.DUPLICATE_GRANTEE);
                },
                LinkedHashMap::new));
  }

  private static Map<String, String> semanticRoles(
      Map<String, ResourceRoleAssignment> assignments) {
    return assignments.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getRoleKey()));
  }

  private static Map<String, String> ownerRoles(Map<String, ResourceRoleAssignment> assignments) {
    return assignments.entrySet().stream()
        .filter(entry -> entry.getValue().getRoleKey().equals(ResourceRoleScheme.OWNER_ROLE))
        .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getRoleKey()));
  }

  private static ResourceAccessException failure(ResourceAccessException.Reason reason) {
    return new ResourceAccessException(reason);
  }

  private record Locked<T>(
      T resource,
      ResourceAccess access,
      ResourceAccessDao.AuthorizationFacts authorizationFacts,
      ResolvedResourceAccess resolved) {
    private User subject() {
      return authorizationFacts.subject();
    }
  }
}
