package com.researchspace.api.v2.resource;

import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.ResourceRegistry;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Everything needed to expose one collection through the standard REST API v2 routes. */
public record ApiV2ResourceSpec<T, ID>(
    CollectionDescription<T> description,
    ResourceOperations<T, ID> operations,
    Function<String, ID> idParser,
    String createErrorKey,
    String updateErrorKey,
    Set<ResourceOperation> exposedOperations,
    Map<ResourceOperation, Map<Integer, String>> errorResponses,
    Map<ResourceOperation, OpenApiOperationDocumentation> operationDocumentation) {

  private static final Set<ResourceOperation> STANDARD_OPERATIONS =
      Set.copyOf(EnumSet.allOf(ResourceOperation.class));

  public ApiV2ResourceSpec {
    Set<ResourceOperation> exposed = Set.copyOf(exposedOperations);
    Map<ResourceOperation, Map<Integer, String>> errors = new EnumMap<>(ResourceOperation.class);
    errorResponses.forEach(
        (operation, responses) -> {
          if (!exposed.contains(operation)) {
            throw new IllegalArgumentException(
                "Cannot document errors for an operation the resource does not expose");
          }
          responses.forEach(
              (status, code) -> {
                if (status < 400 || status > 599 || code == null || code.isBlank()) {
                  throw new IllegalArgumentException(
                      "Resource error responses require an error status and code");
                }
              });
          errors.put(operation, Map.copyOf(responses));
        });
    exposedOperations = exposed;
    errorResponses = Map.copyOf(errors);
    operationDocumentation = Map.copyOf(operationDocumentation);
    if (!exposed.containsAll(operationDocumentation.keySet())) {
      throw new IllegalArgumentException(
          "Cannot document an operation the resource does not expose");
    }
  }

  public ApiV2ResourceSpec(
      CollectionDescription<T> description,
      ResourceOperations<T, ID> operations,
      Function<String, ID> idParser,
      String createErrorKey,
      String updateErrorKey,
      Set<ResourceOperation> exposedOperations,
      Map<ResourceOperation, Map<Integer, String>> errorResponses) {
    this(
        description,
        operations,
        idParser,
        createErrorKey,
        updateErrorKey,
        exposedOperations,
        errorResponses,
        Map.of());
  }

  public ApiV2ResourceSpec(
      CollectionDescription<T> description,
      ResourceOperations<T, ID> operations,
      Function<String, ID> idParser,
      String createErrorKey,
      String updateErrorKey) {
    this(
        description,
        operations,
        idParser,
        createErrorKey,
        updateErrorKey,
        STANDARD_OPERATIONS,
        Map.of(),
        Map.of());
  }

  ApiV2ResourceRegistration<T, ID> bind(
      ResourceRegistry registry, ApiV2RelationshipResolver resolver) {
    return new ApiV2ResourceRegistration<>(
        description,
        registry,
        operations,
        idParser,
        createErrorKey,
        updateErrorKey,
        resolver,
        exposedOperations,
        errorResponses,
        operationDocumentation);
  }
}
