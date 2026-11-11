package com.researchspace.model.collection;

import java.util.List;
import java.util.Objects;

/**
 * One page of a runtime-field catalog.
 *
 * <p>Reports whether more pages exist, and an exact total only when it happens to be free.
 *
 * <p>An exact total is not free in general: knowing how many definitions this actor can reach means
 * evaluating "is this definition carried by any readable item" for every definition in the
 * deployment, which measured 31.6 seconds p95 at 20,000 definitions while the page itself took
 * milliseconds. So the page is read one row longer than asked for, and the extra row answers "is
 * there more" without counting anything. When there is no extra row the total is known exactly,
 * because the page has reached the end.
 *
 * @param total exact count, or null when only {@code hasMore} is known
 */
public record RuntimeFieldCatalogPage(
    List<RuntimeFieldDefinition> fields, Long total, boolean hasMore) {

  public RuntimeFieldCatalogPage {
    fields = List.copyOf(Objects.requireNonNull(fields, "Runtime field definitions"));
    if (total != null && total < 0) {
      throw new IllegalArgumentException("Total must not be negative");
    }
    if (total != null && hasMore) {
      throw new IllegalArgumentException("A total is only exact when there is no further page");
    }
  }

  public static RuntimeFieldCatalogPage empty() {
    return new RuntimeFieldCatalogPage(List.of(), 0L, false);
  }
}
