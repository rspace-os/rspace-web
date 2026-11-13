package com.researchspace.api.v2.resource;

import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionMutationLimits;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.RuntimeCollectionFields;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.springframework.core.ExceptionDepthComparator;
import org.springframework.util.Assert;

/** Everything needed to expose one collection through the standard REST API v2 routes. */
public record ApiV2ResourceSpec<T, ID>(
    CollectionDescription<T> description,
    ResourceOperations<T, ID> operations,
    Function<String, ID> idParser,
    String createErrorKey,
    String updateErrorKey,
    Set<ResourceOperation> exposedOperations,
    Map<ResourceOperation, OpenApiOperationDocumentation> operationDocumentation,
    Map<ResourceOperation, List<ApiV2ErrorMapping>> errorMappings,
    CollectionMutationLimits mutationLimits,
    List<RuntimeCollectionFields<T>> runtimeFields,
    Optional<ResourceAccessSpec<T, ID>> resourceAccess,
    MutationPolicy mutationPolicy,
    Optional<ScalarReader> scalarReader) {

  private static final Set<ResourceOperation> STANDARD_OPERATIONS =
      Set.copyOf(EnumSet.range(ResourceOperation.LIST, ResourceOperation.BULK_DELETE));

  public ApiV2ResourceSpec {
    Objects.requireNonNull(scalarReader, "Scalar reader");
    Objects.requireNonNull(mutationPolicy, "Mutation policy");
    Objects.requireNonNull(description, "Resource description");
    Objects.requireNonNull(operations, "Resource operations");
    Objects.requireNonNull(idParser, "ID parser");
    if (exposedOperations.contains(ResourceOperation.CREATE)
        || exposedOperations.contains(ResourceOperation.BULK_CREATE)) {
      Assert.hasText(createErrorKey, "Create error key must not be blank");
    }
    if (exposedOperations.contains(ResourceOperation.UPDATE)
        || exposedOperations.contains(ResourceOperation.BULK_UPDATE)) {
      Assert.hasText(updateErrorKey, "Update error key must not be blank");
    }
    Set<ResourceOperation> exposed = Set.copyOf(exposedOperations);
    exposedOperations = exposed;
    operationDocumentation = Map.copyOf(operationDocumentation);
    errorMappings = copyErrorMappings(errorMappings);
    Objects.requireNonNull(mutationLimits, "Collection mutation limits");
    runtimeFields = List.copyOf(Objects.requireNonNull(runtimeFields, "Runtime collection fields"));
    resourceAccess = Objects.requireNonNull(resourceAccess, "Resource access spec");
    Set<String> namespaces = new LinkedHashSet<>();
    for (RuntimeCollectionFields<T> provider : runtimeFields) {
      if (provider.namespace().isEmpty() || !namespaces.add(provider.namespace())) {
        throw new IllegalArgumentException(
            "Each runtime field provider needs its own non-empty namespace");
      }
    }
    if (!exposed.containsAll(operationDocumentation.keySet())
        || !exposed.containsAll(errorMappings.keySet())) {
      throw new IllegalArgumentException(
          "Cannot configure an operation the resource does not expose");
    }
    for (ResourceOperation operation : errorMappings.keySet()) {
      documentationFor(operation, operationDocumentation, errorMappings);
    }
  }

  public static <T, ID> Builder<T, ID> builder(
      CollectionDescription<T> description,
      ResourceOperations<T, ID> operations,
      Function<String, ID> idParser) {
    return new Builder<>(description, operations, idParser);
  }

  public static <T, ID> Builder<T, ID> readOnly(
      CollectionDescription<T> description,
      ResourceOperations<T, ID> operations,
      Function<String, ID> idParser) {
    return builder(description, operations, idParser)
        .expose(Set.of(ResourceOperation.LIST, ResourceOperation.COUNT, ResourceOperation.READ));
  }

  public static final class Builder<T, ID> {
    private final CollectionDescription<T> description;
    private final ResourceOperations<T, ID> operations;
    private final Function<String, ID> idParser;
    private String createErrorKey;
    private String updateErrorKey;
    private String tagDescription;
    private String tagGroup;
    private Set<ResourceOperation> exposedOperations = STANDARD_OPERATIONS;
    private final Map<ResourceOperation, OpenApiOperationDocumentation> documentation =
        new LinkedHashMap<>();
    private final Map<ResourceOperation, List<ApiV2ErrorMapping>> errors = new LinkedHashMap<>();
    private CollectionMutationLimits mutationLimits = CollectionMutationLimits.DEFAULT;
    private List<RuntimeCollectionFields<T>> runtimeFields = List.of();
    private Optional<ResourceAccessSpec<T, ID>> resourceAccess = Optional.empty();
    private MutationPolicy mutationPolicy = MutationPolicy.DEFAULT;
    private Optional<ScalarReader> scalarReader = Optional.empty();

    private Builder(
        CollectionDescription<T> description,
        ResourceOperations<T, ID> operations,
        Function<String, ID> idParser) {
      this.description = description;
      this.operations = operations;
      this.idParser = idParser;
    }

    public Builder<T, ID> expose(Set<ResourceOperation> operations) {
      this.exposedOperations = operations;
      return this;
    }

    public Builder<T, ID> mutationErrors(String createKey, String updateKey) {
      this.createErrorKey = createKey;
      this.updateErrorKey = updateKey;
      return this;
    }

    public Builder<T, ID> tagDocumentation(String description, String group) {
      this.tagDescription = description;
      this.tagGroup = group;
      return this;
    }

    public Builder<T, ID> documentation(
        ResourceOperation operation, OpenApiOperationDocumentation doc) {
      documentation.put(operation, doc);
      return this;
    }

    public Builder<T, ID> errorMappings(
        ResourceOperation operation, List<ApiV2ErrorMapping> mappings) {
      errors.put(operation, mappings);
      return this;
    }

    public Builder<T, ID> mutationLimits(CollectionMutationLimits limits) {
      this.mutationLimits = limits;
      return this;
    }

    public Builder<T, ID> runtimeFields(List<RuntimeCollectionFields<T>> providers) {
      this.runtimeFields = providers;
      return this;
    }

    public Builder<T, ID> resourceAccess(ResourceAccessSpec<T, ID> access) {
      this.resourceAccess = Optional.of(access);
      return this;
    }

    public Builder<T, ID> mutationPolicy(MutationPolicy policy) {
      mutationPolicy = policy;
      return this;
    }

    public Builder<T, ID> scalarReader(ScalarReader reader) {
      scalarReader = Optional.of(reader);
      return this;
    }

    public ApiV2ResourceSpec<T, ID> build() {
      Map<ResourceOperation, OpenApiOperationDocumentation> taggedDocumentation =
          new LinkedHashMap<>(documentation);
      if (tagDescription != null || tagGroup != null) {
        for (ResourceOperation operation : exposedOperations) {
          taggedDocumentation.put(
              operation,
              documentation
                  .getOrDefault(operation, OpenApiOperationDocumentation.EMPTY)
                  .withTagDocumentation(tagDescription, tagGroup));
        }
      }

      return new ApiV2ResourceSpec<>(
          description,
          operations,
          idParser,
          createErrorKey,
          updateErrorKey,
          exposedOperations,
          taggedDocumentation,
          errors,
          mutationLimits,
          runtimeFields,
          resourceAccess,
          mutationPolicy,
          scalarReader);
    }
  }

  ApiV2ResourceRegistration<T, ID> bind(
      ResourceRegistry registry, ApiV2RelationshipResolver resolver) {
    return new ApiV2ResourceRegistration<>(this, registry, resolver);
  }

  ApiV2ResourceRegistration<T, ID> bind(
      ResourceRegistry registry,
      ApiV2RelationshipResolver resolver,
      Function<String, List<RuntimeCollectionFields<?>>> providersByResource) {
    return new ApiV2ResourceRegistration<>(this, registry, resolver, providersByResource);
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
              responses.merge(
                  mapping.status().value(),
                  mapping.documentedResponse(),
                  OpenApiOperationDocumentation.Response::merge);
            });
    return documentation
        .getOrDefault(operation, OpenApiOperationDocumentation.EMPTY)
        .withResponses(responses);
  }
}
