package com.researchspace.testutils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.test.context.TestPropertySource;

/**
 * Adds in an additional property file that overrides the default 'dev' properties to set Spring to
 * wire up classes in a 'Community' deployment way.
 *
 * <p>This should be used to annotate a Spring-based test class inheriting from {@link
 * BaseManagerTestCaseBase}.
 *
 * <p>All the tests in the test class annotated with this annotation should run using 'community'
 * configuration - i.e., tests between the 2 product types should not be mixed.
 */
@TestPropertySource(properties = {"deployment.cloud=true", "deployment.standalone=true"})
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface CommunityTestContext {}
