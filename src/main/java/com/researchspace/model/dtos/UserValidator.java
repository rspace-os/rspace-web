package com.researchspace.model.dtos;

import static org.apache.commons.lang3.StringUtils.isBlank;

import com.researchspace.model.Organisation;
import com.researchspace.model.User;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.MessageSourceUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/** Validates a User objects' properties that are submitted from a new registration. */
@Component("userValidator")
@Slf4j
public class UserValidator implements Validator {

  /** value returned by field validation method if the field is valid */
  public static final String FIELD_OK = "OK";

  private static final String ERRORS_MAXLENGTH_KEY = "errors.maxlength";
  private static final String ERRORS_REQUIRED_KEY = "errors.required";

  private Pattern userNamePattern = null;

  private static final Pattern RELAXED_USER_CHARS =
      Pattern.compile(User.ALLOWED_USERNAME_CHARS_RELAXED_LENGTH_REGEX);

  private static final Pattern PWD_CHARS = Pattern.compile(User.ALLOWED_PWD_CHARS_REGEX);

  // file containing popular insecure passwords.
  private Resource invalidPasswordFile = new ClassPathResource("invalidPasswords.txt");

  private List<String> invalidPasswords = new ArrayList<String>();

  private IPropertyHolder properties;

  private @Autowired MessageSourceUtils messages;

  @PostConstruct
  public void init() {
    Integer minUserNameLength = properties.getMinUsernameLength();
    // don't blow up regexp with invalid number number
    if (minUserNameLength < 1 || minUserNameLength > User.MAX_UNAME_LENGTH) {
      log.warn("Min username length is an invalid value: [{}], ignoring", minUserNameLength);
      return;
    }
    // replace '6' with minimum length
    String regex =
        StringUtils.replace(
            User.ALLOWED_USERNAME_CHARS_REGEX,
            User.MIN_UNAME_LENGTH + ",",
            minUserNameLength + ",");
    userNamePattern = Pattern.compile(regex);
  }

  /**
   * @param properties the properties to set
   */
  @Autowired
  public void setProperties(IPropertyHolder properties) {
    this.properties = properties;
  }

  @Override
  public boolean supports(Class<?> clazz) {
    return clazz.isAssignableFrom(User.class);
  }

  /** High-level validation of User object, called on sign up. */
  @Override
  public void validate(Object target, Errors errors) {

    User user = (User) target;

    validateUsername(user.getUsername(), errors);
    validateEmail(user.getEmail(), errors);
    if (!properties.isSSO()) {
      validateMainPassword(
          user.getPassword(), user.getConfirmPassword(), user.getUsername(), errors);
      validateConfirmPassword(user.getConfirmPassword(), errors);
    }
    validateFirstName(user.getFirstName(), errors);
    validateLastName(user.getLastName(), errors);
    validateAffiliation(user.getAffiliation(), errors);
    validateSignupCode(user.getSignupCode(), errors);
  }

  private void validateSignupCode(String signupCode, Errors errors) {
    if (!isBlank(properties.getUserSignupCode())
        && !properties.getUserSignupCode().equals(signupCode)) {
      addToErrorsAndGetMessage(errors, "signupCode", "errors.signupcode.failed", null);
    }
  }

  public String validateUsername(String username) {
    return validateUsername(username, null);
  }

  private String validateUsername(String username, Errors errors) {

    if (StringUtils.isBlank(username)) {
      return addToErrorsAndGetMessage(
          errors, "username", ERRORS_REQUIRED_KEY, new Object[] {"Username"});
    }
    if (usernameTooLong(username)) {
      return addToErrorsAndGetMessage(
          errors,
          "username",
          ERRORS_MAXLENGTH_KEY,
          new Object[] {"Username", User.MAX_UNAME_LENGTH});
    }
    if (!usernameHasValidCharactersAndLength(username)) {
      String invalidUsernameMsg = "errors.invalidusername";
      if (shouldRelaxedUsernamePatternBeUsed()) {
        invalidUsernameMsg = "errors.invalidusername.relaxed";
      }
      return addToErrorsAndGetMessage(errors, "username", invalidUsernameMsg, null);
    }

    return FIELD_OK;
  }

  /**
   * @return error message for given errorCode and arguments
   */
  private String addToErrorsAndGetMessage(
      Errors errors, String field, String errorCode, Object[] args) {

    String validationMsg = messages.getMessage(errorCode, args);
    if (errors != null) {
      errors.rejectValue(field, errorCode, args, null);
    }
    return validationMsg;
  }

  private boolean usernameTooLong(String username) {
    return username.length() > User.MAX_UNAME_LENGTH;
  }

  private boolean usernameHasValidCharactersAndLength(String uname) {
    Matcher matcher = null;
    if (shouldRelaxedUsernamePatternBeUsed()) {
      matcher = RELAXED_USER_CHARS.matcher(uname);
    } else {
      matcher = userNamePattern.matcher(uname);
    }
    return matcher.matches();
  }

  @Value("${user.relaxedUsernamePattern.enabled:false}")
  private boolean isRelaxedUsernamePatternEnabled;

  private boolean shouldRelaxedUsernamePatternBeUsed() {
    return !properties.isStandalone()
        || properties.isLdapAuthenticationEnabled()
        || isRelaxedUsernamePatternEnabled;
  }

