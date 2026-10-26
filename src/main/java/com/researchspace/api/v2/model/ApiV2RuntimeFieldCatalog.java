package com.researchspace.api.v2.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** One page of the runtime fields a caller may name on a collection. */
public record ApiV2RuntimeFieldCatalog(
    List<Entry> fields,
    boolean hasMore,
    // Only known when this is the last page; omitted rather than sent as null.
    @JsonInclude(JsonInclude.Include.NON_NULL) Long totalFields,
    int page,
    int limit) {

  public record Entry(
      String id,
      String selector,
      String label,
      String type,
      String jsonType,
      List<String> operators,
      boolean supportsWildcards,
      boolean columnSelectable,
      boolean sortable,
      Source source,
      List<String> options) {}

  public record Source(String id, String label) {}
}
