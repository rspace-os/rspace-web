package com.researchspace.model.dtos;

import com.researchspace.model.Role;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import java.util.Set;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/**
 * Rejects PiToUser role change if any are true:
 *
 * <ul>
 *   <li>PI is not a PI
 *   <li>PI is currently logged in
 *   <li>Pi is still a PI role in any group.
 * </ul>
 */
public class PiToUserCommandValidator implements Validator {

  private Set<String> activeUsers;
  private User candidatePI;

  public PiToUserCommandValidator(Set<String> activeUsers, User candidatePI) {
    super();
    this.activeUsers = activeUsers;
    this.candidatePI = candidatePI;
  }

  @Override
  public boolean supports(Class<?> clazz) {
    return clazz.isAssignableFrom(UserRoleChangeCmnd.class);
  }

  @Override
  public void validate(Object target, Errors errors) {
    if (activeUsers.contains(candidatePI.getUsername())) {
      errors.reject("system.user2pi.userIsActive", new Object[] {candidatePI.getFullName()}, null);
    }
    if (!candidatePI.hasRole(Role.PI_ROLE)) {
      errors.reject("system.piToUser.piNotpiRole", new Object[] {candidatePI.getFullName()}, null);
    }
    boolean isStillPi =
        candidatePI.getGroups().stream()
            .anyMatch(grp -> candidatePI.hasRoleInGroup(grp, RoleInGroup.PI));
    if (isStillPi) {
      errors.reject(
          "system.piToUser.piStillPiInGroup", new Object[] {candidatePI.getFullName()}, null);
    }
  }
}
