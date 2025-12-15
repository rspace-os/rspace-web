package com.researchspace.service.cloud.impl;

import com.researchspace.Constants;
import com.researchspace.analytics.service.AnalyticsManager;
import com.researchspace.core.util.SecureStringUtils;
import com.researchspace.model.TokenBasedVerification;
import com.researchspace.model.TokenBasedVerificationType;
import com.researchspace.model.User;
import com.researchspace.model.permissions.SecurityLogger;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.EmailBroadcast;
import com.researchspace.service.RoleManager;
import com.researchspace.service.UserManager;
import com.researchspace.service.cloud.CommunityUserManager;
import com.researchspace.service.impl.EmailBroadcastImp.EmailContent;
import com.researchspace.service.impl.StrictEmailContentGenerator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service("cloudUserManager")
@Slf4j
public class CloudUserManagerImpl implements CommunityUserManager {

  private static final Logger SECURITY_LOG = LoggerFactory.getLogger(SecurityLogger.class);

  private @Autowired UserManager userManager;
  private @Autowired StrictEmailContentGenerator strictEmailContentGenerator;
  private @Autowired RoleManager roleManager;
  private @Autowired AnalyticsManager analyticsManager;
  private @Autowired IPropertyHolder properties;

  @Autowired
  @Qualifier("emailBroadcast")
  private EmailBroadcast emailSender;

