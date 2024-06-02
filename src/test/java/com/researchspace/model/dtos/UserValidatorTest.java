package com.researchspace.model.dtos;

import static com.researchspace.core.util.TransformerUtils.toList;
import static com.researchspace.model.Organisation.MAX_INDEXABLE_UTF_LENGTH;
import static org.apache.commons.lang.RandomStringUtils.randomAlphabetic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.ProductType;
import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import com.researchspace.properties.IMutablePropertyHolder;
import com.researchspace.properties.PropertyHolder;
import com.researchspace.service.MessageSourceUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

public class UserValidatorTest {

  private User user;

  private UserValidator userValidator = new UserValidator();

  IMutablePropertyHolder properties;

  @BeforeEach
  public void setUp() throws Exception {
    user = TestFactory.createAnyUser("XXXXXX");
    ResourceBundleMessageSource msgSource = new ResourceBundleMessageSource();
    msgSource.setUseCodeAsDefaultMessage(true);
    userValidator.setMessages(new MessageSourceUtils(msgSource));

    properties = new PropertyHolder();
    properties.setStandalone("true");
    properties.setMinUsernameLength(6);
    userValidator.setProperties(properties);
    userValidator.init();
  }

  @Test
  public void testMinUsernameLength() {
    Errors err = setUpErrorsObject();
    properties.setMinUsernameLength(3);
    userValidator.init();
    user.setUsername("abc");
    userValidator.validate(user, err);

    assertNoFieldErrors(err);
    assertFalse(ValidationTestUtils.hasError("errors.required", err));

    user.setUsername("ac");
    userValidator.validate(user, err);
    assertFieldErrors(err);
    assertInvalidUsername(err);
  }

  @ParameterizedTest
  @ValueSource(ints = {-2, -1, 0, User.MAX_UNAME_LENGTH + 1})
  public void initIgnoresInvalidMinUsernameLengths(int minLength) {
    Errors err = setUpErrorsObject();
    // cannot set too big or too small
    properties.setMinUsernameLength(minLength);
    userValidator.init();
    // min length  is unchanged
    user.setUsername(randomAlphabetic(User.MAX_UNAME_LENGTH));
    userValidator.validate(user, err);
    assertNoFieldErrors(err);
    // 5 is still too small, i.e range is still 6 - 50
    user.setUsername("abcde");
    userValidator.validate(user, err);
    assertFieldErrors(err);
  }

  @Test
  public void initIgnoresInvalidTooShortUsernames() {
    Errors err = setUpErrorsObject();
    // cannot set too big
    properties.setMinUsernameLength(User.MAX_UNAME_LENGTH + 1);
    userValidator.init();
    user.setUsername(randomAlphabetic(User.MAX_UNAME_LENGTH));
    userValidator.validate(user, err);

    assertNoFieldErrors(err);
    assertFalse(ValidationTestUtils.hasError("errors.required", err));
  }

  private void assertFieldRequiredError(Errors err) {
    assertTrue(ValidationTestUtils.hasError("errors.required", err));
  }

  @Test
  public void testValidateAffiliation() {
    Errors err = setUpErrorsObject();
    properties.setCloud("true");
    // null affiliation is not allowed
    userValidator.validate(user, err);
    assertFieldErrors(err);
    assertFieldRequiredError(err);

    // setting an affiliation passed validation
    user.setAffiliation("Edinburgh Uni", ProductType.COMMUNITY);
    err = setUpErrorsObject();
    userValidator.validate(user, err);
    assertNoFieldErrors(err);
    // max length limit
    user.setAffiliation(randomAlphabetic(MAX_INDEXABLE_UTF_LENGTH + 1), ProductType.COMMUNITY);
    err = setUpErrorsObject();
    userValidator.validate(user, err);
    assertFieldErrors(err);

    // for non-public cloud, a null affiliation is OK
    err = setUpErrorsObject();
    properties.setCloud("false");
    user.setAffiliation(null, ProductType.STANDALONE);
    userValidator.validate(user, err);
    assertNoFieldErrors(err);
  }

  private void assertFieldErrors(Errors err) {
    assertTrue(err.hasFieldErrors());
  }

  @Test
  public void testValidate() {
    String FORBIDDEN_CHARS = "abcsa_";
    user.setUsername(FORBIDDEN_CHARS);
    Errors err = setUpErrorsObject();
    userValidator.validate(user, err);
    assertFieldErrors(err);
    assertInvalidUsername(err);

    String TOO_SHORT = "12345";
    user.setUsername(TOO_SHORT);
    err = setUpErrorsObject();
    userValidator.validate(user, err);
    assertFieldErrors(err);
    assertInvalidUsername(err);

    err = setUpErrorsObject();
    user.setUsername("OKOKOK");
    userValidator.validate(user, err);
    assertNoFieldErrors(err);

    err = setUpErrorsObject();
    user.setUsername("test@researchspace.com");
    userValidator.validate(user, err);
    assertNoFieldErrors(err);
  }

