package com.researchspace.api.v1.config;

import com.axiope.service.cfg.DatabaseConfig;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(classes = {ProdAPIConfig.class, DatabaseConfig.class})
@ActiveProfiles(profiles = "prod")
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface APIProdProfileTestConfig {}
