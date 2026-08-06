package com.researchspace.api.v2.resource;

import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.ResourceRegistry;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.springframework.core.ExceptionDepthComparator;

/** Everything needed to expose one collection through the standard REST API v2 routes. */
public record ApiV2ResourceSpec<T, ID>(
    CollectionDescription<T> description,
    ResourceOperations<T, ID> operations,
    Function<String, ID> idParser,
    String createErrorKey,
    String updateErrorKey,
    Set<ResourceOperation> exposedOperations,
    Map<ResourceOperation, OpenApiOperationDocumentation> operationDocumentation,
    Map<ResourceOperation, List<ApiV2ErrorMapping>> errorMappings) {

  private static final Set<ResourceOperation> STANDARD_OPERATIONS =
      Set.copyOf(EnumSet.allOf(ResourceOperation.class));

  public ApiV2ResourceSpec {
    Objects.requireNonNull(description, "Resource description");
    Objects.requireNonNull(operations, "Resource operations");
    Objects.requireNonNull(idParser, "ID parser");
    createErrorKey = requireText(createErrorKey, "Create error key");
    updateErrorKey = requireText(updateErrorKey, "Update error key");
    Set<ResourceOperation> exposed = Set.copyOf(exposedOperations);
    exposedOperations = exposed;
    operationDocumentation = Map.copyOf(operationDocumentation);
    errorMappings = copyErrorMappings(errorMappings);
    if (!exposed.containsAll(operationDocumentation.keySet())
        || !exposed.containsAll(errorMappings.keySet())) {
      throw new IllegalArgumentException(
          "Cannot configure an operation the resource does not expose");
    }
    for (ResourceOperation operation : errorMappings.keySet()) {
      documentationFor(operation, operationDocumentation, errorMappings);
    }
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

  public ApiV2ResourceSpec(
      CollectionDescription<T> description,
      ResourceOperations<T, ID> operations,
      Function<String, ID> idParser,
      String createErrorKey,
      String updateErrorKey,
      Map<ResourceOperation, List<ApiV2ErrorMapping>> errorMappings) {
    this(
        description,
        operations,
        idParser,
        createErrorKey,
        updateErrorKey,
        STANDARD_OPERATIONS,
        Map.of(),
        errorMappings);
  }

  public ApiV2ResourceSpec(
      CollectionDescription<T> description,
      ResourceOperations<T, ID> operations,
      Function<String, ID> idParser,
      String createErrorKey,
      String updateErrorKey,
      Set<ResourceOperation> exposedOperations,
      Map<ResourceOperation, OpenApiOperationDocumentation> operationDocumentation) {
    this(
        description,
        operations,
        idParser,
        createErrorKey,
        updateErrorKey,
        exposedOperations,
        operationDocumentation,
        Map.of());
  }

  ApiV2ResourceRegistration<T, ID> bind(
      ResourceRegistry registry, ApiV2RelationshipResolver resolver) {
    return new ApiV2ResourceRegistration<>(this, registry, resolver);
  }

  OpenApiOperationDocumentation documentationFor(ResourceOperation operation) {
    return documentationFor(operation, operationDocumentation, errorMappings);
  }

  RuntimeException translate(ResourceOperation operation, RuntimeException exception) {
    List<ApiV2ErrorMapping> matches =
        errorMappings.getOrDefault(operation, List.of()).stream()
            .filter(mapping -> mapping.exceptionType().isInstance(exception))
            .toList();
    if (matches.isEmpty()) {
      return exception;
    }
    ExceptionDepthComparator comparator = new ExceptionDepthComparator(exception);
    ApiV2ErrorMapping closest =
        matches.stream()
            .min((left, right) -> comparator.compare(left.exceptionType(), right.exceptionType()))
            .orElseThrow();
    return closest.translate(exception);
  }

  private static Map<ResourceOperation, List<ApiV2ErrorMapping>> copyErrorMappings(
      Map<ResourceOperation, List<ApiV2ErrorMapping>> mappings) {
    Objects.requireNonNull(mappings, "Resource error mappings");
    Map<ResourceOperation, List<ApiV2ErrorMapping>> copy = new LinkedHashMap<>();
    mappings.forEach(
        (operation, errors) -> {
          Objects.requireNonNull(operation, "Resource error operation");
          List<ApiV2ErrorMapping> immutable = List.copyOf(errors);
          if (immutable.stream().map(ApiV2ErrorMapping::exceptionType).distinct().count()
              != immutable.size()) {
            throw new IllegalArgumentException(
                "Duplicate exception mapping for operation " + operation);
          }
          copy.put(operation, immutable);
        });
    return Map.copyOf(copy);
  }

  private static OpenApiOperationDocumentation documentationFor(
      ResourceOperation operation,
      Map<ResourceOperation, OpenApiOperationDocumentation> documentation,
      Map<ResourceOperation, List<ApiV2ErrorMapping>> mappings) {
    Map<Integer, OpenApiOperationDocumentation.Response> responses = new LinkedHashMap<>();
    mappings
        .getOrDefault(operation, List.of())
        .forEach(
            mapping -> {
              OpenApiOperationDocumentation.Response existing =
                  responses.putIfAbsent(mapping.status().value(), mapping.documentedResponse());
              if (existing != null && !existing.equals(mapping.documentedResponse())) {
                throw new IllegalArgumentException(
                    "Conflicting resource error mappings for status " + mapping.status().value());
              }
            });
    return documentation
        .getOrDefault(operation, OpenApiOperationDocumentation.EMPTY)
        .withResponses(responses);
  }

  private static String requireText(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " must not be blank");
    }
    return value;
  }
}
