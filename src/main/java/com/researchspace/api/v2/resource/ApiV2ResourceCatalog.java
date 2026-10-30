package com.researchspace.api.v2.resource;

import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.RuntimeCollectionFields;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** Builds and owns the validated graph of standard REST API v2 resources. */
public final class ApiV2ResourceCatalog {

  private final ResourceRegistry registry;
  private final Map<String, ApiV2ResourceRegistration<?, ?>> resources;

  public ApiV2ResourceCatalog(List<ApiV2ResourceSpec<?, ?>> specs) {
    Objects.requireNonNull(specs, "Resource specs");
    registry = new ResourceRegistry(specs.stream().map(ApiV2ResourceSpec::description).toList());
    Map<String, ApiV2ResourceRegistration<?, ?>> registrations = new LinkedHashMap<>();
    Map<String, ApiV2ReadableResourceTarget> targets = new LinkedHashMap<>();
    ApiV2RelationshipResolver resolver =
        new ApiV2RelationshipResolver(name -> Optional.ofNullable(targets.get(name)));
    specs.stream()
        .map(spec -> bind(spec, registry, resolver, this::runtimeFieldsOf))
        .forEach(
            registration -> {
              registrations.put(registration.resourceName(), registration);
              putUnique(targets, registration.resourceName(), registration);
            });
    resources = Map.copyOf(registrations);
  }

  public ResourceRegistry registry() {
    return registry;
  }

  public Optional<ApiV2ResourceRegistration<?, ?>> find(String resourceName) {
    return Optional.ofNullable(resources.get(resourceName));
  }

  public List<ApiV2ResourceRegistration<?, ?>> routableResources() {
    return List.copyOf(resources.values());
  }

  public List<CollectionDescription<?>> allSchemas() {
    return List.copyOf(registry.resources());
  }

  /** Runtime providers owned by a routable resource. */
  public List<RuntimeCollectionFields<?>> runtimeFieldsOf(String resourceName) {
    return find(resourceName).map(ApiV2ResourceRegistration::providers).orElseGet(List::of);
  }

  private static <T, ID> ApiV2ResourceRegistration<T, ID> bind(
      ApiV2ResourceSpec<T, ID> spec,
      ResourceRegistry registry,
      ApiV2RelationshipResolver resolver,
      Function<String, List<RuntimeCollectionFields<?>>> providersByResource) {
    return spec.bind(registry, resolver, providersByResource);
  }

  private static void putUnique(
      Map<String, ApiV2ReadableResourceTarget> targets,
      String name,
      ApiV2ReadableResourceTarget target) {
    if (targets.putIfAbsent(name, target) != null) {
      throw new IllegalArgumentException("Duplicate REST API v2 relationship target " + name);
    }
  }
}
