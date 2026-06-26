package com.researchspace.netfiles;

import java.time.Instant;
import java.util.Map;

/**
 * The creator/creation-time audit attributes read back from a filestore object's user metadata (the
 * read counterpart to {@link WriteAttribution}). Encapsulates the delete-gate predicate so callers
 * don't re-parse raw metadata maps or re-implement the window check.
 *
 * @param createdBy the {@code rspace-created-by} username, or null if absent
 * @param createdAt the {@code rspace-created-at} instant, or null if absent/unparseable
 */
public record FilestoreAuditMetadata(String createdBy, Instant createdAt) {

  /** Builds audit metadata from an object's user-metadata map (missing/bad values become null). */
  public static FilestoreAuditMetadata from(Map<String, String> userMetadata) {
    Map<String, String> m = userMetadata == null ? Map.of() : userMetadata;
    return new FilestoreAuditMetadata(
        m.get(WriteAttribution.META_CREATED_BY),
        parseInstant(m.get(WriteAttribution.META_CREATED_AT)));
  }

  private static Instant parseInstant(String value) {
    if (value == null) {
      return null;
    }
    try {
      return Instant.parse(value);
    } catch (RuntimeException e) {
      return null;
    }
  }

  /** True when this object records {@code username} as its creator. */
  public boolean isCreatedBy(String username) {
    return createdBy != null && createdBy.equals(username);
  }

  /** True when a creation timestamp is present. */
  public boolean hasTimestamp() {
    return createdAt != null;
  }

  /** True when the creation time is at or after {@code cutoff} (i.e. within the delete window). */
  public boolean isWithin(Instant cutoff) {
    return createdAt != null && !createdAt.isBefore(cutoff);
  }
}
