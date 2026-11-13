package com.researchspace.api.v2.openapi;

import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ordered;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ref;

import com.researchspace.api.v2.ApiV2ErrorCodes;
import com.researchspace.api.v2.openapi.ApiV2OpenApiText.Key;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;

/** Shared OpenAPI problem responses and response headers. */
final class ApiV2OpenApiResponses {
  private ApiV2OpenApiResponses() {}

  static final String PROBLEM_JSON = "application/problem+json";
  private static final Map<String, String> STANDARD_RESPONSE_NAMES =
      Map.of(
          "400", "BadRequest",
          "401", "Unauthenticated",
          "403", "Forbidden",
          "404", "NotFound",
          "406", "NotAcceptable",
          "415", "UnsupportedMediaType",
          "422", "BulkLimit",
          "429", "TooManyRequests",
          "500", "UnexpectedError");

  static Map<String, Object> standardResponses(ApiV2OpenApiText text) {
    return ordered(
        STANDARD_RESPONSE_NAMES.get("400"),
        problemResponse(
            text.get(Key.RESPONSE_BAD_REQUEST),
            400,
            ApiV2ErrorCodes.messageKeyFor(HttpStatus.BAD_REQUEST)),
        STANDARD_RESPONSE_NAMES.get("401"),
        problemResponse(
            text.get(Key.RESPONSE_UNAUTHENTICATED),
            401,
            ApiV2ErrorCodes.messageKeyFor(HttpStatus.UNAUTHORIZED)),
        STANDARD_RESPONSE_NAMES.get("403"),
        problemResponse(
            text.get(Key.RESPONSE_FORBIDDEN),
            403,
            ApiV2ErrorCodes.messageKeyFor(HttpStatus.FORBIDDEN)),
        STANDARD_RESPONSE_NAMES.get("404"),
        problemResponse(
            text.get(Key.RESPONSE_NOT_FOUND),
            404,
            ApiV2ErrorCodes.messageKeyFor(HttpStatus.NOT_FOUND)),
        STANDARD_RESPONSE_NAMES.get("406"),
        problemResponse(
            text.get(Key.RESPONSE_NOT_ACCEPTABLE),
            406,
            ApiV2ErrorCodes.messageKeyFor(HttpStatus.NOT_ACCEPTABLE)),
        STANDARD_RESPONSE_NAMES.get("415"),
        problemResponse(
            text.get(Key.RESPONSE_UNSUPPORTED_MEDIA_TYPE),
            415,
            ApiV2ErrorCodes.messageKeyFor(HttpStatus.UNSUPPORTED_MEDIA_TYPE)),
        STANDARD_RESPONSE_NAMES.get("422"),
        problemResponse(text.get(Key.RESPONSE_BULK_LIMIT), 422, "errors.api.v2.bulk.limit"),
        STANDARD_RESPONSE_NAMES.get("429"),
        ordered(
            "description",
            text.get(Key.RESPONSE_TOO_MANY_REQUESTS),
            "content",
            problemContent(
                text.get(Key.RESPONSE_TOO_MANY_REQUESTS),
                429,
                ApiV2ErrorCodes.messageKeyFor(HttpStatus.TOO_MANY_REQUESTS)),
            "headers",
            rateLimitHeaders()),
        STANDARD_RESPONSE_NAMES.get("500"),
        problemResponse(
            text.get(Key.RESPONSE_UNEXPECTED_ERROR),
            500,
            ApiV2ErrorCodes.messageKeyFor(HttpStatus.INTERNAL_SERVER_ERROR)));
  }

  static Map<String, Object> standardResponseReference(String status) {
    String name = STANDARD_RESPONSE_NAMES.get(status);
    return name == null ? null : Map.of("$ref", "#/components/responses/" + name);
  }

  /** Adds a reference to the shared component response for each status. */
  static void putStandardResponses(Map<String, Object> responses, String... statuses) {
    for (String status : statuses) {
      responses.put(status, Objects.requireNonNull(standardResponseReference(status), status));
    }
  }

  static Map<String, Object> problemResponse(String description, int status, String code) {
    return ordered(
        "description", description, "content", problemContent(description, status, code));
  }

  static Map<String, Object> problemResponse(
      String description, int status, Map<String, String> errors) {
    if (errors.size() == 1) {
      Map.Entry<String, String> error = errors.entrySet().iterator().next();
      return problemResponse(description, status, error.getKey());
    }
    Map<String, Object> examples = new LinkedHashMap<>();
    errors.forEach(
        (code, errorDescription) ->
            examples.put(
                code,
                ordered(
                    "summary",
                    errorDescription,
                    "value",
                    ordered("title", errorDescription, "status", status, "code", code))));
    return ordered(
        "description",
        description,
        "content",
        Map.of(PROBLEM_JSON, ordered("schema", ref("ApiV2Problem"), "examples", examples)));
  }

  private static Map<String, Object> problemContent(String description, int status, String code) {
    Map<String, Object> media = new LinkedHashMap<>();
    media.put("schema", ref("ApiV2Problem"));
    media.put("example", ordered("title", description, "status", status, "code", code));
    return Map.of(PROBLEM_JSON, media);
  }

  static Map<String, Object> standardHeaders(ApiV2OpenApiText text) {
    return ordered(
        "ETag",
        ordered(
            "description",
            text.get(Key.HEADER_ETAG),
            "schema",
            ordered("type", "string", "pattern", "^\\\"[^\\\"\\x00-\\x20]+\\\"$")),
        "RateLimitLimit",
        integerHeader(text.get(Key.HEADER_RATE_LIMIT_LIMIT)),
        "RateLimitRemaining",
        integerHeader(text.get(Key.HEADER_RATE_LIMIT_REMAINING)),
        "RateLimitWait",
        integerHeader(text.get(Key.HEADER_RATE_LIMIT_WAIT)),
        "RateLimitMinWait",
        integerHeader(text.get(Key.HEADER_RATE_LIMIT_MIN_WAIT)));
  }

  private static Map<String, Object> integerHeader(String description) {
    return ordered(
        "description", description, "schema", ordered("type", "integer", "format", "int64"));
  }

  static Map<String, Object> rateLimitHeaders() {
    Map<String, Object> headers =
        ordered(
            "X-Rate-Limit-Limit",
            headerRef("RateLimitLimit"),
            "X-Rate-Limit-Remaining",
            headerRef("RateLimitRemaining"),
            "X-Rate-Limit-WaitTimeMillis",
            headerRef("RateLimitWait"),
            "X-Rate-Limit-MinWaitIntervalMillis",
            headerRef("RateLimitMinWait"));
    for (String interval : List.of("quarter_min", "hour", "day")) {
      headers.put("X-Rate-Limit-Limit-" + interval, headerRef("RateLimitLimit"));
      headers.put("X-Rate-Limit-Remaining-" + interval, headerRef("RateLimitRemaining"));
      headers.put(
          "X-Rate-Limit-WaitTimeTillNextRequestMillis-" + interval, headerRef("RateLimitWait"));
    }
    return headers;
  }

  static Map<String, Object> headerRef(String name) {
    return Map.of("$ref", "#/components/headers/" + name);
  }
}
