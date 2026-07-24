package com.researchspace.api.v2.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** RFC 9457 problem details using the default {@code about:blank} type. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiV2Problem(String title, int status, String detail) {

  public static final MediaType PROBLEM_JSON = MediaType.valueOf("application/problem+json");

  public static ResponseEntity<ApiV2Problem> response(HttpStatus status, String detail) {
    return ResponseEntity.status(status)
        .contentType(PROBLEM_JSON)
        .body(new ApiV2Problem(status.getReasonPhrase(), status.value(), detail));
  }
}
