package com.researchspace.model.dtos;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.testutils.TestFactory;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

public class UserPromotionCmndValidatorTest {

  private UserToPiCommandValidator validator;
  private User u;
  private Set<String> activeUsers = new HashSet<String>();

  @BeforeEach
  public void setUp() throws Exception {
    u = TestFactory.createAnyUser("any");
    u.addRole(Role.USER_ROLE);

    validator = new UserToPiCommandValidator(activeUsers, u);
  }

  @Test
  public void testSupports() {
    assertTrue(validator.supports(UserRoleChangeCmnd.class));
  }

  @Test
  public void testValidate() {
    UserRoleChangeCmnd cmnd = new UserRoleChangeCmnd();
    Errors errors = setUpErrorsObject(cmnd);
    validator.validate(cmnd, errors);
    assertFalse(errors.hasErrors());

    // this mimics that user is currently logged in, should not be allowed
    activeUsers.add(u.getUsername());
    Errors errors2 = setUpErrorsObject(cmnd);
    validator.validate(cmnd, errors2);
    assertTrue(errors2.hasErrors());

    activeUsers.remove(u.getUsername());
    Errors errors3 = setUpErrorsObject(cmnd);
    // is now a pi already
    u.addRole(Role.PI_ROLE);
    validator.validate(cmnd, errors3);
    assertTrue(errors3.hasErrors());
  }

  private BeanPropertyBindingResult setUpErrorsObject(UserRoleChangeCmnd comm) {
    return new BeanPropertyBindingResult(comm, "MyObject");
  }
}
