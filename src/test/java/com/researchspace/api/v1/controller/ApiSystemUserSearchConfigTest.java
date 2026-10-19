package com.researchspace.api.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

public class ApiSystemUserSearchConfigTest {

  @Test
  public void tempUsersOnlyByDefault() {
    ApiSystemUserSearchConfig cfg = new ApiSystemUserSearchConfig();
    assertThat(cfg.toMap()).isEmpty();

    cfg.setCreatedBefore(LocalDate.now().minusDays(5));
    cfg.setLastLoginBefore(LocalDate.now().minusDays(5));
    cfg.setTempAccountsOnly(false);
    assertThat(cfg.toMap()).hasSize(3); // no other options configured by default
  }
}
