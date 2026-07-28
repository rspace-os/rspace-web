package com.researchspace.api.v1.controller;

import static com.researchspace.api.v1.controller.DocumentApiPaginationCriteria.FAVORITE_PARAM;
import static com.researchspace.api.v1.controller.DocumentApiPaginationCriteria.SHARED_WITH_ME_PARAM;

import com.researchspace.core.testutilJU5.JakartaValidatorTestJU5;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DocsearchConfigValidationTest extends JakartaValidatorTestJU5 {

  ApiDocSearchConfig srchConfig;

  @BeforeEach
  public void setUp() throws Exception {
    srchConfig = new ApiDocSearchConfig();
  }

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
