package com.researchspace.api.v2.controller;

import com.researchspace.api.v2.ApiV2ErrorCodes;
import com.researchspace.service.MessageSourceUtils;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.NoSuchMessageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/** Resolves stable problem titles and optional occurrence details. */
@Component
@Slf4j
public final class ApiV2ProblemFactory {
  private final MessageSourceUtils messages;

  public ApiV2ProblemFactory(MessageSourceUtils messages) {
    this.messages = messages;
  }

  public ResponseEntity<ApiV2Problem> problem(HttpStatus status, String code, Object... arguments) {
    return problem(status, code, Map.of(), arguments);
  }

  public ResponseEntity<ApiV2Problem> problem(
      HttpStatus status, String code, Map<String, Object> extensions, Object... arguments) {
    String title;
    String detail = null;
    if (arguments.length > 0) {
      detail = messages.getMessage(code, arguments);
      try {
        String titleKey = code + "Title";
        title = messages.getMessage(titleKey);
        if (titleKey.equals(title)) {
          log.warn("Missing REST API v2 problem title for code {}", code);
          title = messages.getMessage(ApiV2ErrorCodes.messageKeyFor(status));
        }
      } catch (NoSuchMessageException ex) {
        log.warn("Missing REST API v2 problem title for code {}", code, ex);
        title = messages.getMessage(ApiV2ErrorCodes.messageKeyFor(status));
      }
    } else {
      title = messages.getMessage(code);
    }
    return response(status, code, title, detail, null, extensions);
  }

  public ResponseEntity<ApiV2Problem> problem(
      HttpStatus status,
      String code,
      String explicitDetail,
      List<ApiV2Problem.InvalidParam> invalidParams,
      Map<String, Object> extensions) {
    return response(
        status, code, messages.getMessage(code), explicitDetail, invalidParams, extensions);
  }

  private ResponseEntity<ApiV2Problem> response(
      HttpStatus status,
      String code,
      String title,
      String detail,
      List<ApiV2Problem.InvalidParam> invalidParams,
      Map<String, Object> extensions) {
    return ResponseEntity.status(status)
        .contentType(ApiV2Problem.PROBLEM_JSON)
        .body(new ApiV2Problem(title, status.value(), code, detail, invalidParams, extensions));
  }
}
