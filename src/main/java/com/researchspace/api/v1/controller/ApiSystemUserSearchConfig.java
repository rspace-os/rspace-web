package com.researchspace.api.v1.controller;

import static com.researchspace.api.v1.controller.ApiActivitySrchConfig.YYYY_MM_DD_ISO8601;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@Data
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@NoArgsConstructor
public class ApiSystemUserSearchConfig extends ApiSearchConfig {

  @DateTimeFormat(pattern = YYYY_MM_DD_ISO8601)
  private LocalDate createdBefore;

  @DateTimeFormat(pattern = YYYY_MM_DD_ISO8601)
  private LocalDate lastLoginBefore;

  private boolean tempAccountsOnly = true;

  @Override
  public MultiValueMap<String, String> toMap() {
    LinkedMultiValueMap<String, String> rc = new LinkedMultiValueMap<>();

    if (createdBefore != null) {
      rc.add("createdBefore", createdBefore.format(ISO_LOCAL_DATE));
    }
    if (lastLoginBefore != null) {
      rc.add("lastLoginBefore", lastLoginBefore.format(ISO_LOCAL_DATE));
    }
    // true is the default setting
    if (!tempAccountsOnly) {
      rc.add("tempAccountsOnly", Boolean.toString(tempAccountsOnly));
    }

    return rc;
  }
}
