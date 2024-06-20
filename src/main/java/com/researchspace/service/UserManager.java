package com.researchspace.service;

import com.researchspace.Constants;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.TokenBasedVerification;
import com.researchspace.model.TokenBasedVerificationType;
import com.researchspace.model.User;
import com.researchspace.model.UserPreference;
import com.researchspace.model.dto.UserBasicInfo;
import com.researchspace.model.dtos.UserRoleView;
import com.researchspace.model.dtos.UserSearchCriteria;
import com.researchspace.model.events.UserAccountEvent;
import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.preference.Preference;
import com.researchspace.model.views.UserView;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.orm.ObjectRetrievalFailureException;

public interface UserManager extends GenericManager<User, Long> {

  /**
   * Retrieves a user by userId. An exception is thrown if user not found
   *
   * @param userId the identifier for the user
   * @return User
   */
  User getUser(String userId);

  /**
   * Gets public user info based on search term
   *
   * @param term
   * @return a List of UserBasicInfo
   * @throws Exception
   */
  List<UserBasicInfo> searchPublicUserInfoList(String term);

  void updateSessionUser(Long userID);

  /**
   * Finds a user by their username. Equivalent to <code>getUserByUsername (username, false)</code>,
   * so be aware that the object might be retrieved from http session rather than from the database.
   *
   * @param username the user's username used to login
   * @return User a populated user object
   */
  User getUserByUsername(String username);

  /**
   * To be used during login flow - finds a user with given username, or usernameAlias
   *
   * @param usernameOrAlias the user's username or usernameAlias used to login
   * @return User a populated user object, same as {@link #getUserByUsername(String)}
   */
  User getUserByUsernameOrAlias(String usernameOrAlias);

  /**
   * Gets a possibly empty but noon-null list of Users with given email
   *
   * @param userEmail
   * @return
   */
  List<User> getUserByEmail(String userEmail);

  /**
   * Searches database for users with a username, email or name property matching the term. Users
   * who opted out from public listings and are not connected to subject are omitted.
   *
   * @param term
   * @return A possibly empty but non-null list of users.
   */
  List<User> searchUsers(String term);

  /**
   * Method allowing to find a username from incoming username or usernameAlias
   *
   * @param usernameOrAlias
   * @return
   */
  String findUsernameByUsernameOrAlias(String usernameOrAlias);

  /**
   * Variant method to retrieve user based on username, with option to force a fresh reload from the
   * database.
   *
   * @param username The username
   * @param forceRefresh {@link Boolean}
   * @return A {@link User}
   * @throws ObjectRetrievalFailureException if user not found.
   */
  User getUserByUsername(String username, boolean forceRefresh);

  /**
   * Retrieves a list of all users.
   *
   * @return List
   */
  List<User> getUsers();

  /**
   * Saves a user's information for the first time; encrypts and salts password, adds default
   * roles..
   *
   * <p>This method assumes that basic validation by UserValidator has been performed, that the user
   * fields are set correctly.
   *
   * @param user the user's information
   * @throws UserExistsException thrown when user already exists (either by username or by email)
   * @return user the updated user object
   */
  User saveNewUser(User user) throws UserExistsException;

  /**
   * Saves a user and updates password if password has changed. Does not check if this user's email
   * already exists. <br>
   * For <em>new </em> users, use 'saveNewUser'
   *
   * @param toChange
   * @return the persisted user.
   */
  User saveUser(User toChange);

  void addPermission(ConstraintBasedPermission permission, String userName);

  void removePermission(ConstraintBasedPermission permission, String userName);

  /**
   * Gets user with initialized preferences
   *
   * @param name
   * @return
   */
  Set<UserPreference> getUserAndPreferencesForUser(String name);

  UserPreference getPreferenceForUser(User user, Preference preference);

  /**
   * Gets user from current Shiro session keyed by SessionAttributeUtils.USER.<br>
   * This object may be stale - if this is likely to be a problem then load a user using one of the
   * getUserBy.. methods in this class.
   *
   * @return
   */
  User getAuthenticatedUserInSession();

  /**
   * Creates a {@link TokenBasedVerification} object recording the time of the request. If user is
   * not provided, it is looked up by email, but if email is unknown on the system, returns null and
   * does not create an entry in the database.
   *
   * @param email
   * @param remoteHost
   * @param type the TokenBasedVerificationType defining the purpose of this verification
   * @return
   */
  TokenBasedVerification createTokenBasedVerificationRequest(
      User user, String email, String remoteHost, TokenBasedVerificationType type);

  TokenBasedVerification getUserVerificationToken(String token);

  /** Applies a password change from the 'Forgot password' scenario */
  TokenBasedVerification applyLoginPasswordChange(String newPassword, String token);

  /** Applies a password change from the 'Forgot verification password' scenario */
  TokenBasedVerification applyVerificationPasswordChange(String newPassword, String token);

  List<User> getAvailableAdminUsers();

  /**
   * Gets all users in the community that is administered by the admin with this username
   *
   * @param name the admin's username
   * @return return possibly empty but non-null list of {@link User}s.
   */
  List<User> getAllUsersInAdminsCommunity(String name);

  /**
   * Boolean test for whether <code>usernameToTest</code> is a member of the Community administered
   * by admin
   *
   * @param admin an RSpace Community Admin
   * @param usernameToTest
   * @return <code>true</code> if he is, <code>false</code> otherwise. If user is a sysadmin,
   *     returns <code>true</code> by default.
   * @throws IllegalArgumentException if <code>admin</code> does not have an admin role.
   */
  boolean isUserInAdminsCommunity(User admin, String usernameToTest);

