package com.researchspace.api.v2.controller;

import com.researchspace.api.v2.resource.ApiV2ResourceException;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

/**
 * Strict parsing shared by API-v2 version preconditions.
 *
 * <p>Compressing proxies change the ETags they pass on: Apache mod_deflate appends {@code -gzip},
 * {@code -br} or {@code -deflate} inside the quotes, and others weaken the tag with {@code W/}.
 * Clients echo those tags in {@code If-Match}, so both parsers remove one such suffix and the weak
 * prefix before validating, and return the tag the server issued.
 */
final class ApiV2ConditionalRequest {

  private static final String WEAK_PREFIX = "W/";
  private static final Pattern PROXY_SUFFIX = Pattern.compile("-(?:gzip|br|deflate)\"$");

  private ApiV2ConditionalRequest() {}

  static long parseVersion(String value, String requiredCode) {
    String etag = normalize(value, requiredCode);
    try {
      long version = Long.parseLong(etag.substring(1, etag.length() - 1));
      if (version < 0) {
        throw new NumberFormatException();
      }
      return version;
    } catch (NumberFormatException ex) {
      throw new ApiV2BadRequestException("errors.api.v2.invalidRequest");
    }
  }

  static String parseStrongEtag(String value, String requiredCode) {
    String etag = normalize(value, requiredCode);
    if (etag.substring(1, etag.length() - 1).chars().anyMatch(c -> c == '"' || c < 0x21)) {
      throw new ApiV2BadRequestException("errors.api.v2.invalidRequest");
    }
    return etag;
  }

  /** The quoted tag without a weak prefix or proxy suffix; at least one character is left. */
  private static String normalize(String value, String requiredCode) {
    if (value == null) {
      throw ApiV2ResourceException.of(HttpStatus.PRECONDITION_REQUIRED, requiredCode);
    }
    String etag = value.startsWith(WEAK_PREFIX) ? value.substring(WEAK_PREFIX.length()) : value;
    if (!isQuoted(etag)) {
      throw new ApiV2BadRequestException("errors.api.v2.invalidRequest");
    }
    etag = PROXY_SUFFIX.matcher(etag).replaceFirst("\"");
    if (!isQuoted(etag)) {
      throw new ApiV2BadRequestException("errors.api.v2.invalidRequest");
    }
    return etag;
  }

  private static boolean isQuoted(String etag) {
    return etag.length() >= 3 && etag.charAt(0) == '"' && etag.charAt(etag.length() - 1) == '"';
  }
}
