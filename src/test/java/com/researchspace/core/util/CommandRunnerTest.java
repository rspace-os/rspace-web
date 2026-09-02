package com.researchspace.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.lang3.SystemUtils;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** For non-win environments. */
public class CommandRunnerTest {

  @Test
  public void testRunCommanLine() throws ExecuteException, IOException {
    Assumptions.assumeFalse(isWin());

    String listing = new CommandLineRunner().runCommandReturningOutput("ls -l");
    assertTrue(listing.length() > 0);

    listing = new CommandLineRunner().runCommandReturningOutput("ls", "-l");
    assertTrue(listing.length() > 0);
  }

  @Test
  public void testRunCommandLineReturningCode() throws ExecuteException, IOException {
    Assumptions.assumeFalse(isWin());

    int rc = new CommandLineRunner().runCommandReturningExitStatus("ls -l", 2000);
    assertEquals(0, rc, "Expected return code 0 but was " + rc);
  }

  private boolean isWin() {
    return SystemUtils.IS_OS_WINDOWS;
  }
}
