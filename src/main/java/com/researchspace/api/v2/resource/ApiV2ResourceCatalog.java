package com.researchspace.api.v2.resource;

import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.ResourceRegistry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/** Builds and owns the validated graph of standard REST API v2 resources. */
public final class ApiV2ResourceCatalog {

  private final ResourceRegistry registry;
  private final Map<String, ApiV2ResourceRegistration<?, ?>> resources;
  private final Map<String, ApiV2RelationshipTargetSpec<?, ?>> relationshipTargets;

  public ApiV2ResourceCatalog(List<ApiV2ResourceSpec<?, ?>> specs) {
    this(specs, List.of());
  }

  public ApiV2ResourceCatalog(
      List<ApiV2ResourceSpec<?, ?>> specs, List<ApiV2RelationshipTargetSpec<?, ?>> targetSpecs) {
    Objects.requireNonNull(specs, "Resource specs");
    Objects.requireNonNull(targetSpecs, "Relationship target specs");
    registry =
        new ResourceRegistry(
            Stream.concat(
                    specs.stream().map(ApiV2ResourceSpec::description),
                    targetSpecs.stream().map(ApiV2RelationshipTargetSpec::description))
                .toList());
    Map<String, ApiV2ResourceRegistration<?, ?>> registrations = new LinkedHashMap<>();
    Map<String, ApiV2ReadableResourceTarget> targets = new LinkedHashMap<>();
    Map<String, ApiV2RelationshipTargetSpec<?, ?>> targetOnly = new LinkedHashMap<>();
    targetSpecs.forEach(target -> putUnique(targets, target.description().resourceName(), target));
    targetSpecs.forEach(target -> targetOnly.put(target.description().resourceName(), target));
    ApiV2RelationshipResolver resolver =
        new ApiV2RelationshipResolver(name -> Optional.ofNullable(targets.get(name)));
    specs.stream()
        .map(spec -> bind(spec, registry, resolver))
        .forEach(
            registration -> {
              registrations.put(registration.resourceName(), registration);
              putUnique(targets, registration.resourceName(), registration);
            });
    resources = Map.copyOf(registrations);
    relationshipTargets = Map.copyOf(targetOnly);
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

  public List<ApiV2RelationshipTargetSpec<?, ?>> relationshipTargets() {
    return List.copyOf(relationshipTargets.values());
  }

  public List<CollectionDescription<?>> allSchemas() {
    return List.copyOf(registry.resources());
  }

  private static <T, ID> ApiV2ResourceRegistration<T, ID> bind(
      ApiV2ResourceSpec<T, ID> spec,
      ResourceRegistry registry,
      ApiV2RelationshipResolver resolver) {
    return spec.bind(registry, resolver);
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
