package com.researchspace.api.v2.controller;

import com.researchspace.api.v2.resource.ApiV2ResourceException;
import org.springframework.http.HttpStatus;

/** Strict parsing shared by API-v2 version preconditions. */
final class ApiV2ConditionalRequest {

  private ApiV2ConditionalRequest() {}

  static long parseVersion(String value, String requiredCode) {
    if (value == null) {
      throw ApiV2ResourceException.of(HttpStatus.PRECONDITION_REQUIRED, requiredCode);
    }
    if (value.length() < 3 || value.charAt(0) != '"' || value.charAt(value.length() - 1) != '"') {
      throw new ApiV2BadRequestException("errors.api.v2.invalidRequest");
    }
    try {
      long version = Long.parseLong(value.substring(1, value.length() - 1));
      if (version < 0) {
        throw new NumberFormatException();
      }
      return version;
    } catch (NumberFormatException ex) {
      throw new ApiV2BadRequestException("errors.api.v2.invalidRequest");
    }
  }

  static String parseStrongEtag(String value, String requiredCode) {
    if (value == null) {
      throw ApiV2ResourceException.of(HttpStatus.PRECONDITION_REQUIRED, requiredCode);
    }
    if (value.length() < 3
        || value.charAt(0) != '"'
        || value.charAt(value.length() - 1) != '"'
        || value.substring(1, value.length() - 1).chars().anyMatch(c -> c == '"' || c < 0x21)) {
      throw new ApiV2BadRequestException("errors.api.v2.invalidRequest");
    }
    return value;
  }
}
