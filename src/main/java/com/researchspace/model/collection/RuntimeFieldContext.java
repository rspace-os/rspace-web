package com.researchspace.model.collection;

import com.researchspace.model.User;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * The providers and the actor, paired so request parsing can resolve a runtime selector.
 *
 * <p>Parsing is where a runtime field must be authorized, not a later check. A value is parsed with
 * the resolved type, so resolving after parsing would answer "invalid number" for a field the
 * caller may not see and answer "unknown field" for one that does not exist, and the difference
 * would tell them which is which.
 *
 * <p>A list rather than one provider, because one collection can have more than one kind of
 * per-actor field. An instrument has both {@code customFields}, copied from a template and shared
 * by every instrument using it, and {@code extraFields}, typed onto one instrument and shared with
 * nothing. They answer to different providers and cannot be merged: only the namespace tells them
 * apart, and each owns its own identity scheme.
 */
public record RuntimeFieldContext(
    List<RuntimeCollectionFields<?>> providers,
    User actor,
    Function<String, List<RuntimeCollectionFields<?>>> byResource) {

  private static final RuntimeFieldContext EMPTY =
      new RuntimeFieldContext(List.of(), null, name -> List.of());

  public RuntimeFieldContext {
    Objects.requireNonNull(providers, "Runtime field providers");
    providers = List.copyOf(providers);
    Function<String, List<RuntimeCollectionFields<?>>> lookup = byResource;
    byResource =
        lookup == null
            ? name -> List.of()
            : name -> {
              List<RuntimeCollectionFields<?>> found = lookup.apply(name);
              return found == null ? List.of() : found;
            };
  }

  public RuntimeFieldContext(List<RuntimeCollectionFields<?>> providers, User actor) {
    this(providers, actor, name -> List.of());
  }

  public static RuntimeFieldContext empty() {
    return EMPTY;
  }

  public boolean isEmpty() {
    return providers.isEmpty();
  }

  /**
   * The provider of another resource, for a field reached through a relationship.
   *
   * <p>A bookable item has no custom fields of its own; the instrument it points at does. Resolving
   * that still goes through the target resource's own provider and the caller's own access, so the
   * hop grants nothing the caller could not already see on the target itself.
   */
  public Optional<ResolvedRuntimeField> resolveOnTarget(String resourceName, String selector) {
    RuntimeCollectionFields<?> provider = providerFor(byResource.apply(resourceName), selector);
    return provider == null ? Optional.empty() : provider.resolve(selector, actor);
  }

  public Map<String, ResolvedRuntimeField> resolveAllOnTarget(
      String resourceName, Set<String> selectors) {
    return resolveAll(byResource.apply(resourceName), selectors);
  }

  /**
   * As {@link #resolveOnTarget}, but only through a provider whose values a hop can project.
   *
   * <p>Filtering a hopped runtime field needs nothing of the target document, so every provider
   * supports it. Projecting one has to put a value in this collection's response, which the
   * provider can only supply if it can answer by target ID under the target's own read rule. A
   * provider that cannot is not resolved here at all, so the selector is reported as an unsupported
   * field rather than accepted and then rendered empty.
   */
  public Optional<ResolvedRuntimeField> resolveProjectionOnTarget(
      String resourceName, String selector) {
    RuntimeCollectionFields<?> provider = providerFor(byResource.apply(resourceName), selector);
    return provider == null || !provider.projectsThroughRelationship()
        ? Optional.empty()
        : provider.resolve(selector, actor);
  }

  public boolean namespaced(String selector) {
    return providerFor(selector) != null;
  }

  public boolean isBareNamespace(String selector) {
    return providers.stream()
        .anyMatch(
            provider -> !provider.namespace().isEmpty() && provider.namespace().equals(selector));
  }

  public Optional<ResolvedRuntimeField> resolve(String selector) {
    RuntimeCollectionFields<?> provider = providerFor(selector);
    return provider == null ? Optional.empty() : provider.resolve(selector, actor);
  }

  /**
   * Resolves several selectors, one call per provider that owns any of them.
   *
   * <p>Grouped rather than resolved one at a time, so a request projecting many fields costs one
   * query per namespace instead of one per field. Absent keys did not resolve.
   */
  public Map<String, ResolvedRuntimeField> resolveAll(Set<String> selectors) {
    return resolveAll(providers, selectors);
  }

  public Optional<String> namespaceOf(String selector) {
    RuntimeCollectionFields<?> provider = providerFor(selector);
    return provider == null ? Optional.empty() : Optional.of(provider.namespace());
  }

  public Optional<String> namespaceOnTarget(String resourceName, String selector) {
    RuntimeCollectionFields<?> provider = providerFor(byResource.apply(resourceName), selector);
    return provider == null ? Optional.empty() : Optional.of(provider.namespace());
  }

  private Map<String, ResolvedRuntimeField> resolveAll(
      List<RuntimeCollectionFields<?>> available, Set<String> selectors) {
    if (selectors.isEmpty()) {
      return Map.of();
    }
    Map<RuntimeCollectionFields<?>, Set<String>> byProvider = new LinkedHashMap<>();
    for (String selector : selectors) {
      RuntimeCollectionFields<?> provider = providerFor(available, selector);
      if (provider != null) {
        byProvider
            .computeIfAbsent(provider, ignored -> new java.util.LinkedHashSet<>())
            .add(selector);
      }
    }
    Map<String, ResolvedRuntimeField> resolved = new LinkedHashMap<>();
    byProvider.forEach((provider, owned) -> resolved.putAll(provider.resolveAll(owned, actor)));
    return resolved;
  }

  public List<String> namespaces() {
    List<String> names = new ArrayList<>(providers.size());
    providers.forEach(provider -> names.add(provider.namespace()));
    return List.copyOf(names);
  }

  private RuntimeCollectionFields<?> providerFor(String selector) {
    return providerFor(providers, selector);
  }

  private static RuntimeCollectionFields<?> providerFor(
      List<RuntimeCollectionFields<?>> available, String selector) {
    RuntimeCollectionFields<?> best = null;
    for (RuntimeCollectionFields<?> provider : available) {
      if (under(provider, selector)
          && (best == null || provider.namespace().length() > best.namespace().length())) {
        best = provider;
      }
    }
    return best;
  }

  private static boolean under(RuntimeCollectionFields<?> provider, String selector) {
    String namespace = provider.namespace();
    return !namespace.isEmpty()
        && selector != null
        && selector.length() > namespace.length() + 1
        && selector.startsWith(namespace)
        && selector.charAt(namespace.length()) == '.';
  }
}
