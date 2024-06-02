package com.researchspace.service;

import com.researchspace.model.Group;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.dtos.UserRoleChangeCmnd;
import org.apache.shiro.authz.AuthorizationException;

/** Top-level handler for altering users' global roles */
public interface UserRoleHandler {

  /**
   * Promotes user to PI global role, removes them from previous groups, and creates a new group.
   *
   * @param admin
   * @param toPromote
   * @param commd
   * @return The newly created group
   * @throws AuthorizationException if <code>admin</code> user does not have permissions to grant
   *     the role change.
   */
  Group promoteUserToPiWithGroup(User admin, User toPromote, UserRoleChangeCmnd commd);

  /**
   * Gives global PI role to the user. Check is current user (admin) has permission to update the
   * role, and initializes target account if needed.
   *
   * @param admin
   * @param newPi
   * @return the updated User
   * @throws AuthorizationException if <code>admin</code> user does not have permissions to grant
   *     the role change.
   */
  User grantGlobalPiRoleToUser(User admin, User newPi);

  /**
   * Gives global PI role to the user without additional checks.
   *
   * @param newPi
   * @return the updated User
   */
  User doGrantGlobalPiRoleToUser(User newPi);

  /**
   * Withdraws global PI role from user. Checks if current user (admin) has permission to update the
   * role, and initializes target account if needed.
   *
   * @param admin
   * @param piToDemote
   * @return
   * @throws IllegalStateException if <code>piToDemote</code> is still has {@link RoleInGroup} of PI
   *     in a group
   */
  User revokeGlobalPiRoleFromUser(User admin, User piToDemote);

  /**
   * Withdraws global PI role from user without additional checks.
   *
   * @param piToDemote
   * @return
   */
  User doRevokeGlobalPiRoleFromUser(User piToDemote);

  /**
   * Creates a group for user and adds PI role
   *
   * @param newPI
   * @return the updated User.
   * @throws IllegalArgumentException if deployment properties does not allow operation.
   */
  User setNewlySignedUpUserAsPi(User newPI);
}
