package com.researchspace.model.collection;

import com.researchspace.model.User;
import com.researchspace.model.collection.AccessContext.Operation;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Evaluates and caches destination-collection read policies for one collection query.
 *
 * <p>A relationship does not own a second copy of its target's permissions. The target's registered
 * {@link AccessPolicy#readAccess()} remains authoritative, and its {@link AccessResult} is compiled
 * against each relationship alias that needs it. Caching by resource name means two relationship
 * fields that point to the same collection evaluate that policy only once for the caller.
 */
public final class RelationshipReadAccess {

  private static final RelationshipReadAccess NONE =
      new RelationshipReadAccess(null, ignored -> AccessResult.denied(AccessPolicy.FORBIDDEN));

  private final ResourceRegistry registry;
  private final Function<CollectionDescription<?>, AccessResult> evaluator;
  private final Map<String, AccessResult> results = new HashMap<>();

  private RelationshipReadAccess(
      ResourceRegistry registry, Function<CollectionDescription<?>, AccessResult> evaluator) {
    this.registry = registry;
    this.evaluator = Objects.requireNonNull(evaluator, "Access evaluator");
  }

  /** No relationship target descriptions are available. */
  public static RelationshipReadAccess none() {
    return NONE;
  }

  /** Evaluates each destination collection's read policy for the supplied effective user. */
  public static RelationshipReadAccess forActor(ResourceRegistry registry, User actor) {
    Objects.requireNonNull(registry, "Resource registry");
    return new RelationshipReadAccess(
        registry,
        description ->
            description
                .accessPolicy()
                .readAccess()
                .check(new AccessContext(actor, Operation.READ, description.resourceName())));
  }

  /**
   * Supplies target descriptions without narrowing their rows.
   *
   * <p>Used for system-authorized writes whose filter may name a relationship field but whose root
   * row selection is governed by the source collection's mutation policy.
   */
  public static RelationshipReadAccess unrestricted(ResourceRegistry registry) {
    Objects.requireNonNull(registry, "Resource registry");
    return new RelationshipReadAccess(registry, ignored -> AccessResult.allowed());
  }

  /** The target collection, or {@code null} when this query has no such registered target. */
  public CollectionDescription<?> description(String resourceName) {
    return registry == null ? null : registry.findResource(resourceName);
  }

  /** The cached read decision for a registered target collection. */
  public AccessResult result(String resourceName) {
    CollectionDescription<?> description = description(resourceName);
    if (description == null) {
      return AccessResult.denied(AccessPolicy.FORBIDDEN);
    }
    return results.computeIfAbsent(resourceName, ignored -> evaluator.apply(description));
  }

  /** Resolves one relationship-field selector through the same complete registry as access. */
  public Optional<ResourceRegistry.RelationshipQueryPath> findPath(
      String sourceResource, String selector) {
    return registry == null
        ? Optional.empty()
        : registry.findRelationshipQueryPath(sourceResource, selector);
  }
}
