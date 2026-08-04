package com.researchspace.api.v2.resource;

import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.ResourceRegistry;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
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
    Map<ResourceOperation, OpenApiOperationDocumentation> operationDocumentation) {

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
      String updateErrorKey) {
    this(
        description,
        operations,
        idParser,
        createErrorKey,
        updateErrorKey,
        STANDARD_OPERATIONS,
        Map.of());
  }

  ApiV2ResourceRegistration<T, ID> bind(
      ResourceRegistry registry, ApiV2RelationshipResolver resolver) {
    return new ApiV2ResourceRegistration<>(this, registry, resolver);
  }

  private static String requireText(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " must not be blank");
    }
    return value;
  }
}
