package com.researchspace.service.impl;

import static java.lang.String.format;

import com.researchspace.CacheNames;
import com.researchspace.Constants;
import com.researchspace.analytics.service.AnalyticsManager;
import com.researchspace.core.util.CryptoUtils;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.dao.CommunityDao;
import com.researchspace.dao.RoleDao;
import com.researchspace.dao.UserAccountEventDao;
import com.researchspace.dao.UserDao;
import com.researchspace.model.Community;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.Role;
import com.researchspace.model.TokenBasedVerification;
import com.researchspace.model.TokenBasedVerificationType;
import com.researchspace.model.User;
import com.researchspace.model.UserPreference;
import com.researchspace.model.dto.UserBasicInfo;
import com.researchspace.model.dtos.UserRoleView;
import com.researchspace.model.events.UserAccountEvent;
import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.SecurityLogger;
import com.researchspace.model.preference.Preference;
import com.researchspace.model.record.IActiveUserStrategy;
import com.researchspace.model.record.IRecordFactory;
import com.researchspace.model.views.UserView;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.IVerificationPasswordValidator;
import com.researchspace.service.UserExistsException;
import com.researchspace.service.UserManager;
import com.researchspace.session.SessionAttributeUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.crypto.RandomNumberGenerator;
import org.apache.shiro.crypto.SecureRandomNumberGenerator;
import org.apache.shiro.session.Session;
import org.apache.shiro.util.ByteSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;

/** Implementation of UserManager interface. */
@Service("userManager")
public class UserManagerImpl extends GenericManagerImpl<User, Long> implements UserManager {

  private static final Logger SECURITY_LOG = LoggerFactory.getLogger(SecurityLogger.class);

  private RandomNumberGenerator randNumGen = new SecureRandomNumberGenerator();
  private UserDao userDao;

  public UserManagerImpl(@Autowired UserDao userDao) {
    this.dao = userDao;
    this.userDao = userDao;
  }

  private @Autowired CommunityDao communityDao;
  private @Autowired RoleDao roleDao;
  private @Autowired UserAccountEventDao accountEventDao;
  private @Autowired IPermissionUtils permissnUtils;
  private @Autowired AnalyticsManager analyticsManager;
  @Autowired private IPropertyHolder properties;

  private @Autowired IVerificationPasswordValidator verificationPasswordValidator;

  @SuppressWarnings("unused") // unused but required for spring ApplicationContext to load properly
  private @Autowired IRecordFactory recordFactory;

  /** {@inheritDoc} */
  public User getUser(String userId) {
    return userDao.get(Long.valueOf(userId));
  }

  /** {@inheritDoc} */
  public List<User> getUsers() {
    return userDao.getUsers();
  }

  @Override
  public User saveUser(User toChange) {
    encryptPasswordIfRequired(toChange);
    try {
      return doSave(toChange);
    } catch (UserExistsException e) {
      String msg = "This method should not be called for new users ... user saveNewUser instead!";
      log.error(msg);
      throw new IllegalStateException(msg);
    }
  }

  @Override
  public void checkEmailUnique(String email) throws UserExistsException {
    List<User> users = userDao.getUserByEmail(email);
    if (!users.isEmpty()) {
      throw new UserExistsException("There is already a user registered with that email!");
    }
  }

  /** {@inheritDoc} */
  public User saveNewUser(User user) throws UserExistsException {

    checkEmailUnique(user.getEmail());
    encryptPasswordIfRequired(user);

    // set up roles if not already done.
    if (user.getRoles().isEmpty()) {
      if (user.getRole() != null) {
        if (Role.isRoleStringIdentifiable(user.getRole())) {
          user.addRole(roleDao.getRoleByName(user.getRole()));
          // if it's a pi role ,pi's have a user role as well
          if (user.getRole().equals(Constants.PI_ROLE)) {
            user.addRole(roleDao.getRoleByName(Constants.USER_ROLE));
          }
        } else {
          // default -set to user role
          user.addRole(roleDao.getRoleByName(Constants.USER_ROLE));
        }
      }
    }

    User savedUser = doSave(user);
    if (!savedUser.isTempAccount()) {
      analyticsManager.userCreated(savedUser);
    }
    return savedUser;
  }

  private User doSave(User user) throws UserExistsException {
    try {
      return userDao.saveUser(user);
    } catch (DataIntegrityViolationException | JpaSystemException e) {
      log.warn(e.getMessage());
      throw new UserExistsException("User '" + user.getUsername() + "' already exists!");
    }
  }

