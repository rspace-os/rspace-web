package com.researchspace.dao.query;

import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionDescription.Operator;
import com.researchspace.model.collection.CollectionDescription.Relationship;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.RelationshipTarget;
import com.researchspace.model.collection.ResolvedRuntimeField;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.RuntimeFieldValueType;
import com.researchspace.search.customfield.RuntimeFieldIndexNames;
import com.researchspace.search.customfield.RuntimeFieldTextSearch;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Rewrites a word filter over text into an ID set the index already knows.
 *
 * <p>A leading-wildcard {@code LIKE} over a text column cannot use a B-tree index, which measured
 * 3.6 seconds p95 against a 1.5 second goal on 90,000 rows of 10 KiB text. The index can answer the
 * same question by term, so the filter becomes "one of these records" and the database is left with
 * an ID list and the row rule.
 *
 * <p>Three filters take this path, because they ask the index the same question:
 *
 * <ul>
 *   <li>a runtime field of the listed collection, replaced by {@code id IN (…)};
 *   <li>a runtime field of a relationship's target, narrowed by the target IDs;
 *   <li>a target's own scalar, such as {@code target.name}, narrowed the same way.
 * </ul>
 *
 * <p>Narrowing only, in both directions that matter. The rewritten predicate is ANDed with the
 * caller's other filters and with the read rule exactly as before, so the index can never widen a
 * result set; and when the index cannot answer, the original predicate is left in place, so the
 * answer is never silently smaller than it should be.
 *
 * <p>A filter through a relationship keeps its original predicate as well as the ID set, rather
 * than replacing it. The original is what carries the target's read rule, and an index knows
 * nothing about permissions: {@code targetId IN (…)} on its own would return rows whose target the
 * caller cannot read. The ID set prunes the rows the correlated {@code EXISTS} has to be evaluated
 * for, and the {@code EXISTS} still decides.
 *
 * <p>{@code =like=} only, never {@code =contains=}. Both operators are substring matches on the
 * database, but only {@code =like=} is defined in terms of words ("every word must be present, in
 * any order"), which is what an analysed index answers. Serving {@code =contains=} from the index
 * would silently redefine it: {@code =contains=BSL} would stop finding "XBSL-2", and a client
 * cannot ask for a substring anywhere else. The residual difference for {@code =like=} is that a
 * word matches whole rather than as a substring, so {@code =like=BSL} finds "BSL-2" but not
 * "XBSL-2"; {@code =contains=} remains the exact-substring operator.
 */
public final class IndexedTextNarrowing {

  /**
   * No row can match, so the caller should answer with an empty page rather than query at all.
   *
   * <p>Raised only for a reason the database would also reach, never because the index came back
   * empty: an unbuilt or lagging index is empty too, and cannot be allowed to mean "no matches".
   */
  public static final class NoMatch extends RuntimeException {
    private NoMatch() {
      super(null, null, false, false);
    }
  }

  private static final int MAX_RELATIONSHIP_SCALAR_MATCHES = 2_000;

  private IndexedTextNarrowing() {}

  /**
   * Returns the request with every eligible text filter narrowed by an ID set.
   *
   * @throws NoMatch when a rewritten filter matched nothing, so the whole request cannot match
   */
  public static ResourceRequest apply(
      ResourceRequest request,
      CollectionDescription<?> description,
      RuntimeFieldTextSearch search) {
    if (request.filter() == null || search == null) {
      return request;
    }
    FilterExpression rewritten = rewrite(request.filter(), request, description, search);
    if (rewritten == request.filter()) {
      return request;
    }
    return new ResourceRequest(
        rewritten,
        request.serverConstraint(),
        request.sort(),
        request.page(),
        request.fieldSelections(),
        request.includes(),
        request.runtime());
  }

  private static FilterExpression rewrite(
      FilterExpression filter,
      ResourceRequest request,
      CollectionDescription<?> description,
      RuntimeFieldTextSearch search) {
    if (filter instanceof FilterExpression.And and) {
      return rewriteChildren(and.children(), request, description, search, true);
    }
    if (filter instanceof FilterExpression.Or or) {
      return rewriteChildren(or.children(), request, description, search, false);
    }
    if (filter instanceof FilterExpression.Comparison comparison) {
      return rewriteComparison(comparison, request, description, search);
    }
    return filter;
  }

  private static FilterExpression rewriteChildren(
      List<FilterExpression> children,
      ResourceRequest request,
      CollectionDescription<?> description,
      RuntimeFieldTextSearch search,
      boolean conjunction) {
    List<FilterExpression> rewritten = new ArrayList<>(children.size());
    boolean changed = false;
    for (FilterExpression child : children) {
      FilterExpression result;
      try {
        result = rewrite(child, request, description, search);
      } catch (NoMatch noMatch) {
        if (conjunction) {
          throw noMatch;
        }
        changed = true;
        continue;
      }
      changed |= result != child;
      rewritten.add(result);
    }
    if (!changed) {
      return conjunction ? new FilterExpression.And(children) : new FilterExpression.Or(children);
    }
    if (rewritten.isEmpty()) {
      throw new NoMatch();
    }
    return conjunction ? new FilterExpression.And(rewritten) : new FilterExpression.Or(rewritten);
  }

  private static FilterExpression rewriteComparison(
      FilterExpression.Comparison comparison,
      ResourceRequest request,
      CollectionDescription<?> description,
      RuntimeFieldTextSearch search) {
    if (comparison.operator() != Operator.LIKE || comparison.values().size() != 1) {
      return comparison;
    }
    String text = String.valueOf(comparison.values().get(0));
    ResolvedRuntimeField field = request.runtime().find(comparison.field());
    if (field != null) {
      return field.type() == RuntimeFieldValueType.TEXT
          ? narrowRuntimeField(comparison, field, request, description, search, text)
          : comparison;
    }
    return narrowRelationshipScalar(comparison, description, search, text);
  }

  private static FilterExpression narrowRuntimeField(
      FilterExpression.Comparison comparison,
      ResolvedRuntimeField field,
      ResourceRequest request,
      CollectionDescription<?> description,
      RuntimeFieldTextSearch search,
      String text) {
    String indexField = RuntimeFieldIndexNames.fieldForSelector(field.definition().selector());
    if (indexField == null) {
      return comparison;
    }
    String relationshipName = request.runtime().relationshipFor(comparison.field());
    if (relationshipName == null) {
      List<Long> ids =
          matches(
              search,
              description.entityType(),
              indexField,
              text,
              RuntimeFieldTextSearch.MAX_MATCHES);
      return ids == null
          ? comparison
          : new FilterExpression.Comparison(
              description.idField(), Operator.IN, List.copyOf(ids), false);
    }
    return narrowThroughRelationship(
        comparison,
        description,
        search,
        relationshipName,
        indexField,
        text,
        RuntimeFieldTextSearch.MAX_MATCHES);
  }

  private static FilterExpression narrowRelationshipScalar(
      FilterExpression.Comparison comparison,
      CollectionDescription<?> description,
      RuntimeFieldTextSearch search,
      String text) {
    String name = comparison.field();
    int separator = name.indexOf('.');
    if (separator <= 0 || description.findFilterSelector(name).isPresent()) {
      return comparison;
    }
    String targetField = name.substring(separator + 1);
    if (targetField.isEmpty() || targetField.indexOf('.') >= 0) {
      return comparison;
    }
    return narrowThroughRelationship(
        comparison,
        description,
        search,
        name.substring(0, separator),
        targetField,
        text,
        MAX_RELATIONSHIP_SCALAR_MATCHES);
  }

  private static FilterExpression narrowThroughRelationship(
      FilterExpression.Comparison comparison,
      CollectionDescription<?> description,
      RuntimeFieldTextSearch search,
      String relationshipName,
      String indexField,
      String text,
      int maxIds) {
    Optional<? extends Relationship<?>> found = description.findRelationship(relationshipName);
    if (found.isEmpty() || found.get().targets().size() != 1) {
      return comparison;
    }
    RelationshipTarget<?> target = found.get().targets().get(0);
    List<Long> ids = matches(search, target.entityType(), indexField, text, maxIds);
    if (ids == null || ids.size() > maxIds) {
      return comparison;
    }
    return new FilterExpression.And(
        List.of(
            new FilterExpression.Comparison(
                relationshipName + RELATIONSHIP_ID_SUFFIX, Operator.IN, List.copyOf(ids), false),
            comparison));
  }

  private static final String RELATIONSHIP_ID_SUFFIX = ".value";

  private static List<Long> matches(
      RuntimeFieldTextSearch search,
      Class<?> indexedType,
      String indexField,
      String text,
      int maxMatches) {
    Optional<List<Long>> found = search.matchingIds(indexedType, indexField, text, maxMatches);
    return found.isEmpty() || found.get().isEmpty() ? null : found.get();
  }
}
