package com.researchspace.service.inventory.impl;

import com.researchspace.model.collection.ResolvedRuntimeField;
import com.researchspace.model.collection.RuntimeFieldBinding;
import com.researchspace.model.collection.RuntimeFieldCatalogPage;
import com.researchspace.model.collection.RuntimeFieldCatalogQuery;
import com.researchspace.model.collection.RuntimeFieldDefinition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.apache.commons.collections4.ListUtils;

/** Shared selector and batching operations for runtime-field managers. */
final class RuntimeFieldManagerSupport {

  private RuntimeFieldManagerSupport() {}

  static String selectorId(String selector, String namespace) {
    String prefix = namespace + ".";
    return selector == null || !selector.startsWith(prefix)
        ? null
        : selector.substring(prefix.length());
  }

  static <T> void add(Set<T> values, T value) {
    if (value != null) {
      values.add(value);
    }
  }

  static <T> Map<String, ResolvedRuntimeField> resolveAll(
      Set<String> selectors,
      Function<String, T> decode,
      Function<T, String> id,
      Function<T, RuntimeFieldBinding> binding,
      Function<Set<String>, RuntimeFieldCatalogPage> discover) {
    Map<String, T> requested = new LinkedHashMap<>();
    for (String selector : selectors) {
      T definition = decode.apply(selector);
      if (definition != null) {
        requested.put(selector, definition);
      }
    }
    if (requested.isEmpty()) {
      return Map.of();
    }

    Set<String> ids = new LinkedHashSet<>();
    requested.values().forEach(definition -> add(ids, id.apply(definition)));
    Map<String, RuntimeFieldDefinition> found = new LinkedHashMap<>();
    for (List<String> batch :
        ListUtils.partition(new ArrayList<>(ids), RuntimeFieldCatalogQuery.MAX_IDS)) {
      discover
          .apply(new LinkedHashSet<>(batch))
          .fields()
          .forEach(definition -> found.put(definition.id(), definition));
    }

    Map<String, ResolvedRuntimeField> resolved = new LinkedHashMap<>();
    requested.forEach(
        (selector, definition) -> {
          RuntimeFieldDefinition runtimeDefinition = found.get(id.apply(definition));
          if (runtimeDefinition != null) {
            resolved.put(
                selector, new ResolvedRuntimeField(runtimeDefinition, binding.apply(definition)));
          }
        });
    return resolved;
  }
}
