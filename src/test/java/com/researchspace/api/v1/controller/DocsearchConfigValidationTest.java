package com.researchspace.api.v1.controller;

import static com.researchspace.api.v1.controller.DocumentApiPaginationCriteria.FAVORITE_PARAM;
import static com.researchspace.api.v1.controller.DocumentApiPaginationCriteria.SHARED_WITH_ME_PARAM;

import com.researchspace.core.testutil.JavaxValidatorTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DocsearchConfigValidationTest extends JavaxValidatorTest {

  ApiDocSearchConfig srchConfig;

  @Before
  public void setUp() throws Exception {
    srchConfig = new ApiDocSearchConfig();
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testFilterValidation() {
    assertNErrors(srchConfig, 0);
    srchConfig.setFilter(FAVORITE_PARAM);
    assertNErrors(srchConfig, 0);
    srchConfig.setFilter(SHARED_WITH_ME_PARAM);
    assertNErrors(srchConfig, 0);
    srchConfig.setFilter("xxx");
    assertNErrors(srchConfig, 1);
  }
}
