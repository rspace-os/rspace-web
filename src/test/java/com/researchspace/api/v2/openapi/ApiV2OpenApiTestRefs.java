package com.researchspace.api.v2.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Resolves the component references of a generated document for tests that read its content. */
public final class ApiV2OpenApiTestRefs {

  private static final String COMPONENTS = "#/components/";
  private static final String SCHEMAS = COMPONENTS + "schemas/";

  private ApiV2OpenApiTestRefs() {}

  /**
   * A copy of {@code document} with every local component reference replaced by its target, except
   * schema references, which may be recursive.
   */
  public static JsonNode dereferenced(JsonNode document) {
    return dereference(document, document.deepCopy());
  }

  private static JsonNode dereference(JsonNode document, JsonNode node) {
    if (node instanceof ObjectNode object) {
      String ref = object.path("$ref").asText("");
      if (object.size() == 1 && ref.startsWith(COMPONENTS) && !ref.startsWith(SCHEMAS)) {
        JsonNode target = document.at(ref.substring(1));
        if (target.isMissingNode()) {
          throw new AssertionError("Unresolved OpenAPI reference " + ref);
        }
        return dereference(document, target.deepCopy());
      }
      object.properties().forEach(entry -> entry.setValue(dereference(document, entry.getValue())));
      return object;
    }
    if (node instanceof ArrayNode array) {
      for (int index = 0; index < array.size(); index++) {
        array.set(index, dereference(document, array.get(index)));
      }
    }
    return node;
  }
}
