package com.researchspace.webapp.controller.cloud;

import static org.apache.commons.lang.RandomStringUtils.randomAlphanumeric;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.dtos.CreateCloudGroup;
import com.researchspace.model.dtos.CreateCloudGroupValidator;
import com.researchspace.model.dtos.ValidationTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

public class CreateCloudGroupValidatorTest extends SpringTransactionalTest {

  private CreateCloudGroup createCloudGroup;
  private @Autowired CreateCloudGroupValidator createCloudGroupValidator;

  @Before
  public void setUp() throws Exception {
    createCloudGroup = new CreateCloudGroup();
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testValidate() {

    final int maxEmailLength = 255;
    final int normalEmailLength = 10;

    User loginUser = createAndSaveUserIfNotExists("testUser");
    logoutAndLoginAs(loginUser);
    createCloudGroup.setSessionUser(loginUser);
    createCloudGroup.setGroupName("");
    Errors errors = setUpErrorsObject();
    createCloudGroupValidator.validate(createCloudGroup, errors);
    assertTrue(errors.hasFieldErrors());
    assertTrue(ValidationTestUtils.hasError("groupName.required", errors));

    createCloudGroup.setGroupName("testGroup");

    createCloudGroup.setEmails(new String[] {""});
    // session user = pi, nominated=false
    createCloudGroup.setPiEmail(loginUser.getEmail());
    errors = setUpErrorsObject();
    createCloudGroupValidator.validate(createCloudGroup, errors);
    assertFalse(errors.hasFieldErrors());

    // invalid email syntax
    createCloudGroup.setEmails(new String[] {"user@mail.com", "user2@m<>ail"});
    errors = setUpErrorsObject();
    createCloudGroupValidator.validate(createCloudGroup, errors);
    assertTrue(errors.hasFieldErrors());
    assertTrue(ValidationTestUtils.hasError("emails.incorrect", errors));

    // too long
    createCloudGroup.setEmails(new String[] {randomAlphanumeric(maxEmailLength) + "@mail.com"});
    errors = setUpErrorsObject();
    createCloudGroupValidator.validate(createCloudGroup, errors);
    assertTrue(errors.hasFieldErrors());
    assertTrue(ValidationTestUtils.hasError("emails.incorrect", errors));

    // invalid syntax
    createCloudGroup.setEmails(new String[] {randomAlphanumeric(normalEmailLength)});
    errors = setUpErrorsObject();
    createCloudGroupValidator.validate(createCloudGroup, errors);
    assertTrue(errors.hasFieldErrors());
    assertTrue(ValidationTestUtils.hasError("emails.incorrect", errors));

    // login user should not be in invitee list
    createCloudGroup.setEmails(new String[] {loginUser.getEmail()});
    errors = setUpErrorsObject();
    createCloudGroupValidator.validate(createCloudGroup, errors);
    assertTrue(errors.hasFieldErrors());
    assertTrue(ValidationTestUtils.hasError("emails.invalid", errors));

    createCloudGroup.setEmails(
        new String[] {"user@mail.com", "user2@mail.com", "user3@mail.com", "user4@mail.com"});
    errors = setUpErrorsObject();
    createCloudGroupValidator.validate(createCloudGroup, errors);
    assertFalse(errors.hasFieldErrors());

    errors = setUpErrorsObject();
    // PI email should be set
    createCloudGroup.setPiEmail(null);
    createCloudGroupValidator.validate(createCloudGroup, errors);
    assertTrue(errors.hasFieldErrors());
    assertTrue(ValidationTestUtils.hasError("principalEmail.required", errors));
    // another user = pi, therefore is nominated
    createCloudGroup.setPiEmail(randomAlphanumeric(maxEmailLength) + "@mail.com");
    errors = setUpErrorsObject();
    createCloudGroupValidator.validate(createCloudGroup, errors);
    assertTrue(errors.hasFieldErrors());
    assertTrue(ValidationTestUtils.hasError("principalEmail.incorrect", errors));

    // pi email too long
    createCloudGroup.setPiEmail(randomAlphanumeric(normalEmailLength));
    errors = setUpErrorsObject();
    createCloudGroupValidator.validate(createCloudGroup, errors);
    assertTrue(errors.hasFieldErrors());
    assertTrue(ValidationTestUtils.hasError("principalEmail.incorrect", errors));

    createCloudGroup.setPiEmail(randomAlphanumeric(normalEmailLength) + "@.mail.com");
    errors = setUpErrorsObject();
    createCloudGroupValidator.validate(createCloudGroup, errors);
    assertFalse(errors.hasFieldErrors());

    createCloudGroup.setPiEmail(loginUser.getEmail());
    errors = setUpErrorsObject();
    createCloudGroupValidator.validate(createCloudGroup, errors);
    assertFalse(errors.hasFieldErrors());

    createCloudGroup.setPiEmail("principal@mail.com");
    createCloudGroup.setEmails(new String[] {});
    errors = setUpErrorsObject();
    createCloudGroupValidator.validate(createCloudGroup, errors);
    assertFalse(errors.hasFieldErrors());
  }

  private BeanPropertyBindingResult setUpErrorsObject() {
    return new BeanPropertyBindingResult(createCloudGroup, "MyObject");
  }
}