  private void assertInvalidUsername(Errors err) {
    assertTrue(ValidationTestUtils.hasError("errors.invalidusername", err));
  }

  @Test
  public void testValidateUserNameRelaxed() {
    user.setUsername("u1");
    user.setPassword("Valid123");
    user.setConfirmPassword("Valid123");
    Errors err = setUpErrorsObject();

    // enable cloud mode
    properties.setStandalone("false");

    // accepts short username
    userValidator.validate(user, err);
    assertNoFieldErrors(err);

    // reject non-alphanumeric username with correct message
    user.setUsername("u1!");
    userValidator.validate(user, err);
    assertFieldErrors(err);
    assertFalse(ValidationTestUtils.hasError("errors.invalidusername", err));
    assertTrue(ValidationTestUtils.hasError("errors.invalidusername.relaxed", err));

    // sanity check
    properties.setStandalone("true");
    userValidator.validate(user, err);
    assertInvalidUsername(err);

    String toolong = RandomStringUtils.randomAlphabetic(User.MAX_UNAME_LENGTH + 1);
    user.setUsername(toolong);
    userValidator.validate(user, err);
    assertTrue(ValidationTestUtils.hasError("errors.maxlength", err));
  }

  private void assertNoFieldErrors(Errors err) {
    assertFalse(err.hasFieldErrors());
  }

  @Test
  public void validateUserNameDifferentFromPasswd() {
    user.setUsername("Valid123");
    user.setPassword("Valid123");
    user.setConfirmPassword("Valid123");
    Errors err = setUpErrorsObject();
    userValidator.validate(user, err);
    assertTrue(ValidationTestUtils.hasError("errors.password.notequalusername", err));
  }

  @Test
  public void validateInsecurePasswords() {
    user.setUsername("Valid123");
    user.setPassword("password");
    user.setConfirmPassword("password");
    Errors err = setUpErrorsObject();
    userValidator.validate(user, err);
    assertTrue(ValidationTestUtils.hasError("errors.password.insecurepassword", err));
  }

  @Test
  public void validateTooShortPasswords() {
    user.setUsername("Valid123");
    user.setPassword("2short");
    user.setConfirmPassword("2short");
    Errors err = setUpErrorsObject();
    userValidator.validate(user, err);
    assertTrue(ValidationTestUtils.hasError("errors.invalidpwd", err));
  }

  @Test
  public void validatePassword() {
    assertFalse(userValidator.validatePasswords(null, null, "user").equals(UserValidator.FIELD_OK));
    assertFalse(
        userValidator
            .validatePasswords("xxxxxxxx", "yyyyyyyy", "user")
            .equals(UserValidator.FIELD_OK));

    String toolong = RandomStringUtils.randomAlphabetic(User.DEFAULT_MAXFIELD_LEN + 1);
    assertFalse(
        userValidator.validatePasswords(toolong, toolong, "user").equals(UserValidator.FIELD_OK));

    assertFalse(
        userValidator.validatePasswords("xxxx", "yyyy", "user").equals(UserValidator.FIELD_OK));
    assertFalse(
        userValidator
            .validatePasswords("password", "password", "user")
            .equals(UserValidator.FIELD_OK));
    assertTrue(
        userValidator
            .validatePasswords("goodpwd23", "goodpwd23", "user")
            .equals(UserValidator.FIELD_OK));
  }

  @Test
  public void validateUserSignupCode() {
    Errors err = setUpErrorsObject();
    //		empty values of property  mean that user need not supply the code
    for (String emptyCode : toList(null, "", "  ")) {
      properties.setUserSignupCode(emptyCode);
      userValidator.validate(user, err);
      assertFalse(ValidationTestUtils.hasError("errors.signupcode.failed", err));
    }
    // if it is set, it is required and must be case sensitive
    final String validSignupCode = "trial-123";
    properties.setUserSignupCode(validSignupCode);
    for (String incorrectUserCode : toList(null, "", "Trial", "Trial-123")) {
      user.setSignupCode(incorrectUserCode);
      userValidator.validate(user, err);
      assertTrue(ValidationTestUtils.hasError("errors.signupcode.failed", err));
    }
    // must match precisely
    err = setUpErrorsObject();
    user.setSignupCode(validSignupCode);
    userValidator.validate(user, err);

    assertFalse(ValidationTestUtils.hasError("errors.signupcode.failed", err));
  }

  @Test
  public void sSOUserFromSelfSignup() {
    properties.setStandalone("false");

    // try saving just the username
    Errors err = setUpErrorsObject();
    user = new User();
    user.setUsername("Valid123");
    userValidator.validate(user, err);

    // first name, last name and email required
    assertTrue(err.hasErrors());
    assertEquals(3, err.getErrorCount());

    // fill the other fields
    user.setFirstName("first");
    user.setLastName("last");
    user.setEmail("email@asdf");

    err = setUpErrorsObject();
    userValidator.validate(user, err);
    assertFalse(err.hasErrors());
  }

  private BeanPropertyBindingResult setUpErrorsObject() {
    return new BeanPropertyBindingResult(user, "MyObject");
  }
}
