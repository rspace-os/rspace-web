package com.researchspace.api.v2.openapi;

import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ordered;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ref;

import com.researchspace.api.v2.ApiV2ErrorCodes;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

  static Map<String, Object> standardResponses() {
    return ordered(
        STANDARD_RESPONSE_NAMES.get("400"),
        problemResponse(
            "The request is invalid.", 400, ApiV2ErrorCodes.messageKeyFor(HttpStatus.BAD_REQUEST)),
        STANDARD_RESPONSE_NAMES.get("401"),
        problemResponse(
            "Authentication is required.",
            401,
            ApiV2ErrorCodes.messageKeyFor(HttpStatus.UNAUTHORIZED)),
        STANDARD_RESPONSE_NAMES.get("403"),
        problemResponse(
            "The caller is not authorized.",
            403,
            ApiV2ErrorCodes.messageKeyFor(HttpStatus.FORBIDDEN)),
        STANDARD_RESPONSE_NAMES.get("404"),
        problemResponse(
            "The resource was not found or is not visible.",
            404,
            ApiV2ErrorCodes.messageKeyFor(HttpStatus.NOT_FOUND)),
        STANDARD_RESPONSE_NAMES.get("406"),
        problemResponse(
            "No acceptable response representation is available.",
            406,
            ApiV2ErrorCodes.messageKeyFor(HttpStatus.NOT_ACCEPTABLE)),
        STANDARD_RESPONSE_NAMES.get("415"),
        problemResponse(
            "The request media type is unsupported.",
            415,
            ApiV2ErrorCodes.messageKeyFor(HttpStatus.UNSUPPORTED_MEDIA_TYPE)),
        STANDARD_RESPONSE_NAMES.get("422"),
        problemResponse(
            "The bulk operation exceeds the configured limit.", 422, "errors.api.v2.bulk.limit"),
        STANDARD_RESPONSE_NAMES.get("429"),
        ordered(
            "description",
            "The request was throttled.",
            "content",
            problemContent(
                "The request was throttled.",
                429,
                ApiV2ErrorCodes.messageKeyFor(HttpStatus.TOO_MANY_REQUESTS)),
            "headers",
            rateLimitHeaders()),
        STANDARD_RESPONSE_NAMES.get("500"),
        problemResponse(
            "An unexpected server error occurred.",
            500,
            ApiV2ErrorCodes.messageKeyFor(HttpStatus.INTERNAL_SERVER_ERROR)));
  }

  static Map<String, Object> standardResponseReference(String status) {
    String name = STANDARD_RESPONSE_NAMES.get(status);
    return name == null ? null : Map.of("$ref", "#/components/responses/" + name);
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

  static Map<String, Object> standardHeaders() {
    return ordered(
        "ETag",
        ordered(
            "description",
            "Strong entity tag for the returned representation.",
            "schema",
            ordered("type", "string", "pattern", "^\\\"[^\\\"\\x00-\\x20]+\\\"$")),
        "RateLimitLimit",
        integerHeader("Configured request limit for the shortest throttle interval."),
        "RateLimitRemaining",
        integerHeader("Requests remaining in the shortest throttle interval."),
        "RateLimitWait",
        integerHeader("Minimum wait before another request, in milliseconds."),
        "RateLimitMinWait",
        integerHeader("Configured minimum interval between requests, in milliseconds."));
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

  static Map<String, Object> responseRef(String name) {
    return Map.of("$ref", "#/components/responses/" + name);
  }

  static Map<String, Object> headerRef(String name) {
    return Map.of("$ref", "#/components/headers/" + name);
  }
}
