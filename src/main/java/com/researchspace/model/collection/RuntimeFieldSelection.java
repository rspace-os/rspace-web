package com.researchspace.model.collection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The runtime fields one request resolved, carried with the request rather than re-resolved.
 *
 * <p>Filtering and projection are independent, exactly as they are for a described field: naming a
 * field in {@code where} does not put its value in the response, and selecting it as a column adds
 * no predicate. {@code selectors} therefore holds both, and {@code projected} names only the subset
 * whose values the response must carry.
 */
public record RuntimeFieldSelection(
    Map<String, ResolvedRuntimeField> selectors,
    Set<String> projected,
    Map<String, String> throughRelationship) {

  private static final RuntimeFieldSelection EMPTY =
      new RuntimeFieldSelection(Map.of(), Set.of(), Map.of());

  public RuntimeFieldSelection(Map<String, ResolvedRuntimeField> selectors, Set<String> projected) {
    this(selectors, projected, Map.of());
  }

  public RuntimeFieldSelection {
    Objects.requireNonNull(selectors, "Runtime field selectors");
    Objects.requireNonNull(projected, "Projected runtime fields");
    throughRelationship =
        throughRelationship == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(throughRelationship));
    selectors = Collections.unmodifiableMap(new LinkedHashMap<>(selectors));
    projected = Collections.unmodifiableSet(new LinkedHashSet<>(projected));
    for (String selector : projected) {
      if (!selectors.containsKey(selector)) {
        throw new IllegalArgumentException(
            "Projected runtime field was never resolved: " + selector);
      }
    }
  }

  public static RuntimeFieldSelection empty() {
    return EMPTY;
  }

  public boolean isEmpty() {
    return selectors.isEmpty();
  }

  /**
   * The resolved field behind a comparison, or null when the name is not a runtime field.
   *
   * <p>Null rather than an exception because the query compiler asks about every comparison and
   * most of them name a described field.
   */
  public ResolvedRuntimeField find(String selector) {
    return selectors.get(selector);
  }

  /**
   * The relationship a selector is reached through, or null when it is a field of this collection.
   *
   * <p>`target.customFields.SF104` is the instrument's field, not the bookable item's, so the query
   * has to correlate the value table to the instrument rather than to the row being listed.
   */
  public String relationshipFor(String selector) {
    return throughRelationship.get(selector);
  }

  public Set<String> projectedIds() {
    Set<String> ids = new LinkedHashSet<>();
    projected.forEach(selector -> ids.add(selectors.get(selector).id()));
    return Collections.unmodifiableSet(ids);
  }

  /**
   * As above, for one namespace only.
   *
   * <p>A collection can publish several namespaces, and each provider knows only its own IDs.
   * Handing one provider another's would ask it for definitions it has never heard of.
   */
  public Set<String> projectedIdsUnder(String namespace) {
    String prefix = namespace + ".";
    Set<String> ids = new LinkedHashSet<>();
    projected.stream()
        .filter(selector -> selector.startsWith(prefix))
        .forEach(selector -> ids.add(selectors.get(selector).id()));
    return Collections.unmodifiableSet(ids);
  }
}
