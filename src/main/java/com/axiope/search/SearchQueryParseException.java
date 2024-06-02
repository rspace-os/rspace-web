package com.axiope.search;

/** Encapsulates search implementation-specific query parsing errors */
public class SearchQueryParseException extends RuntimeException {

  public SearchQueryParseException(Exception cause) {
    super(cause);
  }

  /** */
  private static final long serialVersionUID = 1L;
}
