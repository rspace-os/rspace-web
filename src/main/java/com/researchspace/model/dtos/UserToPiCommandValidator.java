package com.researchspace.model.dtos;

import com.researchspace.model.Role;
import com.researchspace.model.User;
import java.util.Set;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/**
 * Rejects UserToPiPRomotion if:
 *
 * <ul>
 *   <li>User is already a PI
 *   <li>User is not a user
 *   <li>User is currently logged in
 * </ul>
 */
public class UserToPiCommandValidator implements Validator {

  private Set<String> activeUsers;
  private User candidatePI;

  public UserToPiCommandValidator(Set<String> activeUsers, User candidatePI) {
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
    if (candidatePI.hasRole(Role.PI_ROLE)) {
      errors.reject("system.user2pi.useralreadyPI", new Object[] {candidatePI.getFullName()}, null);
    }
    if (!candidatePI.hasRole(Role.USER_ROLE)) {
      errors.reject(
          "system.user2pi.userNotUserRole", new Object[] {candidatePI.getFullName()}, null);
    }
  }
}
