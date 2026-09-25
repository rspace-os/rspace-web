package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.util.MultiValueMap;

public class ApiACtivitySrchConfigTest {

  @Test
  public void testToMap() {
    ApiActivitySrchConfig cfg = new ApiActivitySrchConfig();
    LocalDate from = LocalDate.of(2000, Month.APRIL, 12);
    cfg.setDateFrom(Date.from(from.atStartOfDay(ZoneId.systemDefault()).toInstant()));
    LocalDate to = LocalDate.of(2001, Month.MAY, 13);
    cfg.setDateTo(Date.from(to.atStartOfDay(ZoneId.systemDefault()).toInstant()));

    MultiValueMap<String, String> asMap = cfg.toMap();
    assertEquals("2000-04-12", asMap.get("dateFrom").get(0));
    assertEquals("2001-05-13", asMap.get("dateTo").get(0));
  }
}
