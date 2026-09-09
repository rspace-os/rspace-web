package com.researchspace.model.sort;

import java.util.List;
import lombok.Getter;

/** A request asked to sort a listing by a key that listing does not offer. Mapped to HTTP 400. */
@Getter
public class UnknownSortKeyException extends IllegalArgumentException {

  private static final long serialVersionUID = 1L;

  private final String requestedKey;
  private final List<String> allowedKeys;

  public UnknownSortKeyException(String requestedKey, List<String> allowedKeys) {
    super("Unknown sort key [" + requestedKey + "], expected one of " + allowedKeys);
    this.requestedKey = requestedKey;
    this.allowedKeys = allowedKeys;
  }
}
