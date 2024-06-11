package com.researchspace.ldap.impl;

import static com.researchspace.webapp.filter.RemoteUserRetrievalPolicy.SSO_DUMMY_PASSWORD;
import static org.springframework.ldap.query.LdapQueryBuilder.query;

import com.researchspace.Constants;
import com.researchspace.ldap.UserLdapRepo;
import com.researchspace.licensews.LicenseExceededException;
import com.researchspace.licensews.LicenseServerUnavailableException;
import com.researchspace.model.Role;
import com.researchspace.model.SignupSource;
import com.researchspace.model.User;
import com.researchspace.model.dtos.UserValidator;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.EmailBroadcast;
import com.researchspace.service.ISignupHandlerPolicy;
import com.researchspace.service.LicenseRequestResult;
import com.researchspace.service.LicenseService;
import com.researchspace.service.UserExistsException;
import com.researchspace.service.UserManager;
import com.researchspace.service.UserSignupException;
import java.util.List;
import javax.naming.directory.DirContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.ldap.support.LdapUtils;
import org.springframework.stereotype.Component;

/** Implementing LDAP methods, based on LDAP structure as used in UoE. */
@Component
@Slf4j
public class UserLdapRepoImpl implements UserLdapRepo {

  private @Autowired LdapContextSource ldapContext;
  private @Autowired LdapTemplate ldapTemplate;
  private @Autowired IPropertyHolder properties;
  private @Autowired LicenseService licenseService;
  private @Autowired UserManager userManager;
  private @Autowired UserValidator userValidator;

  @Value("${ldap.userSearchQuery.uidField}")
  private String ldapSearchQueryUidField;

  @Value("${ldap.userSearchQuery.dnField}")
  private String ldapSearchQueryDnField;

  @Value("${ldap.userSearchQuery.objectSidField}")
  private String ldapSearchObjectSidField;

  @Value("${ldap.baseSuffix}")
  private String ldapBaseSuffix;

  @Value("${ldap.url}")
  private String ldapUrl;

  @Value("${ldap.fallbackDnCalculationEnabled}")
  private String fallbackDnCalculationEnabled;

  @Autowired
  @Qualifier("manualPolicy")
  private ISignupHandlerPolicy manualSignupPolicy;

  @Override
  public User findUserByUsername(String username) {
    assertLdapEnabled();

    log.info("searching for ldap user '{}'", username);

    List<User> foundUsers = null;
    try {
      foundUsers =
          ldapTemplate.search(
              query().where(ldapSearchQueryUidField).is(username), getUserAttributeMapper());

    } catch (Exception e) {
      log.warn("exception on executing ldap search", e);
      throw e;
    }

    if (foundUsers == null || foundUsers.size() == 0) {
      log.info("user {} not found in ldap", username);
      return null;
    }
    log.info("Found {} user(s) with username {}", foundUsers.size(), username);
    if (foundUsers.size() > 1) {
      throw new IllegalArgumentException("found more than one user for uid=" + username);
    }
    User foundUser = foundUsers.get(0);

    // if fallback mechanism is enabled, then use it
    Boolean fallbackDnCalculation = Boolean.valueOf(fallbackDnCalculationEnabled);
    if (fallbackDnCalculation) {
      String ldapSearchFoundDn = getLdapSearchCmdLineExecutor().findDnForUid(username);
      foundUser.setToken(ldapSearchFoundDn);
    }

    // if user token is unavailable, log the warning
    if (StringUtils.isBlank(foundUser.getToken())) {
      log.warn(
          "dn not found for {}: 'attrs.get(\"{}\")', fallbackDnCalculation flag is {}",
          username,
          ldapSearchQueryDnField,
          fallbackDnCalculation);
    }
    return foundUser;
  }

  private UserAttributeMapper getUserAttributeMapper() {
    return new UserAttributeMapper(
        ldapSearchQueryUidField, ldapSearchQueryDnField, ldapSearchObjectSidField);
  }

  private LdapSearchCmdLineExecutor getLdapSearchCmdLineExecutor() {
    return new LdapSearchCmdLineExecutor(ldapBaseSuffix, ldapUrl, ldapSearchQueryDnField);
  }

