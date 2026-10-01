package com.researchspace.api.v2.resource;

import com.researchspace.model.User;
import com.researchspace.model.collection.ResolvedRuntimeField;
import com.researchspace.model.collection.RuntimeCollectionFields;
import com.researchspace.model.collection.RuntimeFieldCatalogPage;
import com.researchspace.model.collection.RuntimeFieldCatalogQuery;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reuses a source's providers without granting its row access through a safe relationship. */
record DelegatedRuntimeFields<T>(RuntimeCollectionFields<T> source, String resourceName)
    implements RuntimeCollectionFields<T> {

  @Override
  public String namespace() {
    return source.namespace();
  }

  @Override
  public RuntimeFieldCatalogPage discover(User actor, RuntimeFieldCatalogQuery query) {
    return source.discover(actor, query);
  }

  @Override
  public Map<String, ResolvedRuntimeField> resolveAll(Set<String> selectors, User actor) {
    Map<String, ResolvedRuntimeField> fields = new LinkedHashMap<>();
    source
        .resolveAll(selectors, actor)
        .forEach(
            (selector, field) ->
                fields.put(
                    selector,
                    new ResolvedRuntimeField(field.definition(), field.binding(), resourceName)));
    return fields;
  }

  @Override
  public Map<Object, Map<String, Object>> values(List<T> resources, Set<String> ids, User actor) {
    return source.values(resources, ids, actor);
  }

  @Override
  public boolean projectsThroughRelationship() {
    return source.projectsThroughRelationship();
  }

  @Override
  public Map<Object, Map<String, Object>> valuesForIds(
      Collection<?> resourceIds, Set<String> ids, User actor) {
    return source.valuesForIds(resourceIds, ids, actor);
  }
}
