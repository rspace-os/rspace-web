package com.researchspace.model.collection;

import com.researchspace.model.User;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A collection's per-actor fields, which no source file can enumerate.
 *
 * <p>{@link CollectionDescription} is the fixed public schema and stays that way: it is the safe
 * default for every resource without a provider, and it is what the globally cached OpenAPI
 * document publishes. Fields that exist only because some user created a template cannot live
 * there, so a resource that has them supplies one of these instead.
 *
 * <p>Everything here is actor-scoped by contract. {@link #resolve} returning empty is the single
 * answer for "no such field" and for "not yours", so a caller cannot use the difference to discover
 * that a definition exists.
 *
 * @param <T> the entity this collection exposes
 */
public interface RuntimeCollectionFields<T> {

  String namespace();

  /**
   * The definitions this actor may name, bounded by {@code query}.
   *
   * <p>Always a page, never the whole set. How many definitions exist is a property of how many
   * templates a deployment has created, so an unbounded answer has a cost the server cannot predict
   * and a size the client cannot use.
   */
  RuntimeFieldCatalogPage discover(User actor, RuntimeFieldCatalogQuery query);

  /**
   * Resolves one {@code namespace.id} selector for this actor.
   *
   * @return empty when the selector is malformed, unknown, or not readable by this actor
   */
  Optional<ResolvedRuntimeField> resolve(String selector, User actor);

  /**
   * Resolves several selectors for this actor in one go.
   *
   * <p>One request can name as many selectors as the projection limit allows, and resolving them
   * one at a time turns a single page load into that many provider round trips. The default keeps a
   * provider without a batch of its own correct; a provider that can answer in one query overrides
   * it.
   *
   * @return only the selectors that resolved, keyed by the selector as it was written. An absent
   *     key is the same single answer {@link #resolve} gives: no such field, or not yours.
   */
  default Map<String, ResolvedRuntimeField> resolveAll(Set<String> selectors, User actor) {
    Map<String, ResolvedRuntimeField> resolved = new LinkedHashMap<>();
    selectors.forEach(
        selector -> resolve(selector, actor).ifPresent(field -> resolved.put(selector, field)));
    return resolved;
  }

  /**
   * Loads the selected values for a whole page in bulk.
   *
   * <p>Batched rather than per-row because the alternative walks each row's field collection and
   * turns one page into one query per row. The outer key is the collection's own ID value, as
   * returned by {@link CollectionDescription#idValue}; the inner key is the definition ID.
   *
   * <p>An absent inner key means the definition does not apply to that row. A present {@code null}
   * means it applies but holds no value.
   */
  Map<Object, Map<String, Object>> values(List<T> resources, Set<String> fieldIds, User actor);

  /**
   * Whether this provider's fields can be projected through another collection's relationship.
   *
   * <p>False by default, and published as {@code columnSelectable} on a hopped namespace, because a
   * column that cannot be filled is worse than one that is not offered. A provider says yes only
   * once {@link #valuesForIds} applies its own resource's read rule: the caller reached these rows
   * through a relationship, so nothing upstream has checked they may read the targets.
   */
  default boolean projectsThroughRelationship() {
    return false;
  }

  /**
   * Values for resources named by ID rather than held as entities.
   *
   * <p>The hopped counterpart of {@link #values}. A relationship gives the ID of its target and
   * nothing else, so this is the only shape that path can ask in. Unlike {@link #values}, whose
   * resources are the page the caller just read, an implementation here must scope to what the
   * caller may read: a caller who can read the referring row has not thereby been granted the
   * target.
   *
   * <p>Empty by default, which pairs with {@link #projectsThroughRelationship} returning false.
   */
  default Map<Object, Map<String, Object>> valuesForIds(
      Collection<?> resourceIds, Set<String> fieldIds, User actor) {
    return Map.of();
  }

  static <T> RuntimeCollectionFields<T> none() {
    return new RuntimeCollectionFields<>() {

      @Override
      public String namespace() {
        return "";
      }

      @Override
      public RuntimeFieldCatalogPage discover(User actor, RuntimeFieldCatalogQuery query) {
        return RuntimeFieldCatalogPage.empty();
      }

      @Override
      public Optional<ResolvedRuntimeField> resolve(String selector, User actor) {
        return Optional.empty();
      }

      @Override
      public Map<Object, Map<String, Object>> values(
          List<T> resources, Set<String> fieldIds, User actor) {
        return Map.of();
      }
    };
  }
}
