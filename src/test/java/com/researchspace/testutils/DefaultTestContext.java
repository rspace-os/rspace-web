package com.researchspace.testutils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/**
 * Default test Spring context that will run unit tests in the standard way, wiring up Spring beans
 * for the 'standalone' Enterprise functionality in 'dev' profile
 */
@ContextConfiguration(
    locations = {
      "classpath:applicationContext-resources.xml",
      "classpath:applicationContext-dao.xml",
      "classpath:applicationContext-test-service.xml"
    })
@ActiveProfiles(profiles = "dev")
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface DefaultTestContext {}