  @Override
  public boolean checkTempCloudUser(String email) {
    List<User> users = userManager.getUserByEmail(email);
    if (!users.isEmpty()) {
      User tempUser = users.get(0);
      if (tempUser.isTempAccount()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public List<User> createInvitedUserList(List<String> emails) {
    List<User> users = new ArrayList<User>();
    Set<String> emailsNoDups = new HashSet<>(emails);
    for (String email : emailsNoDups) {
      User invitedUser = createInvitedUser(email);
      users.add(invitedUser);
    }
    return users;
  }

  @Override
  public User createInvitedUser(String email) {
    User invitedUser = null;
    List<User> list = userManager.getUserByEmail(email);
    if (!list.isEmpty()) {
      // return existing user (may be temporary) with this email
      invitedUser = list.get(0);
    } else {
      // or create a temporary user
      invitedUser = createTempUser(email);
    }
    return invitedUser;
  }

  @Override
  public User mergeSignupFormWithTempUser(User user, String email) {
    List<User> tempUsers = userManager.getUserByEmail(email);
    if (!tempUsers.isEmpty()) {
      User tempUser = tempUsers.get(0);
      if (tempUser.isTempAccount()) {
        tempUser.setEmail(user.getEmail());
        tempUser.setFirstName(user.getFirstName());
        tempUser.setLastName(user.getLastName());
        tempUser.setPassword(user.getPassword());
        tempUser.setEnabled(user.isEnabled());
        tempUser.setRole(user.getRole());
        tempUser.setAffiliation(user.getAffiliation());
        tempUser.addRole(roleManager.getRole(Constants.USER_ROLE));
        return tempUser;
      }
    }
    return user;
  }

  public List<String> getUsernameList(List<User> users) {
    List<String> list = new ArrayList<String>();
    for (User user : users) {
      list.add(user.getUsername());
    }
    return list;
  }

  @Override
  public User activateUser(String tokenStr) {
    TokenBasedVerification token = userManager.getUserVerificationToken(tokenStr);
    if (token == null) {
      log.error("No token could be retrieved");
      return null;
    }

    List<User> toActivate = userManager.getUserByEmail(token.getEmail());
    if (toActivate.size() == 0) {
      log.error("No user could be identified with the token");
      return null;
    }

    User user = toActivate.get(0);

    if (user.isEnabled() && !user.isTempAccount()) {
      log.warn("Trying to activate user [{}] again (already activated).", user.getUsername());
      userManager.setTokenCompleted(token);
      return null;
    }

    user.addRole(roleManager.getRole(Constants.USER_ROLE));
    user.setEnabled(true);
    user.setTempAccount(false);

    userManager.save(user);
    userManager.setTokenCompleted(token);

    analyticsManager.userCreated(user);

    return user;
  }

  private User createTempUser(String email) {
    String randUsername = email + RandomStringUtils.randomAlphanumeric(4);
    User tempUser = new User();
    tempUser.setUsername(randUsername.toString());
    tempUser.setPassword(SecureStringUtils.getURLSafeSecureRandomString(16));
    tempUser.setFirstName(email);
    tempUser.setLastName(email);
    tempUser.setEmail(email);
    tempUser.setEnabled(false);
    tempUser.setTempAccount(true);
    User result = userManager.save(tempUser);
    return result;
  }

  @Override
  public TokenBasedVerification emailChangeRequested(User user, String email, String remoteAddr) {
    Validate.notEmpty(email, "email cannot be empty!");

    TokenBasedVerification token =
        userManager.createTokenBasedVerificationRequest(
            user, email, remoteAddr, TokenBasedVerificationType.EMAIL_CHANGE);

    sendEmailChangeVerificationMsg(user, email, remoteAddr, token);
    return token;
  }

  @Override
  public boolean emailChangeConfirmed(String tokenStr, User subject) {

    TokenBasedVerification changeEmailToken = userManager.getUserVerificationToken(tokenStr);
    if (changeEmailToken == null) {
      log.warn("Token not found");
      return false;
    }

    String tokenUsername = changeEmailToken.getUser().getUsername();
    if (!tokenUsername.equals(subject.getUsername())) {
      SECURITY_LOG.warn(
          "User [{}] tried to change email using token generated by user [{}]",
          subject.getUsername(),
          tokenUsername);
      return false;
    }

    String newEmail = changeEmailToken.getEmail();
    if (CollectionUtils.isNotEmpty(userManager.getUserByEmail(newEmail))) {
      log.info(
          "User "
              + tokenUsername
              + " tried to change email to "
              + newEmail
              + ", but it's already taken");
      return false;
    }

    User user = userManager.getUserByUsername(tokenUsername);
    String oldEmail = user.getEmail();
    userManager.changeEmail(user, newEmail);

    userManager.setTokenCompleted(changeEmailToken);
    sendEmailChangeConfirmationMsg(user, oldEmail);

    return true;
  }

  private void sendEmailChangeVerificationMsg(
      User user, String email, String remoteAddr, TokenBasedVerification token) {

    Map<String, Object> rc = new HashMap<String, Object>();
    rc.put("firstName", user.getFirstName());
    rc.put("ipAddress", remoteAddr);

    String verifyLink =
        properties.getUrlPrefix() + "/cloud/verifyEmailChange?token=" + token.getToken();
    rc.put("verifyLink", verifyLink);

    EmailContent content =
        strictEmailContentGenerator.generatePlainTextAndHtmlContent(
            "emailChangeVerificationMsg.vm", rc);

    log.info("Sending mail to {} at unverified email: {}", user.getUsername(), email);
    emailSender.sendHtmlEmail(
        "Email address update", content, Arrays.asList(new String[] {email}), null);
  }

  private void sendEmailChangeConfirmationMsg(User user, String oldEmail) {

    Map<String, Object> model = new HashMap<>();
    model.put("firstName", user.getFirstName());
    model.put("newEmailAddress", user.getEmail());

    EmailContent content =
        strictEmailContentGenerator.generatePlainTextAndHtmlContent(
            "emailChangeConfirmationMsg.vm", model);
    log.info("Sending confirmation mail to {} at old email {}", user.getUsername(), oldEmail);
    emailSender.sendHtmlEmail(
        "Email address update", content, Arrays.asList(new String[] {oldEmail}), null);
  }

  /*
   * for testing
   */
  public void setAnalyticsManager(AnalyticsManager analyticsManager) {
    this.analyticsManager = analyticsManager;
  }

  public void setEmailSender(EmailBroadcast emailSender) {
    this.emailSender = emailSender;
  }
}
