package com.researchspace.service.resourceaccess;

import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.resourceaccess.ResourceAccess;
import com.researchspace.model.resourceaccess.ResourceAudience;
import com.researchspace.model.resourceaccess.ResourceGranteeKind;
import com.researchspace.model.resourceaccess.ResourceRoleAssignment;
import com.researchspace.model.resourceaccess.ResourceRoleSource;
import com.researchspace.model.resourceaccess.ResourceRoleSourceKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Resolves dynamic direct, group, audience, and implicit roles into one effective role. */
@Component
public class ResourceAccessResolver {

  private final ResourceRoleSchemeRegistry schemes;

  public ResourceAccessResolver(ResourceRoleSchemeRegistry schemes) {
    this.schemes = schemes;
  }

  /** Resolves access from current principal status and memberships, failing closed on bad data. */
  public ResolvedResourceAccess resolve(ResourceAccess access, User subject) {
    if (access == null || subject == null || !subject.isEnabled()) {
      return ResolvedResourceAccess.none();
    }

    return resolve(access, subject, currentGroupIds(subject), true);
  }

  /** Resolves only sources represented by this access document and the supplied memberships. */
  ResolvedResourceAccess resolveBounded(
      ResourceAccess access, User subject, Set<Long> assignedGroupIds) {
    if (access == null || subject == null || !subject.isEnabled()) {
      return ResolvedResourceAccess.none();
    }
    return resolve(access, subject, assignedGroupIds, false);
  }

  private ResolvedResourceAccess resolve(
      ResourceAccess access, User subject, Set<Long> currentGroupIds, boolean includeImplicit) {
    ResourceRoleScheme scheme = schemes.getRequired(access.getSchemeKey());
    Map<String, Integer> ranks = ranksByRole(scheme);
    List<ResourceRoleSource> sources = new ArrayList<>();

    for (ResourceRoleAssignment assignment : access.getAssignments()) {
      validateAssignment(scheme, ranks, assignment);
      if (appliesTo(assignment, subject, currentGroupIds)) {
        sources.add(
            new ResourceRoleSource(
                sourceKind(assignment.getGranteeKind()),
                assignment.getRoleKey(),
                assignment.getGranteeKey(),
                assignment.getNameSnapshot()));
      }
    }

    if (includeImplicit) {
      scheme.implicitRole(subject).ifPresent(role -> addImplicitSource(ranks, role, sources));
    }
    if (sources.isEmpty()) {
      return ResolvedResourceAccess.none();
    }

    String effectiveRole =
        sources.stream()
            .max(Comparator.comparingInt(source -> ranks.get(source.roleKey())))
            .orElseThrow()
            .roleKey();
    return new ResolvedResourceAccess(
        Optional.of(effectiveRole), scheme.capabilities(effectiveRole), sources);
  }

  private static Map<String, Integer> ranksByRole(ResourceRoleScheme scheme) {
    Map<String, Integer> ranks = new HashMap<>();
    scheme.roles().forEach(role -> ranks.put(role.key(), role.rank()));
    return ranks;
  }

  private static Set<Long> currentGroupIds(User subject) {
    return subject.getGroups().stream()
        .filter(group -> group.getId() != null)
        .map(Group::getId)
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  private static void validateAssignment(
      ResourceRoleScheme scheme, Map<String, Integer> ranks, ResourceRoleAssignment assignment) {
    if (!ranks.containsKey(assignment.getRoleKey())) {
      throw new IllegalArgumentException(
          "Unknown role " + assignment.getRoleKey() + " in scheme " + scheme.key());
    }
    if (!scheme
        .allowedGranteeKinds(assignment.getRoleKey())
        .contains(assignment.getGranteeKind())) {
      throw new IllegalArgumentException(
          "Grantee kind "
              + assignment.getGranteeKind()
              + " is not allowed for role "
              + assignment.getRoleKey());
    }
  }

  private static boolean appliesTo(
      ResourceRoleAssignment assignment, User subject, Set<Long> currentGroupIds) {
    return switch (assignment.getGranteeKind()) {
      case USER ->
          assignment.getUser() != null
              && assignment.getUser().isEnabled()
              && assignment.getUser().getId().equals(subject.getId());
      case GROUP ->
          assignment.getGroup() != null
              && assignment.getGroup().getEnabledMemberSize() > 0
              && currentGroupIds.contains(assignment.getGroup().getId());
      case AUDIENCE -> assignment.getAudienceKey() == ResourceAudience.ALL_USERS;
    };
  }

  private static ResourceRoleSourceKind sourceKind(ResourceGranteeKind kind) {
    return switch (kind) {
      case USER -> ResourceRoleSourceKind.DIRECT;
      case GROUP -> ResourceRoleSourceKind.GROUP;
      case AUDIENCE -> ResourceRoleSourceKind.AUDIENCE;
    };
  }

  private static void addImplicitSource(
      Map<String, Integer> ranks, String role, List<ResourceRoleSource> sources) {
    if (!ranks.containsKey(role)) {
      throw new IllegalArgumentException("Implicit resource role is not registered: " + role);
    }
    sources.add(ResourceRoleSource.implicit(role));
  }
}
