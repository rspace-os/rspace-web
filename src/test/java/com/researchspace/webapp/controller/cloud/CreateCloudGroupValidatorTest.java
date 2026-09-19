package com.researchspace.webapp.controller.cloud;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.dtos.CreateCloudGroup;
import com.researchspace.model.dtos.CreateCloudGroupValidator;
import com.researchspace.model.dtos.ValidationTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

public class CreateCloudGroupValidatorTest extends SpringTransactionalTest {

  private CreateCloudGroup createCloudGroup;
  private @Autowired CreateCloudGroupValidator createCloudGroupValidator;

  @BeforeEach
  public void setUp() throws Exception {
    createCloudGroup = new CreateCloudGroup();
  }

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
    assertTrue(ValidationTestUtils.hasError("groups.creation.errors.groupNameRequired", errors));

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
    assertTrue(ValidationTestUtils.hasError("groups.creation.errors.memberEmailInvalid", errors));

    // too long
    createCloudGroup.setEmails(new String[] {randomAlphanumeric(maxEmailLength) + "@mail.com"});
    errors = setUpErrorsObject();
    createCloudGroupValidator.validate(createCloudGroup, errors);
    assertTrue(errors.hasFieldErrors());
    assertTrue(ValidationTestUtils.hasError("groups.creation.errors.memberEmailInvalid", errors));

    // invalid syntax
    createCloudGroup.setEmails(new String[] {randomAlphanumeric(normalEmailLength)});
    errors = setUpErrorsObject();
    createCloudGroupValidator.validate(createCloudGroup, errors);
    assertTrue(errors.hasFieldErrors());
    assertTrue(ValidationTestUtils.hasError("groups.creation.errors.memberEmailInvalid", errors));

    // login user should not be in invitee list
    createCloudGroup.setEmails(new String[] {loginUser.getEmail()});
    errors = setUpErrorsObject();
    createCloudGroupValidator.validate(createCloudGroup, errors);
    assertTrue(errors.hasFieldErrors());
    assertTrue(
        ValidationTestUtils.hasError("groups.creation.errors.currentUserAlreadyMember", errors));

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
    assertTrue(ValidationTestUtils.hasError("groups.creation.errors.piEmailRequired", errors));
    // another user = pi, therefore is nominated
    createCloudGroup.setPiEmail(randomAlphanumeric(maxEmailLength) + "@mail.com");
    errors = setUpErrorsObject();
    createCloudGroupValidator.validate(createCloudGroup, errors);
    assertTrue(errors.hasFieldErrors());
    assertTrue(ValidationTestUtils.hasError("groups.creation.errors.piEmailInvalid", errors));

    // pi email too long
    createCloudGroup.setPiEmail(randomAlphanumeric(normalEmailLength));
    errors = setUpErrorsObject();
    createCloudGroupValidator.validate(createCloudGroup, errors);
    assertTrue(errors.hasFieldErrors());
    assertTrue(ValidationTestUtils.hasError("groups.creation.errors.piEmailInvalid", errors));

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
