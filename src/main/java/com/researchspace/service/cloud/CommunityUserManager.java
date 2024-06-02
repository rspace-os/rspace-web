package com.researchspace.service.cloud;

import com.researchspace.model.TokenBasedVerification;
import com.researchspace.model.User;
import java.util.List;

public interface CommunityUserManager {

  /**
   * Check if there is a temporary user with the email.
   *
   * @param email
   * @return
   */
  boolean checkTempCloudUser(String email);

  /**
   * Create a list of user checking if the provided e-mail list. If it is an existing user just add
   * to the user list. If it is a new user, create a temporary user and add it to the user list.
   *
   * @param emails
   * @return
   */
  List<User> createInvitedUserList(List<String> emails);

  /**
   * Creates a temporary user using the email or, if there is already user with such email, returns
   * that user.
   *
   * @param email
   * @return
   */
  User createInvitedUser(String email);

  /**
   * Get a temporary user from database. The method sets some attributes from user to the temporary
   * user (like username, password, firstName,...) basically some information which it comes from
   * the sign up form. <b>This method does not save anything to the database!</b> It just copies
   * data from the signup form into the existing user object.
   *
   * @param user - a transient uUser object from the signup form
   * @param email - the email of the new user
   * @return User - the temp user with the spscified email saved previously, with their details
   *     updated from the signupform
   */
  User mergeSignupFormWithTempUser(User user, String email);

  /**
   * Activates the user after they have agreed to accept account, as part of the signup process.
   *
   * <ul>
   *   <li>Enables user
   *   <li>Removes temp flag
   *   <li>Records token as used
   * </ul>
   *
   * @param token Token that is part of a a {@link TokenBasedVerification} object.
   * @return The activated User, or <code>null</code> if could not be activated.
   */
  User activateUser(String token);

  /**
   * Sends verification email to new address provided by the user.
   *
   * @param user
   * @param email
   * @param remoteAddr IP address of remote user, if known
   * @return
   */
  TokenBasedVerification emailChangeRequested(User user, String email, String remoteAddr);

  /**
   * Tries to change email of currently logged user by using provided verification token.
   *
   * @param tokenStr email change verification token
   * @param subject currently logged in user
   * @return true if email change was successful
   */
  boolean emailChangeConfirmed(String tokenStr, User subject);
}
