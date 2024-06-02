package com.researchspace.model.dtos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.Community;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

public class CommunityValidatorTest {

  private CommunityValidator validator;

  @Before
  public void setUp() throws Exception {
    validator = new CommunityValidator();
  }

  @Test
  public void testSupports() {
    assertTrue(validator.supports(Community.class));
  }

  @Test
  public void testValidate() {
    Community comm = new Community();
    Errors errors = setUpErrorsObject(comm);
    validator.validate(comm, errors);
    assertTrue(errors.hasErrors());
    final int INITIAL_ERRORCOUNT = errors.getErrorCount();

    // now set in valid values and the error count decreases
    Errors errors2 = setUpErrorsObject(comm);
    comm.setDisplayName("display");
    validator.validate(comm, errors2);
    assertEquals(INITIAL_ERRORCOUNT - 1, errors2.getErrorCount());

    Errors errors3 = setUpErrorsObject(comm);
    comm.setUniqueName("unique");
    validator.validate(comm, errors3);
    assertEquals(INITIAL_ERRORCOUNT - 2, errors3.getErrorCount());

    Errors errors4 = setUpErrorsObject(comm);
    comm.setAdminIds(Arrays.asList(new Long[] {1L}));
    validator.validate(comm, errors4);
    assertEquals(INITIAL_ERRORCOUNT - 3, errors4.getErrorCount());
  }

  private BeanPropertyBindingResult setUpErrorsObject(Community comm) {
    return new BeanPropertyBindingResult(comm, "MyObject");
  }
}
