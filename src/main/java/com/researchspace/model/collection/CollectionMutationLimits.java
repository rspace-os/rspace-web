package com.researchspace.model.collection;

/** Default safety limits for one REST API v2 collection mutation request. */
public final class CollectionMutationLimits {

  /** Create bodies are fully parsed, relationship-resolved, persisted, and rendered in memory. */
  public static final int MAX_BULK_CREATE_ROWS = 100;

  /** Filtered mutations select at most one bounded working set before changing any rows. */
  public static final int MAX_BULK_UPDATE_DELETE_ROWS = 1000;

  private CollectionMutationLimits() {}
}
