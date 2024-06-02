package com.researchspace.testutils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

/** Custom configuration for testing beans that are normally only created in 'prod' profile. */
@ContextConfiguration(
    locations = {
      "classpath:applicationContext-resources.xml",
      "classpath:applicationContext-dao.xml",
      "classpath:applicationContext-test-service.xml",
    })
@ActiveProfiles(profiles = "prod-test")
@TestPropertySource(properties = {"propertyFileDir=classpath:deployments/dev"})
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface ProdProfileTestConfiguration {}
