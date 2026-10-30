package com.researchspace.api.v1.controller;

import com.researchspace.core.testutil.JakartaValidatorTest;
import org.junit.jupiter.api.Test;

public class ApiFormSearchConfigTest extends JakartaValidatorTest {

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

  @Test
  public void cjkQueryIsValid() {
    ApiFormSearchConfig cfg = new ApiFormSearchConfig();
    cfg.setQuery("細胞");
    assertNErrors(cfg, 0);
    // the two-consecutive-characters rule still applies in any script
    cfg.setQuery("細 胞");
    assertNErrors(cfg, 1);
  }
}
