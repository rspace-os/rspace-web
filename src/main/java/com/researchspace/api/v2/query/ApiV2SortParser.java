package com.researchspace.api.v2.query;

import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionDescription.Sort;
import com.researchspace.model.collection.CollectionQueryException;
import com.researchspace.model.collection.CollectionQueryLimits;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Parses Payload-style comma-separated sort fields against a collection allowlist. */
public final class ApiV2SortParser {

  static final int MAX_SORT_FIELDS = CollectionQueryLimits.MAX_SORT_FIELDS;

  private ApiV2SortParser() {}

  public static List<Sort> parse(String requestedSort, CollectionDescription<?> description) {
    if (requestedSort == null || requestedSort.isBlank()) {
      return description.defaultSort();
    }

    String[] tokens = requestedSort.split(",", -1);
    if (tokens.length > MAX_SORT_FIELDS) {
      throw new CollectionQueryException(CollectionQueryException.Reason.COMPLEXITY);
    }

    List<Sort> result = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    boolean includesId = false;
    for (String token : tokens) {
      String value = token.trim();
      if (value.isEmpty()) {
        throw new CollectionQueryException(CollectionQueryException.Reason.FIELD);
      }
      boolean ascending = !value.startsWith("-");
      String field = value.startsWith("-") ? value.substring(1) : value;
      if (!description.requireField(field).sortable()) {
        throw new CollectionQueryException(CollectionQueryException.Reason.FIELD);
      }
      if (!seen.add(field)) {
        throw new CollectionQueryException(CollectionQueryException.Reason.FIELD);
      }
      result.add(new Sort(field, ascending));
      includesId |= description.idField().equals(field);
    }
    if (!includesId) {
      result.add(new Sort(description.idField(), true));
    }
    return List.copyOf(result);
  }
}
