package com.researchspace.api.v2.openapi;

import com.researchspace.api.v2.openapi.ApiV2OpenApiText.Key;
import com.researchspace.api.v2.resource.ApiV2ResourceRegistration;
import com.researchspace.api.v2.resource.DeletePolicy;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.core.util.EnglishInflector;
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
    String singular = EnglishInflector.singular(name);
    if (!EnglishInflector.plural(singular).equals(name)) {
      throw new IllegalArgumentException("Resource needs an explicit singular definition: " + name);
    }
    return pascalCase(singular);
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

  static String summary(
      ApiV2ResourceRegistration<?, ?> registration,
      ResourceOperation operation,
      ApiV2OpenApiText text) {
    boolean archives = archives(registration);
    Key key =
        switch (operation) {
          case RUNTIME_FIELDS -> throw runtimeFieldsDocumentedElsewhere();
          case LIST -> Key.LIST_SUMMARY;
          case COUNT -> Key.COUNT_SUMMARY;
          case READ -> Key.READ_SUMMARY;
          case CREATE -> Key.CREATE_SUMMARY;
          case BULK_CREATE -> Key.BULK_CREATE_SUMMARY;
          case UPDATE -> Key.UPDATE_SUMMARY;
          case BULK_UPDATE -> Key.BULK_UPDATE_SUMMARY;
          case DELETE -> archives ? Key.ARCHIVE_SUMMARY : Key.DELETE_SUMMARY;
          case BULK_DELETE -> archives ? Key.BULK_ARCHIVE_SUMMARY : Key.BULK_DELETE_SUMMARY;
        };
    return text.get(key, registration.resourceName());
  }

  static String description(
      ApiV2ResourceRegistration<?, ?> registration,
      ResourceOperation operation,
      ApiV2OpenApiText text) {
    boolean archives = archives(registration);
    Key key =
        switch (operation) {
          case RUNTIME_FIELDS -> throw runtimeFieldsDocumentedElsewhere();
          case LIST -> Key.LIST_DESCRIPTION;
          case COUNT -> Key.COUNT_DESCRIPTION;
          case READ -> Key.READ_DESCRIPTION;
          case CREATE -> Key.CREATE_DESCRIPTION;
          case BULK_CREATE -> Key.BULK_CREATE_DESCRIPTION;
          case UPDATE -> Key.UPDATE_DESCRIPTION;
          case BULK_UPDATE -> Key.BULK_UPDATE_DESCRIPTION;
          case DELETE ->
              !archives
                  ? Key.DELETE_DESCRIPTION
                  : registration.mutationPolicy().delete().permanentOption()
                      ? Key.ARCHIVE_PERMANENT_DESCRIPTION
                      : Key.ARCHIVE_DESCRIPTION;
          case BULK_DELETE -> archives ? Key.BULK_ARCHIVE_DESCRIPTION : Key.BULK_DELETE_DESCRIPTION;
        };
    return text.get(key, registration.resourceName());
  }

  /** The response description of a successful standard operation. */
  static String success(ResourceOperation operation, ApiV2OpenApiText text) {
    Key key =
        switch (operation) {
          case RUNTIME_FIELDS -> throw runtimeFieldsDocumentedElsewhere();
          case LIST -> Key.LIST_SUCCESS;
          case COUNT -> Key.COUNT_SUCCESS;
          case READ -> Key.READ_SUCCESS;
          case CREATE -> Key.CREATE_SUCCESS;
          case BULK_CREATE -> Key.BULK_CREATE_SUCCESS;
          case UPDATE -> Key.UPDATE_SUCCESS;
          case BULK_UPDATE -> Key.BULK_UPDATE_SUCCESS;
          case DELETE -> Key.DELETE_SUCCESS;
          case BULK_DELETE -> Key.BULK_DELETE_SUCCESS;
        };
    return text.get(key);
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
