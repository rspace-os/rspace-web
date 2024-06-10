package com.researchspace.dao;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.Role;
import com.researchspace.model.TokenBasedVerification;
import com.researchspace.model.User;
import com.researchspace.model.UserProfile;
import com.researchspace.model.dtos.UserRoleView;
import com.researchspace.model.views.UserStatistics;
import com.researchspace.model.views.UserView;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** User Data Access Object (GenericDao) interface. */
public interface UserDao extends GenericDao<User, Long> {
  /**
   * Gets a list of active (i.e., non-blocked) users ordered by the uppercase version of their
   * username.
   *
   * @return List populated list of users
   */
  List<User> getUsers();

  /**
   * Gets basic user information for all users in the system. Use this in preference to getUsers()
   * which loads up much related information
   *
   * @return
   */
  List<UserRoleView> getUsersBasicInfoWithRoles();

  /**
   * Retrieves some statistic about the number of enabled, active, online users, etc
   *
   * @param daysToCountAsActive the number of days previous to the current date that a user needs to
   *     have logged in, to be considered active. E.g., 7 - active in past week; '31', active in
   *     past month, etc.
   * @return A UserStatistics object
   */
  UserStatistics getUserStats(int daysToCountAsActive);

  /**
   * Saves a user's information.
   *
   * @param user the object to be saved
   * @return the persisted User object
   */
  User saveUser(User user);

  String getUserPassword(String username);

  User getUserByUsername(String userName);

  Optional<User> getUserByUsernameAlias(String usernameAlias);

  /**
   * Gets a paginated list of users and returns an {@link ISearchResults}
   *
   * @param pgCrit
   * @return
   */
  ISearchResults<User> searchUsers(PaginationCriteria<User> pgCrit);

  /**
   * Gets all users who have a PI role in at least one group.
   *
   * @param searchTerm optional, returns only PIs with partially matching details
   * @return
   */
  Set<User> getAllGroupPis(String searchTerm);

  TokenBasedVerification saveTokenBasedVerification(TokenBasedVerification upwChange);

  /**
   * Gets a UserPassword change object from its token
   *
   * @param token
   * @return
   */
  TokenBasedVerification getByToken(String token);

  /**
   * Gets a list of users with the given email
   *
   * @param email
   * @return A List<User>, possibly empty
   */
  List<User> getUserByEmail(String email);

  /**
   * Gets a list of users matching username, first name, last name or email.
   *
   * @param term
   * @return
   */
  List<User> searchUsers(String term);

  /**
   * Gets a user profile for the specified user
   *
   * @param user
   * @return A {@link UserProfile} or <code>null</code> if a profile does not exist for that user.
   */
  UserProfile getUserProfileByUser(User user);

  /**
   * Saves or updates a user profile
   *
   * @param profile
   * @return the saved UserProfile
   */
  UserProfile saveUserProfile(UserProfile profile);

  /**
   * Gets UserProfile by its id
   *
   * @param profileId
   * @return the user profile or <code>null</code> if no such profile exists
   */
  UserProfile getUserProfileById(Long profileId);

  /**
   * Gets users ordered by role
   *
   * @param role
   * @return
   */
  ISearchResults<User> listUsersByRole(Role role, PaginationCriteria<User> pgCrit);

  /**
   * Gets a list of admins that are not currently assigned to a community.
   *
   * @return A possiblye mpty but non-null list of users.
   */
  List<User> getAvailableAdminsForCommunity();

  ISearchResults<User> listUsersInCommunity(Long communityId, PaginationCriteria<User> pgCrit);

  /**
   * Boolean test for whether <code>usernameToTest</code> is a a member of the given community
   *
   * @param usernameToTest
   * @param communityId the id of the community
   * @return <code>true</code> if he is, <code>false</code> otherwise
   */
  boolean isUserInAdminsCommunity(String usernameToTest, Long communityId);

  boolean userExists(String username);

  /**
   * Returns users that subject can see thanks to their role. <br>
   * If subject has User role, this method returns a single element list of the user. If subject has
   * PI/Lab Admin with View All role, gets a non-redundant list of all group members in groups where
   * subject has that role. If subject is Community Admin, returns all users in their Community.
   *
   * @param subject
   * @return
   */
  List<User> getViewableUsersByRole(User subject);

  /**
   * Gets a list of {@link User}, with no duplicates, of users who have shared documents either
   * directly with the <code>subject</code>, or with a group the <code>subject</code> belongs to.
   *
   * @param subject
   * @return a Possibly empty but non-null list of Users.
   */
  List<User> getViewableSharedRecordOwners(User subject);

  /**
   * Gets a list of IDs of users in the subject's Community
   *
   * @param subject
   * @return
   */
  List<Long> getUserIdsInAdminsCommunity(User subject);

  Optional<String> getUsernameByToken(String token);

  /**
   * Loads basic information for all users
   *
   * @return
   */
  List<UserView> getAllUsersView();

  /**
   * Gets minimal user info by username
   *
   * @param username
   * @return
   */
  UserView getUserViewByUsername(String username);

  /** Retrieves all tags used for tagging users (on System->Users page) */
  List<String> getAllUserTags();
}
