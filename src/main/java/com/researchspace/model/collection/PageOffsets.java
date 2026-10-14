package com.researchspace.model.collection;

/** Offset arithmetic for one-based pages; request validation stays with each caller. */
public final class PageOffsets {

  private PageOffsets() {}

  public static long offset(int page, int size) {
    return ((long) page - 1) * size;
  }
}
