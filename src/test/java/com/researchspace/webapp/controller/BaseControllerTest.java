package com.researchspace.webapp.controller;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Date;
import org.junit.Test;
import org.springframework.http.HttpHeaders;

public class BaseControllerTest {
  // concrete class
  static class BaseControllerTSS extends BaseController {}

  BaseController bc = new BaseControllerTSS();

  @Test
  public void testSetCacheTimeInBrowser() {
    HttpHeaders hdrs = new HttpHeaders();
    Date date = new Date();
    bc.setCacheTimeInBrowser(1000, date, hdrs);
    assertTrue(hdrs.getCacheControl().contains("1000"));
    assertTrue(date.getTime() - hdrs.getLastModified() < 1000); // rounds to
    // second
    bc.setCacheTimeInBrowser(1000, null, hdrs); // null is ok
  }

  @Test
  public void isValidSettingsKey() {
    assertTrue(BaseController.isValidSettingsKey("abc1ADE"));
    assertFalse(BaseController.isValidSettingsKey("javascript:alert(1)"));
  }
}
