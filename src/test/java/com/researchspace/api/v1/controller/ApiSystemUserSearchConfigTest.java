package com.researchspace.api.v1.controller;

import static org.junit.Assert.*;

import java.time.LocalDate;
import org.junit.Test;

public class ApiSystemUserSearchConfigTest {

  @Test
  public void tempUsersOnlyByDefault() {
    ApiSystemUserSearchConfig cfg = new ApiSystemUserSearchConfig();
    assertEquals(0, cfg.toMap().size());

    cfg.setCreatedBefore(LocalDate.now().minusDays(5));
    cfg.setLastLoginBefore(LocalDate.now().minusDays(5));
    cfg.setTempAccountsOnly(false);
    assertEquals(3, cfg.toMap().size()); // no other options configured by default
  }
}
