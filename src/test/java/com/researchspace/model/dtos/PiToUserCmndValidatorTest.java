package com.researchspace.model.dtos;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.Group;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import java.util.HashSet;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

public class PiToUserCmndValidatorTest {

  private PiToUserCommandValidator validator;
  private User pi;
  private Set<String> activeUsers = new HashSet<String>();

  @Before
  public void setUp() throws Exception {
    pi = TestFactory.createAnyUser("any");
    pi.addRole(Role.PI_ROLE);
    pi.addRole(Role.USER_ROLE);

    validator = new PiToUserCommandValidator(activeUsers, pi);
  }

  @After
  public void tearDown() throws Exception {}

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
    activeUsers.add(pi.getUsername());
    Errors errors2 = setUpErrorsObject(cmnd);
    validator.validate(cmnd, errors2);
    assertTrue(errors2.hasErrors());

    // add to group
    activeUsers.remove(pi.getUsername());
    Group grp = TestFactory.createAnyGroup(pi, null);
    Errors errors3 = setUpErrorsObject(cmnd);
    validator.validate(cmnd, errors3);
    assertTrue(errors3.hasErrors());

    grp.removeMember(pi);
    Errors errors4 = setUpErrorsObject(cmnd);
    // is not a pi
    pi.removeRole(Role.PI_ROLE);
    validator.validate(cmnd, errors4);
    assertTrue(errors4.hasErrors());
  }

  private BeanPropertyBindingResult setUpErrorsObject(UserRoleChangeCmnd comm) {
    return new BeanPropertyBindingResult(comm, "MyObject");
  }
}
