package com.researchspace.core.testutil;

import org.mockito.AdditionalMatchers;
import org.mockito.Mockito;
import org.slf4j.Logger;
/**
 * Mockito utils to work with asertions for {@link Logger} calls that 
 *  are mocked by Mockito.
 *  <em/>
 *   Usually this is used to inspect logging calls made by the DevEmailSender
 *  for functionality that generates email content
 *
 */
public class MockLoggingUtils {
	
	public static void assertNoLogging(Logger logger) {
		Mockito.verify(logger, Mockito.never()).info(Mockito.anyString());
	}
	public static void assertLoggerCalledVelocityVariablesReplaced(Logger logger, String messagePart) {
		Mockito.verify(logger).info(AdditionalMatchers.and(
				AdditionalMatchers.not(Mockito.contains("$")),
				Mockito.contains("Hello"))); // some content
	}

}
