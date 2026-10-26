package com.researchspace.api.v2.resource;

import com.researchspace.model.User;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.Relationship;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.RuntimeCollectionFields;
import com.researchspace.model.collection.RuntimeFieldContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

final class ApiV2RuntimeFieldProjection<T> {

  private final CollectionDescription<T> description;
  private final ApiV2ResourceSpec<T, ?> spec;
  private final Function<String, List<RuntimeCollectionFields<?>>> providersByResource;

  ApiV2RuntimeFieldProjection(
      CollectionDescription<T> description,
      ApiV2ResourceSpec<T, ?> spec,
      Function<String, List<RuntimeCollectionFields<?>>> providersByResource) {
    this.description = description;
    this.spec = spec;
    this.providersByResource = providersByResource;
  }

  public List<RuntimeCollectionFields<T>> runtimeFields() {
    return spec.runtimeFields();
  }

  public Optional<RuntimeCollectionFields<T>> runtimeFields(String namespace) {
    return spec.runtimeFields().stream()
        .filter(provider -> provider.namespace().equals(namespace))
        .findFirst();
  }

  public List<String> runtimeNamespaces() {
    return spec.runtimeFields().stream().map(RuntimeCollectionFields::namespace).toList();
  }

  public RuntimeFieldContext runtimeFieldContext(User caller) {
    return new RuntimeFieldContext(providers(), caller, providersByResource);
  }

  public List<RuntimeCollectionFields<?>> providers() {
    return new ArrayList<>(spec.runtimeFields());
  }

  ApiV2CrudDispatcher.DocumentDecorator<T> runtimeValues(ResourceRequest request, User caller) {
    if (request.runtime().projected().isEmpty()) {
      return ApiV2CrudDispatcher.DocumentDecorator.none();
    }
    Map<RuntimeCollectionFields<T>, Set<String>> requested = requestedRuntimeValues(request);
    Map<String, Set<String>> hopped = hoppedProjections(request);
    if (requested.isEmpty() && hopped.isEmpty()) {
      return ApiV2CrudDispatcher.DocumentDecorator.none();
    }
    return (resources, documents) -> {
      requested.forEach(
          (provider, ids) -> {
            Map<Object, Map<String, Object>> values = provider.values(resources, ids, caller);
            for (int index = 0; index < resources.size(); index++) {
              Object id = description.idValue(resources.get(index));
              documents.get(index).put(provider.namespace(), values.getOrDefault(id, Map.of()));
            }
          });
      hopped.forEach(
          (responseField, ids) -> attachHopped(responseField, ids, resources, documents, caller));
    };
  }

  Map<RuntimeCollectionFields<T>, Set<String>> requestedRuntimeValues(ResourceRequest request) {
    Map<RuntimeCollectionFields<T>, Set<String>> requested = new LinkedHashMap<>();
    for (RuntimeCollectionFields<T> provider : spec.runtimeFields()) {
      Set<String> ids = request.runtime().projectedIdsUnder(provider.namespace());
      if (!ids.isEmpty()) {
        requested.put(provider, ids);
      }
    }
    return requested;
  }

  ApiV2CrudDispatcher.ReadDocumentDecorator runtimeReadDocumentValues(
      ResourceRequest request, User caller) {
    Map<RuntimeCollectionFields<T>, Set<String>> requested = requestedRuntimeValues(request);
    if (requested.isEmpty()) {
      return ApiV2CrudDispatcher.ReadDocumentDecorator.none();
    }
    return (resources, documents) -> {
      List<Object> resourceIds =
          resources.stream().map(resource -> resource.get(description.idField())).toList();
      requested.forEach(
          (provider, ids) -> {
            Map<Object, Map<String, Object>> values =
                provider.projectsThroughRelationship()
                    ? provider.valuesForIds(resourceIds, ids, caller)
                    : provider.valuesForAuthorizedIds(resourceIds, ids, caller);
            for (int index = 0; index < resourceIds.size(); index++) {
              documents
                  .get(index)
                  .put(provider.namespace(), values.getOrDefault(resourceIds.get(index), Map.of()));
            }
          });
    };
  }

  Map<String, Set<String>> hoppedProjections(ResourceRequest request) {
    Map<String, Set<String>> grouped = new LinkedHashMap<>();
    for (String selector : request.runtime().projected()) {
      String relationship = request.runtime().relationshipFor(selector);
      if (relationship == null) {
        continue;
      }
      int lastDot = selector.lastIndexOf('.');
      if (lastDot <= 0) {
        continue;
      }
      grouped
          .computeIfAbsent(selector.substring(0, lastDot), ignored -> new LinkedHashSet<>())
          .add(request.runtime().find(selector).id());
    }
    return grouped;
  }

  void attachHopped(
      String responseField,
      Set<String> fieldIds,
      List<T> resources,
      List<Map<String, Object>> documents,
      User caller) {
    int lastDot = responseField.indexOf('.');
    String relationshipName = responseField.substring(0, lastDot);
    String namespace = responseField.substring(lastDot + 1);
    Relationship<T> relationship = description.findRelationship(relationshipName).orElse(null);
    if (relationship == null || relationship.targets().size() != 1) {
      return;
    }
    String targetResource = relationship.targets().get(0).resourceName();
    RuntimeCollectionFields<?> provider =
        providersByResource.apply(targetResource).stream()
            .filter(candidate -> candidate.namespace().equals(namespace))
            .filter(RuntimeCollectionFields::projectsThroughRelationship)
            .findFirst()
            .orElse(null);
    if (provider == null) {
      return;
    }
    List<Object> targetIds = new ArrayList<>(resources.size());
    Set<Object> distinct = new LinkedHashSet<>();
    for (T resource : resources) {
      Object targetId = description.relationshipTargetId(resource, relationship).orElse(null);
      targetIds.add(targetId);
      if (targetId != null) {
        distinct.add(targetId);
      }
    }
    Map<Object, Map<String, Object>> values = provider.valuesForIds(distinct, fieldIds, caller);
    for (int index = 0; index < documents.size(); index++) {
      Object targetId = targetIds.get(index);
      documents
          .get(index)
          .put(
              responseField, targetId == null ? Map.of() : values.getOrDefault(targetId, Map.of()));
    }
  }
}
