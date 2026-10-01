package com.researchspace.model.collection;

/** Default safety limits for one REST API v2 collection mutation request. */
public record CollectionMutationLimits(int maxBulkCreateRows, int maxBulkUpdateDeleteRows) {

  /** Create bodies are fully parsed, relationship-resolved, persisted, and rendered in memory. */
  public static final int MAX_BULK_CREATE_ROWS = 100;

  /** Filtered mutations select at most one bounded working set before changing any rows. */
  public static final int MAX_BULK_UPDATE_DELETE_ROWS = 1000;

  public static final CollectionMutationLimits DEFAULT =
      new CollectionMutationLimits(MAX_BULK_CREATE_ROWS, MAX_BULK_UPDATE_DELETE_ROWS);

  public CollectionMutationLimits {
    if (maxBulkCreateRows < 1 || maxBulkUpdateDeleteRows < 1) {
      throw new IllegalArgumentException("Collection mutation limits must be positive");
    }
  }
}
