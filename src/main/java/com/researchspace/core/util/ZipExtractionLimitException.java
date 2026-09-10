package com.researchspace.core.util;

/**
 * Thrown when extraction stops because a {@link ZipExtractionLimits} bound was exceeded. Carries
 * which bound tripped and its configured value so callers can tell the user what to change.
 * Extraction has already removed everything it wrote.
 */
public class ZipExtractionLimitException extends InvalidArchiveException {

  /** The bound that stopped extraction. */
  public enum Limit {
    EXPANDED_SIZE,
    ENTRY_COUNT,
    FREE_DISK_SPACE,
    COMPRESSION_RATIO
  }

  private final Limit limit;
  private final long limitValue;

  public ZipExtractionLimitException(Limit limit, long limitValue, String message) {
    super(message);
    this.limit = limit;
    this.limitValue = limitValue;
  }

  public Limit getLimit() {
    return limit;
  }

  /** The configured bound, in the unit of the corresponding {@link ZipExtractionLimits} field. */
  public long getLimitValue() {
    return limitValue;
  }
}
