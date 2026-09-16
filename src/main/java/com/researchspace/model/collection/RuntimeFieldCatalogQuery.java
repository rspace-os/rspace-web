package com.researchspace.model.collection;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * What one caller wants from a runtime-field catalog.
 *
 * <p>Bounded by construction. An unbounded catalog is the one shape that cannot work here: the
 * number of definitions is a property of how many templates a deployment has created, so "return
 * everything" is a request whose cost the server cannot predict and the client cannot use.
 *
 * <p>Two modes, because a client needs two different things. Browsing wants a page, optionally
 * narrowed by a search term. Restoring a saved view wants specific IDs and nothing else, which is
 * why {@code ids} bypasses paging rather than being a filter over it.
 *
 * @param search case-insensitive substring of the label, or null for no narrowing
 * @param ids specific definition IDs to hydrate; when non-empty, search and paging are ignored
 * @param page one-based page number
 * @param limit page size
 */
public record RuntimeFieldCatalogQuery(String search, Set<String> ids, int page, int limit) {

  public static final int DEFAULT_LIMIT = 50;
  public static final int MAX_LIMIT = 200;

  public static final int MAX_IDS = CollectionQueryLimits.MAX_RUNTIME_PROJECTIONS;

  public RuntimeFieldCatalogQuery {
    search = search == null || search.isBlank() ? null : search.trim();
    ids = ids == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(ids));
    if (ids.size() > MAX_IDS) {
      throw new IllegalArgumentException("Too many runtime field IDs requested");
    }
    if (page < 1) {
      throw new IllegalArgumentException("Page number must be positive");
    }
    if (limit < 1 || limit > MAX_LIMIT) {
      throw new IllegalArgumentException("Page size must be between 1 and " + MAX_LIMIT);
    }
    if ((long) (page - 1) * limit > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Page number is out of range");
    }
  }

  public static RuntimeFieldCatalogQuery firstPage() {
    return new RuntimeFieldCatalogQuery(null, Set.of(), 1, DEFAULT_LIMIT);
  }

  public static RuntimeFieldCatalogQuery byIds(Set<String> ids) {
    return new RuntimeFieldCatalogQuery(null, ids, 1, DEFAULT_LIMIT);
  }

  public boolean hydratesIds() {
    return !ids.isEmpty();
  }

  public String normalisedSearch() {
    return search == null ? null : search.toLowerCase(Locale.ROOT);
  }

  public int offset() {
    return (page - 1) * limit;
  }
}