  /**
   * Gets a list of users within the administrative remit of the specified subject. In general, this
   * will be governed by the subject's role:
   *
   * <ul>
   *   <li>{@link Constants#USER_ROLE} can only view themselves
   *   <li>{@link Constants#PI_ROLE} can view people in their lab group, where they have a PI role
   *       in that lab group.
   *   <li>{@link Constants#ADMIN_ROLE} will retrieve all users in the admin's community
   *   <li>{@link Constants#SYSADMIN_ROLE} will retrieve all users.
   * </ul>
   *
   * @param subject The {@link User} performing the search.
   * @param pgCrit A {@link PaginationCriteria}. This can be configured with a {@link
   *     UserSearchCriteria} object to further refine the search - e.g., by name, email or by
   *     enabled state.
   * @return An {@link ISearchResults} of Users
   */
  ISearchResults<User> getViewableUsers(User subject, PaginationCriteria<User> pgCrit);

  /**
   * Boolean test for whether a user with the specified username exists in the DB.
   *
   * @param username
   * @return <code>true</code> if exists, <code>false</code> otherwise.
   */
  boolean userExists(String username);

  /**
   * Marks a {@link TokenBasedVerification} as completed, meaning it cannot be used again.
   *
   * @param token A {@link TokenBasedVerification}.
   * @return The modified {@link TokenBasedVerification} object
   */
  TokenBasedVerification setTokenCompleted(TokenBasedVerification token);

  /**
   * Gets a list of users where the subject has some permission related to one or more base records.
   * This method is not the same as getViewableUsers().
   *
   * <p>This method is used to populate the auto complete user list when search by owner on the
   * workspace page (simple search and advanced search).
   *
   * <p>The result is the combination of two list :
   *
   * <ul>
   *   <li>List Viewable user list by role :
   *       <ul>
   *         <li>{@link Constants#USER_ROLE} and {@link Constants#SYSADMIN_ROLE} can only see
   *             themselves.
   *         <li>{@link Constants#PI_ROLE} can view people in their lab group, where they have a PI
   *             role in that lab group.
   *         <li>{@link Constants#ADMIN_ROLE} can view people in their community
   *       </ul>
   *   <li>List Viewable shared record owner : List of base record owner who they have shared one or
   *       more base records individually with the subject or with groups where the subject is a
   *       member.
   * </ul>
   *
   * @param subject
   * @return List<User> User list.
   */
  List<User> getViewableUserList(User subject);

  /**
   * Returns list of users that has some connection to the subject and will be able to see subject's
   * profile even if subject opted out from public listings.
   *
   * <p>The result is the combination of two list :
   *
   * <ul>
   *   <li>List returned by {@link #getViewableUserList(User)}, which contains users who can see
   *       some records belonging to subject
   *   <li>List of all group members in any group subject belongs too.
   * </ul>
   *
   * Method also populates subject's list of connected users, so the {@link
   * User#isConnectedToUser(User)} method can be subsequently called on the subject.
   *
   * @param subject
   * @return
   */
  List<User> populateConnectedUserList(User subject);

  /**
   * Returns list of groups that user has connection to i.e. they are either a member, or are a
   * Community Admin of.
   *
   * <p>Method populates subject's list of connected group, so the {@link
   * User#isConnectedToGroup(Group)} method can be subsequently called on the subject.
   *
   * @param subject
   * @return
   */
  List<Group> populateConnectedGroupList(User subject);

  /**
   * Verifies that given email address is not used by another user
   *
   * @param email
   * @throws UserExistsException when there is an existing user with this email
   */
  void checkEmailUnique(String email) throws UserExistsException;

  /**
   * Updates a user preference
   *
   * @param preference
   * @param value - must be a valid value for preference type
   * @param subject - the username
   * @return the newly created preference
   * @throws IllegalArgumentException if preference value isn't a valid value
   */
  UserPreference setPreference(Preference preference, String value, String subject);

  /**
   * Variant of getUserByUsername that does not return User from Session. In general usage, use
   * getUserByUsername(). This method is or specifi cases when a Session is not available
   *
   * @param username
   * @return
   */
  User getUserByUsernameNoSession(String username);

  /**
   * Updates email of existing user
   *
   * @return the updated user
   */
  User changeEmail(User user, String email);

  User changeUsernameAlias(Long userId, String usernameAlias) throws UserExistsException;

  Optional<String> getUsernameByToken(String token);

  /**
   * Marks user profile as private, so it won't be displayed in Directory listings
   *
   * @param value if 'true' the profile is marked as private, if 'false' as public
   * @param user to change
   * @return
   */
  User setAsPrivateProfile(boolean value, User user);

  /** Gets basic user information without loading up all relations and extra information */
  List<UserView> getAllUsersView();

  /**
   * Gets a minimal information about user by username
   *
   * @param username
   * @return UserView
   */
  UserView getUserViewByUsername(String username);

  /**
   * Gets basic information for all users + roles
   *
   * @return
   */
  List<UserRoleView> getAllUsersViewWithRoles();

  /**
   * Gets a full name from the username. Cacheable.
   *
   * @param username
   * @return
   */
  String getFullNameByUsername(String username);

  /** Saves a new user-account event */
  UserAccountEvent saveUserAccountEvent(UserAccountEvent event);

  /**
   * Gets user-account events for a specific user
   *
   * @param toQuery The user whose account events are being retrieved
   * @return A List of UserAccountEvents (possibly empty but not null)
   */
  List<UserAccountEvent> getAccountEventsForUser(User toQuery);

  /**
   * When a sysadmin is operating as another user, the other user will be the subject.
   *
   * @param subject
   * @return the sysadmin ('original') user in an operate-as scenario; else returns the argument
   *     user unchanged
   * @throws IllegalStateException if the original user is not a sysadmin ROLE
   */
  User getOriginalUserForOperateAs(User subject);
}
