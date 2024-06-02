package com.researchspace.dao.customliquibaseupdates;

import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;
import static org.junit.Assert.fail;

import liquibase.exception.CustomPreconditionErrorException;
import liquibase.exception.CustomPreconditionFailedException;
import liquibase.precondition.CustomPrecondition;
import org.junit.Test;

public class IsNotWindowsPreconditionTest {

  @Test
  public void testCheck()
      throws CustomPreconditionFailedException, CustomPreconditionErrorException {
    CustomPrecondition isWin = new IsNotWindowsPrecondition();
    // different test depending on OS of test machine
    if (IS_OS_WINDOWS) {
      try {
        isWin.check(null);
      } catch (CustomPreconditionFailedException e) {
        // expect this
        return;
      }
      fail("is windows, but precondition didn't fail");

    } else {
      isWin.check(null);
      return;
    }
  }
}
