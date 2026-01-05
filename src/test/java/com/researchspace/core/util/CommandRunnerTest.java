package com.researchspace.core.util;

import static org.junit.Assert.*;

import java.io.IOException;

import org.apache.commons.exec.ExecuteException;
import org.apache.commons.lang3.SystemUtils;
import org.junit.Assume;
import org.junit.Test;
/**
 * For non-win environments.
 */
public class CommandRunnerTest {

	@Test
	public void testRunCommanLine() throws ExecuteException, IOException {
		Assume.assumeFalse(isWin());

		String listing = new CommandLineRunner().runCommandReturningOutput("ls -l");
		assertTrue(listing.length() > 0);

		listing = new CommandLineRunner().runCommandReturningOutput("ls", "-l");
		assertTrue(listing.length() > 0);
	}
	
	@Test
	public void testRunCommandLineReturningCode() throws ExecuteException, IOException {
		Assume.assumeFalse(isWin());

		int rc  = new CommandLineRunner().runCommandReturningExitStatus("ls -l", 2000);
		assertEquals("Expected return code 0 but was "+ rc, 0, rc);
	}

	private boolean isWin() {
		return SystemUtils.IS_OS_WINDOWS;
	}
}
