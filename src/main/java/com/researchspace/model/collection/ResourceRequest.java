package com.researchspace.model.collection;

import com.researchspace.model.collection.CollectionDescription.Sort;
import java.util.List;
import java.util.Objects;

/** Complete, typed request for reading or selecting resources from one collection. */
public record ResourceRequest(
    FilterExpression filter,
    FilterExpression serverConstraint,
    List<Sort> sort,
    Page page,
    ResourceFieldSelections fieldSelections,
    IncludeTree includes) {

  public record Page(int number, int size) {

    public Page {
      if (number < 1 || size < 1) {
        throw new IllegalArgumentException("Page number and size must be positive");
      }
    }
  }

  public ResourceRequest {
    sort = List.copyOf(sort);
    Objects.requireNonNull(page, "Page");
    Objects.requireNonNull(fieldSelections, "Field selections");
    Objects.requireNonNull(includes, "Include tree");
  }

  public ResourceRequest(
      FilterExpression filter,
      List<Sort> sort,
      Page page,
      FieldSelection fields,
      IncludeTree includes) {
    this(filter, null, sort, page, ResourceFieldSelections.root(fields), includes);
  }

  public ResourceRequest(
      FilterExpression filter,
      List<Sort> sort,
      Page page,
      ResourceFieldSelections fieldSelections,
      IncludeTree includes) {
    this(filter, null, sort, page, fieldSelections, includes);
  }

  /** Field selection for the request's root resource. */
  public FieldSelection fields() {
    return fieldSelections.root();
  }

  public static ResourceRequest unpaged(FilterExpression filter) {
    return new ResourceRequest(
        filter, List.of(), new Page(1, 1), FieldSelection.all(), IncludeTree.empty());
  }

  /** Adds a trusted server-owned restriction without changing or reclassifying caller input. */
  public ResourceRequest restrict(FilterExpression constraint) {
    if (constraint == null) {
      return this;
    }
    FilterExpression combined =
        serverConstraint == null
            ? constraint
            : new FilterExpression.And(List.of(serverConstraint, constraint));
    return new ResourceRequest(filter, combined, sort, page, fieldSelections, includes);
  }
}
