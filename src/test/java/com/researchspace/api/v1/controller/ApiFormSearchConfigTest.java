package com.researchspace.api.v1.controller;

import com.researchspace.core.testutil.JavaxValidatorTest;
import org.junit.Test;

public class ApiFormSearchConfigTest extends JavaxValidatorTest {

  @Test
  public void searchConfig() {
    ApiFormSearchConfig cfg = new ApiFormSearchConfig();
    assertNErrors(cfg, 0);

    cfg.setQuery("");
    assertNErrors(cfg, 2);
    // must have > 2 letters at least once
    cfg.setQuery("a b d");
    assertNErrors(cfg, 1);
    cfg.setQuery("a term d");
    assertNErrors(cfg, 0);
  }
}
