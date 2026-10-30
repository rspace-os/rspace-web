package com.researchspace.model.collection;

import java.util.List;
import java.util.Objects;

/** Complete, typed request for reading or selecting resources from one collection. */
public record ResourceRequest(
    FilterExpression filter,
    QueryConstraint serverConstraint,
    List<Sort> sort,
    Page page,
    ResourceFieldSelections fieldSelections,
    IncludeTree includes,
    RuntimeFieldSelection runtime,
    Integer countMax) {

  /** Preserves the unbounded request contract for existing collection callers. */
  public ResourceRequest(
      FilterExpression filter,
      QueryConstraint serverConstraint,
      List<Sort> sort,
      Page page,
      ResourceFieldSelections fieldSelections,
      IncludeTree includes,
      RuntimeFieldSelection runtime) {
    this(filter, serverConstraint, sort, page, fieldSelections, includes, runtime, null);
  }

  /** Applies a bound only to explicit count operations, leaving counted pages unchanged. */
  public ResourceRequest withCountMax(Integer max) {
    if (max != null && (max < 1 || max > 10000))
      throw new IllegalArgumentException("Invalid count bound");
    return new ResourceRequest(
        filter, serverConstraint, sort, page, fieldSelections, includes, runtime, max);
  }

  public record Page(int number, int size) {

    public Page {
      if (number < 1 || size < 1 || size > CollectionQueryLimits.MAX_PAGE_SIZE) {
        throw new IllegalArgumentException(
            "Page number must be positive and size must be between 1 and "
                + CollectionQueryLimits.MAX_PAGE_SIZE);
      }
    }

    /** The zero-based row offset, which can exceed a persistence adapter's integer limit. */
    public long offset() {
      return PageOffsets.offset(number, size);
    }
  }

  public ResourceRequest {
    sort = List.copyOf(sort);
    Objects.requireNonNull(page, "Page");
    Objects.requireNonNull(fieldSelections, "Field selections");
    Objects.requireNonNull(includes, "Include tree");
    Objects.requireNonNull(runtime, "Runtime field selection");
  }

  /**
   * The shape every resource without a runtime-field provider keeps.
   *
   * <p>An empty selection rather than a null one, so the query compiler and the renderer need no
   * special case and a resource that never opts in behaves exactly as it did.
   */
  public ResourceRequest(
      FilterExpression filter,
      QueryConstraint serverConstraint,
      List<Sort> sort,
      Page page,
      ResourceFieldSelections fieldSelections,
      IncludeTree includes) {
    this(
        filter,
        serverConstraint,
        sort,
        page,
        fieldSelections,
        includes,
        RuntimeFieldSelection.empty());
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

  public ResourceRequest(
      FilterExpression filter,
      List<Sort> sort,
      Page page,
      ResourceFieldSelections fieldSelections,
      IncludeTree includes,
      RuntimeFieldSelection runtime) {
    this(filter, null, sort, page, fieldSelections, includes, runtime);
  }

  /** Field selection for the request's root resource. */
  public FieldSelection fields() {
    return fieldSelections.root();
  }

  public static ResourceRequest unpaged(FilterExpression filter) {
    return new ResourceRequest(
        filter, List.of(), new Page(1, 1), FieldSelection.all(), IncludeTree.empty());
  }

  public static ResourceRequest unpaged(FilterExpression filter, RuntimeFieldSelection runtime) {
    return new ResourceRequest(
        filter,
        List.of(),
        new Page(1, 1),
        ResourceFieldSelections.root(FieldSelection.all()),
        IncludeTree.empty(),
        runtime);
  }

  /** Adds a trusted server-owned restriction without changing or reclassifying caller input. */
  public ResourceRequest restrict(QueryConstraint constraint) {
    if (constraint == null || containsConstraint(serverConstraint, constraint)) {
      return this;
    }
    QueryConstraint combined =
        serverConstraint == null
            ? constraint
            : new QueryConstraint.And(List.of(serverConstraint, constraint));
    return new ResourceRequest(
        filter, combined, sort, page, fieldSelections, includes, runtime, countMax);
  }

  private static boolean containsConstraint(QueryConstraint current, QueryConstraint candidate) {
    if (candidate.equals(current)) return true;
    return current instanceof QueryConstraint.And and
        && and.children().stream().anyMatch(child -> containsConstraint(child, candidate));
  }
}
