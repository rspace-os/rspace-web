package com.researchspace.model.collection;

public final class CollectionQueryLimits {

  public static final int MAX_PAGE_SIZE = 100;
  public static final int MAX_RELATIONSHIP_DEPTH = 10;
  public static final int MAX_WHERE_LENGTH = 32768;
  public static final int MAX_SORT_FIELDS = 5;
  public static final int MAX_COMPARISONS = 50;
  public static final int MAX_LIKE_COMPARISONS = 10;
  public static final int MAX_FILTER_NESTING = 10;
  public static final int MAX_ARGUMENTS = 1000;

  private CollectionQueryLimits() {}
}
