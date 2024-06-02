package com.researchspace.testutils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/** Custom configuration for testing beans that are normally only created in 'run' profile. */
@ContextConfiguration(
    locations = {
      "classpath:applicationContext-resources.xml",
      "classpath:applicationContext-dao.xml",
      "classpath:applicationContext-test-service.xml",
      "classpath:dispatcher-test-servlet.xml"
    })
@ActiveProfiles(profiles = "run")
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface RunProfileTestConfiguration {}
