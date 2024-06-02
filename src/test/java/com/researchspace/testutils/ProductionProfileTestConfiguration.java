package com.researchspace.testutils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

/**
 * Loads production profile classes defined by 'prod' profile. You probably don't want to run real
 * unit tests using this configuration; this annotation is just used so that we can test the wiring
 * of Spring beans and fail early if there's a problem.
 */
@ContextConfiguration(
    locations = {
      "classpath:applicationContext-resources.xml",
      "classpath:applicationContext-dao.xml",
      "file:src/main/webapp/WEB-INF/security.xml",
      "classpath:applicationContext-service.xml",
    })
@TestPropertySource(
    properties = {
      "propertyFileDir=classpath:deployments/dev",
      "startup.skip.inventory.template.creation=true"
    })
@ActiveProfiles(profiles = "prod")
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface ProductionProfileTestConfiguration {}
