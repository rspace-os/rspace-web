package com.researchspace.api.v2.openapi;

import com.researchspace.api.v2.resource.ApiV2ResourceRegistration;
import com.researchspace.api.v2.resource.DeletePolicy;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.model.collection.CollectionDescription;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Component names, operation IDs and default operation text for registered resources. */
final class ApiV2OpenApiNames {

  private ApiV2OpenApiNames() {}

  static Map<String, String> componentNames(Collection<CollectionDescription<?>> resources) {
    Map<String, String> result = new LinkedHashMap<>();
    Set<String> used = new LinkedHashSet<>();
    for (CollectionDescription<?> resource : resources) {
      String component = pascalCase(resource.resourceName());
      if (!used.add(component)) {
        throw new IllegalArgumentException("OpenAPI component name collision: " + component);
      }
      result.put(resource.resourceName(), component);
    }
    return Map.copyOf(result);
  }

  static String singularName(ApiV2ResourceRegistration<?, ?> resource) {
    String name = resource.resourceName();
    if (!resource.description().singular().isBlank())
      return pascalCase(resource.description().singular());
    if (!name.endsWith("s") || name.endsWith("ss") || name.endsWith("ies")) {
      throw new IllegalArgumentException("Resource needs an explicit singular definition: " + name);
    }
    return pascalCase(name.substring(0, name.length() - 1));
  }

  private static String pascalCase(String value) {
    StringBuilder result = new StringBuilder();
    for (String part : value.split("[^A-Za-z0-9]+")) {
      if (!part.isEmpty()) {
        result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
      }
    }
    if (result.isEmpty() || Character.isDigit(result.charAt(0))) {
      throw new IllegalArgumentException("Invalid OpenAPI component name for " + value);
    }
    return result.toString();
  }

  static String operationId(ResourceOperation operation, String component, String singular) {
    return switch (operation) {
      case RUNTIME_FIELDS -> throw runtimeFieldsDocumentedElsewhere();
      case LIST -> "list" + component;
      case COUNT -> "count" + component;
      case READ -> "get" + singular;
      case CREATE -> "create" + singular;
      case BULK_CREATE -> "createMany" + component;
      case UPDATE -> "update" + singular;
      case BULK_UPDATE -> "updateMany" + component;
      case DELETE -> "delete" + singular;
      case BULK_DELETE -> "deleteMany" + component;
    };
  }

  static String summary(ApiV2ResourceRegistration<?, ?> registration, ResourceOperation operation) {
    String resource = registration.resourceName();
    return switch (operation) {
      case RUNTIME_FIELDS -> throw runtimeFieldsDocumentedElsewhere();
      case LIST -> "List " + resource;
      case COUNT -> "Count " + resource;
      case READ -> "Get one " + resource;
      case CREATE -> "Create one " + resource;
      case BULK_CREATE -> "Create many " + resource;
      case UPDATE -> "Update one " + resource;
      case BULK_UPDATE -> "Update matching " + resource;
      case DELETE -> (archives(registration) ? "Archive one " : "Delete one ") + resource;
      case BULK_DELETE ->
          (archives(registration) ? "Archive matching " : "Delete matching ") + resource;
    };
  }

  static String description(
      ApiV2ResourceRegistration<?, ?> registration, ResourceOperation operation) {
    String resource = registration.resourceName();
    return switch (operation) {
      case RUNTIME_FIELDS -> throw runtimeFieldsDocumentedElsewhere();
      case LIST ->
          "Returns a paginated view of "
              + resource
              + ". Filtering, sorting, sparse fieldsets, exclusions, and relationship depth are"
              + " applied before rendering.";
      case COUNT -> "Counts " + resource + " matching the optional bounded filter.";
      case READ ->
          "Returns one visible "
              + resource
              + ". A resource that is absent or not readable is reported as not found.";
      case CREATE -> "Creates one " + resource + " from the documented create schema.";
      case BULK_CREATE ->
          "Creates the supplied "
              + resource
              + " documents in input order. The batch is handled as one service-layer mutation;"
              + " validation failure prevents the entire batch from being created.";
      case UPDATE -> "Partially updates one " + resource + " and returns the updated document.";
      case BULK_UPDATE ->
          "Updates every "
              + resource
              + " matching the required filter. The operation is handled as one service-layer"
              + " mutation; resource-specific limits are reported with status 422.";
      case DELETE ->
          archives(registration)
              ? "Archives one "
                  + resource
                  + " and returns the archived document. Repeating ordinary DELETE is a no-op."
                  + (registration.mutationPolicy().delete().permanentOption()
                      ? " The explicit permanent=true option permanently removes the resource for"
                          + " a direct sysadmin and returns no document."
                      : "")
              : "Deletes one " + resource + " and returns the deleted document.";
      case BULK_DELETE ->
          (archives(registration) ? "Archives every " : "Deletes every ")
              + resource
              + " matching the required filter. The operation is handled as one service-layer"
              + " mutation; resource-specific limits are reported with status 422.";
    };
  }

  static String requireText(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " must not be blank");
    }
    return value;
  }

  private static boolean archives(ApiV2ResourceRegistration<?, ?> registration) {
    return registration.mutationPolicy().delete().kind() == DeletePolicy.Kind.SOFT;
  }

  private static IllegalArgumentException runtimeFieldsDocumentedElsewhere() {
    return new IllegalArgumentException("Runtime fields are documented from registered providers");
  }
}
