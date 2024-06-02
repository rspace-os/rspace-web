package com.axiope.userimport;

import com.researchspace.Constants;
import com.researchspace.model.Role;
import com.researchspace.model.dto.UserRegistrationInfo;
import com.researchspace.properties.IPropertyHolder;
import java.util.Set;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

@Component
public class UserLineParserFromCSV {

  private static final int EXPECTED_ELEMENTS_SSO = 5;
  private static final int EXPECTED_ELEMENTS_STANDALONE = 6;
  private static final int EXPECTED_ELEMENTS_CLOUD = 7;

  @Autowired private MessageSource messages;

  @Autowired private IPropertyHolder properties;

  @Autowired private UserNameCreationStrategy unamecreationStrategy;

  /**
   * populates userRegInfo object passed as a parameter with details from CSV line
   *
   * @return error message or null if parsed without errors
   */
  public String populateUserInfo(
      UserRegistrationInfo userRegInfo, String line, int lineNumber, Set<String> seenUsernames) {

    String[] elements = line.split(",", -1);

    int expectedLength = getExpectedNumberOfElements();
    if (elements.length != expectedLength) {
      return createWrongNumberOfElementsMessage(line, lineNumber, expectedLength, elements.length);
    }

    int fieldNo = 0;
    String firstName = elements[fieldNo++];
    String lastName = elements[fieldNo++];
    String email = elements[fieldNo++];

    String affiliation = null;
    if (properties.isCloud()) {
      affiliation = elements[fieldNo++];
    }

    String role = elements[fieldNo++];
    String username = elements[fieldNo++];

    String password = null;
    if (!properties.isSSO()) {
      password = elements[fieldNo++];
    }

    // role provided but unrecognised
    if (!StringUtils.isBlank(role) && !Role.isRoleStringIdentifiable(role.trim())) {
      return createInvalidRoleMsg(line, lineNumber, role);
    }

    if (firstName != null) {
      userRegInfo.setFirstName(firstName.trim());
    }
    if (lastName != null) {
      userRegInfo.setLastName(lastName.trim());
    }
    if (email != null) {
      userRegInfo.setEmail(email.trim());
    }
    if (affiliation != null) {
      userRegInfo.setAffiliation(affiliation.trim());
    }

    if (StringUtils.isBlank(role)) {
      userRegInfo.setRole(Constants.USER_ROLE);
    } else {
      userRegInfo.setRole(role.trim());
    }

    if (StringUtils.isBlank(username)) {
      boolean usernameOk =
          unamecreationStrategy.createUserName(username, userRegInfo, seenUsernames);
      if (!usernameOk) {
        return createNoUsernameErrorMsg(line, lineNumber);
      }
    } else {
      userRegInfo.setUsername(username.trim());
      seenUsernames.add(username.trim());
    }

    if (!properties.isSSO()) {
      String createdPassword = createPassword(password, userRegInfo);
      userRegInfo.setPassword(createdPassword);
      userRegInfo.setConfirmPassword(createdPassword);
    }

    return null;
  }

  private int getExpectedNumberOfElements() {
    if (properties.isCloud()) {
      return EXPECTED_ELEMENTS_CLOUD;
    }
    if (properties.isSSO()) {
      return EXPECTED_ELEMENTS_SSO;
    }
    return EXPECTED_ELEMENTS_STANDALONE;
  }

  private String createPassword(String pwd, UserRegistrationInfo userRegInfo) {
    if (!StringUtils.isBlank(pwd)) {
      return pwd;
    }
    int suffix = 0;
    for (char c : userRegInfo.getUsername().toCharArray()) {
      suffix += c;
    }
    return userRegInfo.getUsername() + "?" + suffix;
  }

  private String createNoUsernameErrorMsg(String line, int lineNumber) {
    return messages.getMessage(
        "system.csvimport.user.nousername", new Object[] {line, lineNumber}, null);
  }

  private String createWrongNumberOfElementsMessage(
      String line, int lineNumber, int expected, int actual) {
    return messages.getMessage(
        "system.csvimport.user.wrongNumberOfFields",
        new Object[] {line, lineNumber, expected, actual},
        null);
  }

  private String createInvalidRoleMsg(String line, int lineNumber, String role) {
    return messages.getMessage(
        "system.csvimport.user.unrecognisedRole",
        new Object[] {line, lineNumber, role, StringUtils.join(Role.getValidRoles())},
        null);
  }

  /*
   * =====================
   *  for tests
   * ====================
   */

  public void setMessages(MessageSource messages) {
    this.messages = messages;
  }

  public void setProperties(IPropertyHolder properties) {
    this.properties = properties;
  }

  public void setUnamecreationStrategy(UserNameCreationStrategy unamecreationStrategy) {
    this.unamecreationStrategy = unamecreationStrategy;
  }
}
