package com.researchspace.testutils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.test.context.TestPropertySource;

/**
 * Adds in an additional property file that overrides the default 'dev' properties to set Spring to
 * wire up classes in a 'SSO' deployment way.
 *
 * <p>This should be used to annotate a Spring-based test class inheriting from {@link
 * BaseManagerTestCaseBase}.
 *
 * <p>All the tests in the test class annotated with this annotation should run using 'SSO'
 * configuration - i.e., tests using SSO and non-SSO should not be mixed in the same test class.
 */
@TestPropertySource(properties = {"deployment.standalone=false"})
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface SSOTestContext {}