  private String validateEmail(String email, Errors errors) {

    if (StringUtils.isBlank(email)) {
      return addToErrorsAndGetMessage(errors, "email", ERRORS_REQUIRED_KEY, new Object[] {"Email"});
    }
    if (emailTooLong(email)) {
      return addToErrorsAndGetMessage(
          errors,
          "email",
          ERRORS_MAXLENGTH_KEY,
          new Object[] {"Email", User.DEFAULT_MAXFIELD_LEN + ""});
    }

    return FIELD_OK;
  }

  private boolean emailTooLong(String email) {
    return email.length() > User.DEFAULT_MAXFIELD_LEN;
  }

  /**
   * Performs checks on whether suggested password is a suitable password and matches confirmation
   * password.
   *
   * @param password
   * @param confirmPwd
   * @param username
   * @return
   */
  public String validatePasswords(String password, String confirmPwd, String username) {
    return validatePasswords(password, confirmPwd, username, null);
  }

  /**
   * Performs checks on whether suggested password is a suitable password and matches confirmation
   * password.
   *
   * @param password
   * @param confirmPwd
   * @param username
   * @param errors An {@link Errors} object for Spring MVC form binding
   * @return
   */
  public String validatePasswords(
      String password, String confirmPwd, String username, Errors errors) {
    String passwordValidationMsg = validateMainPassword(password, confirmPwd, username, errors);
    if (!FIELD_OK.equals(passwordValidationMsg)) {
      return passwordValidationMsg;
    }
    String passwordConfirmValidationMsg = validateConfirmPassword(password, errors);
    if (!FIELD_OK.equals(passwordConfirmValidationMsg)) {
      return passwordConfirmValidationMsg;
    }
    return FIELD_OK;
  }

  private String validateMainPassword(
      String password, String confirmPassword, String username, Errors errors) {

    if (StringUtils.isBlank(password)) {
      return addToErrorsAndGetMessage(
          errors, "password", ERRORS_REQUIRED_KEY, new Object[] {"Password"});
    }
    if (passwordTooLong(password)) {
      return addToErrorsAndGetMessage(
          errors,
          "password",
          ERRORS_MAXLENGTH_KEY,
          new Object[] {"Password", User.DEFAULT_MAXFIELD_LEN + ""});
    }
    if (!passwordHasValidCharacters(password)) {
      return addToErrorsAndGetMessage(
          errors, "password", "errors.invalidpwd", new Object[] {User.MIN_PWD_LENGTH});
    }
    if (!passwordIsValid(password)) {
      return addToErrorsAndGetMessage(errors, "password", "errors.password.insecurepassword", null);
    }
    if (passwordEqualsUserName(password, username)) {
      return addToErrorsAndGetMessage(errors, "password", "errors.password.notequalusername", null);
    }
    if (pwdDoesntMatchConfirmPassword(password, confirmPassword)) {
      return addToErrorsAndGetMessage(errors, "password", "errors.password.conflict", null);
    }

    return FIELD_OK;
  }

  private boolean passwordTooLong(String pwd) {
    return pwd.length() > User.DEFAULT_MAXFIELD_LEN;
  }

  private boolean passwordHasValidCharacters(String pword) {
    Matcher matcherPwd = PWD_CHARS.matcher(pword);
    return matcherPwd.matches();
  }

  private boolean passwordIsValid(String password) {
    if (invalidPasswords.isEmpty()) {
      loadInvalidPasswords();
    }
    return !invalidPasswords.contains(password);
  }

  private boolean passwordEqualsUserName(String pword, String username) {
    return pword.equals(username);
  }

  private void loadInvalidPasswords() {
    synchronized (this) {
      try {
        invalidPasswords = IOUtils.readLines(invalidPasswordFile.getInputStream());
      } catch (IOException e) {
        log.error("Error reading invalid password file.", e);
      }
    }
  }

  private String validateConfirmPassword(String confirmPwd, Errors errors) {
    if (StringUtils.isBlank(confirmPwd)) {
      return addToErrorsAndGetMessage(
          errors, "confirmPassword", ERRORS_REQUIRED_KEY, new Object[] {"Confirm password"});
    }
    return FIELD_OK;
  }

  private boolean pwdDoesntMatchConfirmPassword(String password, String confirmPassword) {
    return password != null && !password.equals(confirmPassword);
  }

  protected String validateFirstName(String firstName, Errors errors) {
    if (StringUtils.isBlank(firstName)) {
      return addToErrorsAndGetMessage(
          errors, "firstName", ERRORS_REQUIRED_KEY, new Object[] {"First Name"});
    }
    return FIELD_OK;
  }

  protected String validateLastName(String lastName, Errors errors) {
    if (StringUtils.isBlank(lastName)) {
      return addToErrorsAndGetMessage(
          errors, "lastName", ERRORS_REQUIRED_KEY, new Object[] {"Last Name"});
    }
    return FIELD_OK;
  }

  protected String validateAffiliation(String affiliation, Errors errors) {
    if (properties != null && properties.isCloud()) {
      if (StringUtils.isBlank(affiliation)) {
        return addToErrorsAndGetMessage(
            errors, "affiliation", ERRORS_REQUIRED_KEY, new Object[] {"Affiliation"});
        // rspac-932
      } else if (!StringUtils.isBlank(affiliation)
          && affiliation.length() > Organisation.MAX_INDEXABLE_UTF_LENGTH) {
        return addToErrorsAndGetMessage(
            errors,
            "affiliation",
            "errors.maxlength",
            new String[] {"affiliation", "" + Organisation.MAX_INDEXABLE_UTF_LENGTH});
      }
    }
    return FIELD_OK;
  }

  /*
   * for testing
   */
  public void setMessages(MessageSourceUtils messageSourceUtils) {
    this.messages = messageSourceUtils;
  }
}
