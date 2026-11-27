package com.researchspace.webapp.filter;

import com.researchspace.core.util.JacksonUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * RFC 9457 problem details that tell the document editor to keep its draft and ask the user to sign
 * in again. They are sent only to requests whose Accept header names {@code
 * application/problem+json}, so other callers keep the plain-text error they already display.
 */
public enum SessionProblem {
  /** The request has no authenticated session. */
  SESSION_ENDED("session-ended", "Session ended"),
  /** The signed-in account is not the one that opened the editor. */
  EDITOR_ACCOUNT_CHANGED("editor-account-changed", "Signed in as a different user");

  private static final HttpStatus STATUS = HttpStatus.FORBIDDEN;

  private final URI type;
  private final String title;

  SessionProblem(String typeName, String title) {
    this.type = URI.create("https://researchspace.com/problems/" + typeName);
    this.title = title;
  }

  public URI getType() {
    return type;
  }

  /**
   * @return whether the request's Accept header explicitly names {@code application/problem+json}.
   *     A wildcard is not enough, since generic error handlers display the body as text.
   */
  public static boolean isAcceptedBy(HttpServletRequest request) {
    try {
      return MediaType.parseMediaTypes(Collections.list(request.getHeaders(HttpHeaders.ACCEPT)))
          .stream()
          .anyMatch(
              mediaType ->
                  mediaType.getQualityValue() > 0
                      && MediaType.APPLICATION_PROBLEM_JSON.equalsTypeAndSubtype(mediaType));
    } catch (InvalidMediaTypeException e) {
      return false;
    }
  }

  /** Writes this problem from a servlet filter, which runs outside Spring MVC. */
  public void write(HttpServletResponse response) throws IOException {
    response.setStatus(STATUS.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.getWriter().write(toJson());
  }

  /** This problem as a Spring MVC response. */
  public ResponseEntity<String> toResponseEntity() {
    return ResponseEntity.status(STATUS)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(toJson());
  }

  private String toJson() {
    Map<String, Object> problem = new LinkedHashMap<>();
    problem.put("type", type.toString());
    problem.put("title", title);
    problem.put("status", STATUS.value());
    return JacksonUtil.toJson(problem);
  }
}