  private void encryptPasswordIfRequired(User user) {
    // Get and prepare password management-related artifacts
    boolean passwordChanged = false;

    // Check whether we have to encrypt (or re-encrypt) the password
    if (user.getVersion() == null) {
      // New user, always encrypt
      passwordChanged = true;
    } else {
      // Existing user, check password in DB
      String currentPassword = userDao.getUserPassword(user.getUsername());
      if (currentPassword == null) {
        passwordChanged = true;
      } else {
        if (!currentPassword.equals(user.getPassword())) {
          passwordChanged = true;
        }
      }
    }

    // If password was changed (or new user), encrypt it
    if (passwordChanged) {
      changePassword(user.getPassword(), user);
    }
  }

  private void changePassword(String plainText, User user) {
    final int saltlength = 16;
    ByteSource salt = randNumGen.nextBytes(saltlength);
    user.setPassword(CryptoUtils.hashWithSha256inHex(plainText, salt));
    user.setSalt(salt.toBase64());
  }

  // session may be null if it's api call
  public User getUserByUsername(String username, boolean forceRefresh) {
    Session session = SecurityUtils.getSubject().getSession(false);

    Object sessionUser = null;
    if (session != null) {
      sessionUser = session.getAttribute(SessionAttributeUtils.USER);
    }

    if (!forceRefresh
        && sessionUser != null
        && ((User) sessionUser).getUsername().equals(username)) {
      return (User) session.getAttribute(SessionAttributeUtils.USER);
    } else {
      User u = userDao.getUserByUserName(username);
      if (u.getUsername().equals(SecurityUtils.getSubject().getPrincipal()) && session != null) {
        session.setAttribute(SessionAttributeUtils.USER, u);
      }
      return u;
    }
  }

  @Override
  public void updateSessionUser(Long userID) {
    Session session = SecurityUtils.getSubject().getSession();
    session.setAttribute(SessionAttributeUtils.USER, this.get(userID));
  }

  /**
   * {@inheritDoc}
   *
   * @param username the login name of the human
   * @return User the populated user object
   */
  public User getUserByUsername(String username) {
    return getUserByUsername(username, false);
  }

  public List<User> getUserByEmail(String userEmail) {
    return userDao.getUserByEmail(userEmail);
  }

  public List<User> searchUsers(String term) {
    final int minLength = 3;
    if (StringUtils.isBlank(term) || term.trim().length() < minLength) {
      throw new IllegalArgumentException(
          "Search term [" + term + "] must be at least 3 characters");
    }
    return userDao.searchUsers(term);
  }

  @Override
  public List<UserBasicInfo> searchPublicUserInfoList(String term) {
    User userInSession = getAuthenticatedUserInSession();
    if (properties.isProfileHidingEnabled()) {
      populateConnectedUserList(userInSession);
    }
    List<User> users = searchUsers(term);
    return getPublicOrPrivateButConnectedUserBasicInfos(userInSession, users);
  }

  /*
   * Get UserBasicInfo from users with public profile or (if profile hiding enabled) from private profile
   * if the user is connected to the logged in user.
   */
  private List<UserBasicInfo> getPublicOrPrivateButConnectedUserBasicInfos(
      User userInSession, List<User> users) {
    List<UserBasicInfo> userInfos = new ArrayList<>();
    users.remove(userInSession);
    for (User user : users) {
      if (user.isPrivateProfile()
          && properties.isProfileHidingEnabled()
          && !userInSession.isConnectedToUser(user)) {
        continue;
      }
      userInfos.add(user.toPublicInfo());
    }
    return userInfos;
  }

  @Override
  public void addPermission(ConstraintBasedPermission permission, String userName) {
    User u = getUserByUsername(userName, true);
    u.addPermission(permission);
    dao.save(u);
    permissnUtils.refreshCache();
  }

  @Override
  public void removePermission(ConstraintBasedPermission permission, String userName) {
    User u = getUserByUsername(userName);
    u.removePermission(permission);
    dao.save(u);
    permissnUtils.refreshCache();
  }

  @Override
  public Set<UserPreference> getUserAndPreferencesForUser(String username) {
    User u = getUserByUsername(username, true);
    return u.getAllUserPreferences();
  }

  @Override
  @CachePut(
      value = "com.researchspace.model.UserPreference",
      key = "#username + #preference.name()")
  @CacheEvict(value = CacheNames.INTEGRATION_INFO, key = "#username + #preference.name()")
  public UserPreference setPreference(Preference preference, String value, String username) {
    User subject = getUserByUsername(username, true);
    Validate.notNull(preference, "preference can't be null");
    String error = preference.getInvalidErrorMessageForValue(value);
    if (!StringUtils.isEmpty(error)) {
      throw new IllegalArgumentException(error);
    }
    UserPreference userPreference = new UserPreference(preference, subject, value);
    subject.setPreference(userPreference);
    save(subject);
    return userPreference;
  }

