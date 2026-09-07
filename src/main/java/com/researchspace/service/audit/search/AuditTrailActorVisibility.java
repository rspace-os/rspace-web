package com.researchspace.service.audit.search;

import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.service.UserManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Applies the existing audit-actor visibility rules to an audit search. */
@Component
public class AuditTrailActorVisibility {

  private final UserManager userManager;

  public AuditTrailActorVisibility(UserManager userManager) {
    this.userManager = userManager;
  }

  /**
   * Returns a search element restricted to the audit actors visible to {@code subject}.
   *
   * @param inputSearchConfig requested audit filters
   * @param subject user performing the search
   * @return a search element with the existing actor restriction applied
   */
  public AuditTrailSearchElement restrict(IAuditTrailSearchConfig inputSearchConfig, User subject) {
    AuditTrailSearchElement internalConfig = new AuditTrailSearchElement(inputSearchConfig);
    if (isSysAdmin(subject)) {
      return internalConfig;
    }

    PaginationCriteria<User> pagination = PaginationCriteria.createDefaultForClass(User.class);
    pagination.setResultsPerPage(Integer.MAX_VALUE);
    List<User> viewableUsers =
        new ArrayList<>(userManager.getViewableUsers(subject, pagination).getResults());
    if (!viewableUsers.contains(subject)) {
      viewableUsers.add(subject);
    }

    boolean usernameRestriction = !inputSearchConfig.getUsernames().isEmpty();
    Set<String> usernames =
        viewableUsers.stream()
            .map(User::getUsername)
            .filter(
                username ->
                    !usernameRestriction || inputSearchConfig.getUsernames().contains(username))
            .collect(Collectors.toSet());
    internalConfig.setUsernames(usernames);
    return internalConfig;
  }

  /** Returns whether the subject may see audit events for every actor. */
  public boolean isSysAdmin(User subject) {
    return subject.hasRole(Role.SYSTEM_ROLE);
  }
}
