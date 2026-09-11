package com.researchspace.model.sort;

import java.util.List;
import lombok.Getter;

/** A request asked to sort a listing by a key that listing does not offer. Mapped to HTTP 400. */
@Getter
public class UnknownSortKeyException extends IllegalArgumentException {

  private final String requestedKey;
  private final List<String> allowedKeys;

  public UnknownSortKeyException(String requestedKey, List<String> allowedKeys) {
    super(
        "Unknown sort key ["
            + withoutLineBreaks(requestedKey)
            + "], expected one of "
            + allowedKeys);
    this.requestedKey = withoutLineBreaks(requestedKey);
    this.allowedKeys = allowedKeys;
  }

  private static String withoutLineBreaks(String value) {
    return value == null ? null : value.replace("\r", "").replace("\n", "");
  }
}