  @Override
  @Cacheable(
      value = "com.researchspace.model.UserPreference",
      key = "#user.username + #preference.name()")
  public UserPreference getPreferenceForUser(User user, Preference preference) {
    user = get(user.getId());
    return user.getValueForPreference(preference);
  }

  @Override
  public User getAuthenticatedUserInSession() {
    Session session = SecurityUtils.getSubject().getSession();
    User user = (User) session.getAttribute(SessionAttributeUtils.USER);
    if (user == null) {
      // can happen e.g. in SSO environment, see RSPAC-2679
      if (SecurityUtils.getSubject().getPrincipal() instanceof String) {
        String currentSubject = (String) SecurityUtils.getSubject().getPrincipal();
        user = getUserByUsername(currentSubject); // updates session user
      }
    }
    return user;
  }

  @Override
  public TokenBasedVerification createTokenBasedVerificationRequest(
      User user, String email, String remoteHost, TokenBasedVerificationType type) {
    if (StringUtils.isBlank(email)) {
      throw new IllegalArgumentException("email cannot be empty!");
    }
    if (user == null) {
      List<User> found = userDao.getUserByEmail(email);
      if (found == null || found.isEmpty()) {
        return null;
      }
    }
    TokenBasedVerification upc = new TokenBasedVerification(email, null, type);
    upc.setIpAddressOfRequestor(remoteHost);
    upc.setUser(user);
    return userDao.saveTokenBasedVerification(upc);
  }

  @Override
  public TokenBasedVerification getUserVerificationToken(String token) {
    if (StringUtils.isBlank(token)) {
      throw new IllegalArgumentException("Token cannot be empty");
    }
    TokenBasedVerification upc = userDao.getByToken(token);
    return upc;
  }

  @Override
  public TokenBasedVerification applyLoginPasswordChange(String newPassword, String token) {
    return applyPasswordChange(newPassword, token, false);
  }

  @Override
  public TokenBasedVerification applyVerificationPasswordChange(String newPassword, String token) {
    return applyPasswordChange(newPassword, token, true);
  }

  private TokenBasedVerification applyPasswordChange(
      String newPassword, String token, boolean isVerificationPassword) {
    TokenBasedVerification upwChange = userDao.getByToken(token);
    if (upwChange == null) {
      return null;
    }
    List<User> users = userDao.getUserByEmail(upwChange.getEmail());
    if (users == null || users.isEmpty()) {
      return null;
    }
    User toChange = users.get(0);

    if (isVerificationPassword) {
      toChange.setVerificationPassword(
          verificationPasswordValidator.hashVerificationPassword(newPassword));
    } else {
      changePassword(newPassword, toChange);
    }
    userDao.save(toChange);
    setTokenCompleted(upwChange);

    return upwChange;
  }

  @Override
  public Optional<String> getUsernameByToken(String token) {
    return userDao.getUsernameByToken(token);
  }

  @Override
  public List<User> getAvailableAdminUsers() {
    return userDao.getAvailableAdminsForCommunity();
  }

  @Override
  public boolean isUserInAdminsCommunity(User admin, String usernameToTest) {
    if (admin.hasRole(Role.SYSTEM_ROLE)) {
      return true;
    }
    assertAdminHasAdminRole(admin);

    List<Community> comms = communityDao.listCommunitiesForAdmin(admin.getId());
    if (comms.isEmpty()) {
      return false;
    }
    return userDao.isUserInAdminsCommunity(usernameToTest, comms.get(0).getId());
  }

  private void assertAdminHasAdminRole(User admin) {
    if (!admin.hasAdminRole()) {
      throw new IllegalArgumentException("User must be an admin");
    }
  }

  @Override
  public List<User> getAllUsersInAdminsCommunity(String adminUsername) {

    PaginationCriteria<User> pgcrit = PaginationCriteria.createDefaultForClass(User.class);
    pgcrit.setResultsPerPage(Integer.MAX_VALUE); // get all
    ISearchResults<User> usersInCommunity = listUsersInAdminsCommunity(adminUsername, pgcrit);
    // avoid returning immutable EMPTY_LIST if no users in community
    return usersInCommunity == null || usersInCommunity.getTotalHits() == 0
        ? new ArrayList<>()
        : usersInCommunity.getResults();
  }

  private ISearchResults<User> listUsersInAdminsCommunity(
      String adminUsername, PaginationCriteria<User> pgCrit) {
    User admin = userDao.getUserByUserName(adminUsername);
    assertAdminHasAdminRole(admin);
    List<Community> comms = communityDao.listCommunitiesForAdmin(admin.getId());
    if (comms.isEmpty()) {
      return SearchResultsImpl.emptyResult(pgCrit);
    }
    Community comm = comms.get(0);

    ISearchResults<User> usersInCommunity = userDao.listUsersInCommunity(comm.getId(), pgCrit);
    return usersInCommunity;
  }