  @Override
  public User authenticate(String username, String credentials) {
    assertLdapEnabled();
    assertLdapAuthenticationEnabled();

    User user = findUserByUsername(username);
    if (user == null) {
      log.info("no ldap user found for username {}", username);
      return null;
    }

    String userFullDn = user.getToken();
    user.setToken(null); // reset field, as it's not holding a real token

    if (userFullDn == null) {
      log.warn("full dn not found - LDAP authentication cannot proceed");
      return null;
    }

    DirContext ctx = null;
    try {
      ctx = ldapContext.getContext(userFullDn, credentials);
      log.info("ldap credentials correct for ldap user: " + userFullDn);

    } catch (Exception e) {
      if (StringUtils.contains(e.getMessage(), "Invalid Credentials")) {
        log.info("invalid credentials for ldap user: " + userFullDn);
      } else {
        log.warn("exception on retrieving context for ldap user: " + userFullDn, e);
      }
      return null;
    } finally {
      LdapUtils.closeContext(ctx);
    }

    return user;
  }

  @Override
  public User signupLdapUser(User user) throws UserSignupException {
    assertLdapEnabled();
    assertLdapAuthenticationEnabled();

    log.info("signing up ldap user: " + user.getUsername());

    // check that username is valid
    String username = user.getUsername();
    String usernameValidationResult = userValidator.validateUsername(username);
    if (!UserValidator.FIELD_OK.equals(usernameValidationResult)) {
      throw new UserSignupException(
          "LDAP Username '"
              + username
              + "' cannot be used "
              + "as RSpace username: "
              + usernameValidationResult);
    }

    if (StringUtils.isEmpty(user.getFirstName())) {
      user.setFirstName("-");
    }
    if (StringUtils.isEmpty(user.getLastName())) {
      user.setLastName("-");
    }

    // check that email is provided and valid
    String email = user.getEmail();
    if (StringUtils.isEmpty(email)) {
      user.setEmail(username + EmailBroadcast.UNKNOWN_EMAIL_SUFFIX);
    }
    try {
      userManager.checkEmailUnique(email);
    } catch (UserExistsException e) {
      throw new UserSignupException("Email not unique: " + email, e);
    }

    // check that license is OK BEFORE saving the user.
    LicenseRequestResult result = licenseService.requestUserLicenses(1, Role.USER_ROLE);
    if (result.isLicenseServerAvailable() && !result.isRequestOK()) {
      throw new UserSignupException(
          new LicenseExceededException(
              "There are insufficient license seats to signup "
                  + "new user, please contact your System Admin."));
    } else if (!result.isLicenseServerAvailable()) {
      throw new UserSignupException(
          new LicenseServerUnavailableException(
              "Sorry, new users cannot be created right now, please try again later."));
    }

    // save the user
    user.setRole(Constants.USER_ROLE);
    user.setSignupSource(SignupSource.LDAP);
    user.setPassword(SSO_DUMMY_PASSWORD);
    user.setConfirmPassword(SSO_DUMMY_PASSWORD);

    User signedUser;
    try {
      signedUser = manualSignupPolicy.saveUser(user, null);
    } catch (UserExistsException e) {
      throw new UserSignupException(e);
    }

    return signedUser;
  }

  @Override
  public String retrieveSidForLdapUser(String username) {
    User dbUser = userManager.getUserByUsernameOrAlias(username);
    if (dbUser == null) {
      log.info("User {} not found", username);
      throw new IllegalArgumentException("asked for SID retrieval for unknown user: " + username);
    }
    if (!SignupSource.LDAP.equals(dbUser.getSignupSource())) {
      throw new IllegalArgumentException("asked for SID retrieval for non-ldap user: " + username);
    }
    if (StringUtils.isNotBlank(dbUser.getSid())) {
      log.info("User {} already has SID: {}", username, dbUser.getSid());
      throw new IllegalArgumentException("asked for SID retrieval for user with SID: " + username);
    }

    User ldapUser = findUserByUsername(username);
    if (ldapUser == null) {
      log.info("User {} not found in LDAP", username);
      return null;
    }
    String foundSID = ldapUser.getSid();
    dbUser.setSid(foundSID);
    userManager.save(dbUser);
    log.info("Retrieved user {}, new SID is: {}", username, foundSID);

    return foundSID;
  }

  private void assertLdapEnabled() {
    if (!Boolean.parseBoolean(properties.getLdapEnabled())) {
      throw new IllegalStateException(
          "Calling LDAP method, but LDAP not configured in " + "deployment.properties.");
    }
  }

  private void assertLdapAuthenticationEnabled() {
    if (!properties.isLdapAuthenticationEnabled()) {
      throw new IllegalStateException(
          "Calling LDAP authentication method, but it's not "
              + "configured in deployment.properties.");
    }
  }
}
