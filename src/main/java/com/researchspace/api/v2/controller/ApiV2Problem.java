package com.researchspace.api.v2.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** RFC 9457 problem details using the default {@code about:blank} type. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiV2Problem(
    String title, int status, String code, String detail, List<InvalidParam> invalidParams) {

  public ApiV2Problem {
    Objects.requireNonNull(title, "Problem title");
    Objects.requireNonNull(code, "Problem code");
  }

  public record InvalidParam(String name, String reason) {}

  public static final MediaType PROBLEM_JSON = MediaType.valueOf("application/problem+json");

  public static ResponseEntity<ApiV2Problem> response(
      HttpStatus status, String title, String code, String detail) {
    return response(status, title, code, detail, null);
  }

  public static ResponseEntity<ApiV2Problem> response(
      HttpStatus status,
      String title,
      String code,
      String detail,
      List<InvalidParam> invalidParams) {
    return ResponseEntity.status(status)
        .contentType(PROBLEM_JSON)
        .body(new ApiV2Problem(title, status.value(), code, detail, invalidParams));
  }
}
