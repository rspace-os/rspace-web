package com.researchspace.dao.customliquibaseupdates;

import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;
import static org.junit.Assert.fail;

import liquibase.exception.CustomPreconditionErrorException;
import liquibase.exception.CustomPreconditionFailedException;
import liquibase.precondition.CustomPrecondition;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class IsWindowsPreconditionTest {

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testCheck()
      throws CustomPreconditionFailedException, CustomPreconditionErrorException {
    CustomPrecondition isWin = new IsWindowsPrecondition();
    // different test depending on OS of test machine
    if (IS_OS_WINDOWS) {
      isWin.check(null);
      return;
    } else {
      try {
        isWin.check(null);
      } catch (CustomPreconditionFailedException e) {
        // expect this
        return;
      }
      fail("not windows, but precondition didn't fail");
    }
  }
}
