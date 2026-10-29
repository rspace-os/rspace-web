package com.researchspace.api.v2;

import org.springframework.http.HttpStatus;

/** Default problem codes shared by API v2 error responses and their OpenAPI examples. */
public final class ApiV2ErrorCodes {

  private ApiV2ErrorCodes() {}

  /** Returns the fallback message key for a status; specific failures may supply their own key. */
  public static String messageKeyFor(HttpStatus status) {
    if (status.is5xxServerError()) {
      return "errors.api.v2.unexpected";
    }
    return switch (status) {
      case NOT_FOUND -> "errors.api.v2.notFound";
      case METHOD_NOT_ALLOWED -> "errors.api.v2.methodNotAllowed";
      case NOT_ACCEPTABLE -> "errors.api.v2.notAcceptable";
      case UNSUPPORTED_MEDIA_TYPE -> "errors.api.v2.unsupportedMediaType";
      case UNAUTHORIZED -> "errors.api.v2.authenticationRequired";
      case FORBIDDEN -> "errors.api.v2.forbidden";
      case TOO_MANY_REQUESTS -> "errors.api.v2.tooManyRequests";
      case BAD_REQUEST -> "errors.api.v2.invalidRequest";
      default -> "errors.api.v2.requestRejected";
    };
  }
}
