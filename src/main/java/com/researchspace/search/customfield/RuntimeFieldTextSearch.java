package com.researchspace.search.customfield;

import java.util.List;
import java.util.Optional;

/**
 * Finds the IDs of indexed records whose value for one text field matches free text.
 *
 * <p>Narrowing only. The IDs this returns are fed back into the ordinary collection query as one
 * more conjunct, so the caller's row rule still decides what is returned. The index is never
 * allowed to widen a result set, only to shrink the set the database has to consider.
 *
 * <p>Deliberately expressed as "IDs of this entity for this index field" rather than "instruments
 * for this custom field". The same question is asked of a template-backed custom field, an ad-hoc
 * extra field and a relationship target's own scalar, and the only thing that differs between them
 * is which index field holds the value.
 */
public interface RuntimeFieldTextSearch {

  int MAX_MATCHES = 10_000;

  /**
   * IDs of {@code indexedType} whose {@code indexField} matches every word of {@code text}.
   *
   * @param maxMatches most IDs worth having. Above it the caller would take the database path
   *     anyway, so the search stops there rather than fetching a set nobody will use.
   * @return empty when the index cannot answer — disabled, no such field, more than {@code
   *     maxMatches} hits, or a search fault — which tells the caller to use the database rather
   *     than silently truncating or shrinking the result
   */
  Optional<List<Long>> matchingIds(
      Class<?> indexedType, String indexField, String text, int maxMatches);

  void reindexAll() throws InterruptedException;
}
