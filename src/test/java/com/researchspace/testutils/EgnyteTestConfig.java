package com.researchspace.testutils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.test.context.TestPropertySource;

/**
 * Tests annotated with this annotation will set up a file store to work with Egnyte RSpace test
 * domain at . <br>
 * Test classes will still need to set an access token e.g. from a system property
 */
@TestPropertySource(
    properties = {
      "rs.filestore=EGNYTE",
      "rs.ext.filestore.root=/Shared/RSpaceTestFileStore/TestRoot"
    })
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface EgnyteTestConfig {}
