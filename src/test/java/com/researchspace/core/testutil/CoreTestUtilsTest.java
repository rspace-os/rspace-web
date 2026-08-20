package com.researchspace.core.testutil;

import static com.researchspace.core.testutil.CoreTestUtils.assertExceptionThrown;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

class CoreTestUtilsTest {

	static class Testclass {
		void doSomething() {
			throw new IllegalArgumentException("message");
		}
	}

	@Test
	void testOK() throws Exception {
		Testclass testclass = new Testclass();
		assertExceptionThrown(testclass::doSomething, IllegalArgumentException.class);
		Matcher<String> matcher = containsString("essage");
		assertExceptionThrown(testclass::doSomething, IllegalArgumentException.class, matcher);
	}

	@Test
	void testExceptionThrown() throws Exception {
		Testclass testclass = new Testclass();
		assertExceptionThrown(testclass::doSomething, IllegalArgumentException.class);
		Matcher<String> matcher = containsString("nomatch");
		assertThrows(AssertionFailedError.class,
				() -> assertExceptionThrown(testclass::doSomething, IllegalArgumentException.class, matcher));

	}

}