  @Override
  public ISearchResults<User> getViewableUsers(User subject, PaginationCriteria<User> pgCrit) {
    if (subject.hasRole(Role.SYSTEM_ROLE)) {
      return userDao.searchUsers(pgCrit);
    }
    if (subject.hasRole(Role.ADMIN_ROLE)) {
      return listUsersInAdminsCommunity(subject.getUsername(), pgCrit);
    }
    if (subject.hasRole(Role.PI_ROLE)) {
      List<User> users = new ArrayList<User>();
      for (Group g : subject.getGroups()) {
        if (g.isLabGroup() && g.getUserGroupForUser(subject).isPIRole()) {
          users.addAll(g.getMembers());
        }
      }
      return new SearchResultsImpl<User>(users, pgCrit, users.size());
    }

    List<User> user = new ArrayList<User>();
    user.add(subject);
    return new SearchResultsImpl<>(user, pgCrit, 1);
  }

  @Override
  public boolean userExists(String username) {
    return userDao.userExists(username);
  }

  @Override
  public TokenBasedVerification setTokenCompleted(TokenBasedVerification token) {
    token.setResetCompleted(true);
    return userDao.saveTokenBasedVerification(token);
  }

  @Override
  public List<User> getViewableUserList(User subject) {
    List<User> result = new ArrayList<>();
    Set<User> distinctUsers = new HashSet<>();
    distinctUsers.addAll(userDao.getViewableUsersByRole(subject));
    distinctUsers.addAll(userDao.getViewableSharedRecordOwners(subject));
    result.addAll(distinctUsers);
    return result;
  }

  @Override
  public List<User> populateConnectedUserList(User subject) {
    List<User> result = new ArrayList<>();
    Set<User> distinctUsers = new HashSet<>();
    distinctUsers.addAll(getViewableUserList(subject));
    for (Group g : subject.getGroups()) {
      distinctUsers.addAll(g.getMembers());
    }
    result.addAll(distinctUsers);
    subject.setConnectedUsers(result);
    return result;
  }

  @Override
  public List<Group> populateConnectedGroupList(User subject) {
    List<Group> result = new ArrayList<>();
    Set<Group> distinctGroups = new HashSet<>();
    // groups where subject is a member
    distinctGroups.addAll(subject.getGroups());
    // groups in subject's community
    if (subject.hasAdminRole()) {
      List<Community> comms = communityDao.listCommunitiesForAdmin(subject.getId());
      for (Community c : comms) {
        distinctGroups.addAll(c.getLabGroups());
      }
    }
    result.addAll(distinctGroups);
    subject.setConnectedGroups(result);
    return result;
  }

  @Override
  public User changeEmail(User user, String email) {
    user.setEmail(email);
    user = saveUser(user);
    SECURITY_LOG.info("{} [{}] changed email to  {}", user.getFullName(), user.getId(), email);
    return user;
  }

  /*
   * for testing
   */
  @Override
  public User getUserByUsernameNoSession(String userName) {
    return userDao.getUserByUserName(userName);
  }

  @Override
  public User setAsPrivateProfile(boolean newValue, User user) {
    if (user.isPrivateProfile() != newValue) {
      user.setPrivateProfile(newValue);
      return userDao.save(user);
    }
    return user;
  }

  @Override
  public UserView getUserViewByUsername(String username) {
    return userDao.getUserViewByUsername(username);
  }

  @Override
  public List<UserView> getAllUsersView() {
    return userDao.getAllUsersView();
  }

  @Override
  @Cacheable(value = "com.researchspace.model.User.fullName")
  public String getFullNameByUsername(String username) {
    return getUserByUsername(username).getFullName();
  }

  @Override
  public List<UserRoleView> getAllUsersViewWithRoles() {
    return userDao.getUsersBasicInfoWithRoles();
  }

  @Override
  public UserAccountEvent saveUserAccountEvent(UserAccountEvent event) {
    return accountEventDao.save(event);
  }

  @Override
  public List<UserAccountEvent> getAccountEventsForUser(User toQuery) {
    return accountEventDao.getAccountEventsForUser(toQuery);
  }

  @Override
  public User getOriginalUserForOperateAs(User subject) {
    String originalUsername =
        IActiveUserStrategy.CHECK_OPERATE_AS.getOriginalUser(subject.getUsername());
    if (!originalUsername.equals(subject.getUsername())) {
      subject = getUserByUsername(originalUsername);
      if (!subject.hasAdminRole()) {
        throw new IllegalStateException(
            format(
                "User %s is operating as another user but is not an admin!",
                subject.getUniqueName()));
      }
    }
    return subject;
  }
}
