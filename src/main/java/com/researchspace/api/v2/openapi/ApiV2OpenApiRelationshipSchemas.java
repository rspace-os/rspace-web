package com.researchspace.api.v2.openapi;

import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ordered;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ref;

import com.researchspace.model.collection.RelationshipInputForm;
import com.researchspace.model.collection.RelationshipSchema;
import com.researchspace.model.collection.WriteOperation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Read and write schemas of relationship fields, including RSpace global identifier forms. */
final class ApiV2OpenApiRelationshipSchemas {

  private ApiV2OpenApiRelationshipSchemas() {}

  static Map<String, Object> relationshipOutput(
      RelationshipSchema relationship, Map<String, String> names) {
    List<Map<String, Object>> variants = new ArrayList<>();
    relationship
        .targetResources()
        .forEach(
            target -> {
              String prefix = relationship.globalIdPrefixesByTarget().get(target);
              variants.add(
                  prefix == null
                      ? ref(names.get(target) + "Reference")
                      : globalReferenceSchema(names.get(target) + "Reference", prefix, true));
              variants.add(
                  expandedReferenceSchema(target, ref(names.get(target) + "Read"), prefix));
            });
    return ordered("oneOf", variants);
  }

  static Map<String, Object> relationshipInput(
      RelationshipSchema relationship, WriteOperation operation, Map<String, String> names) {
    List<Map<String, Object>> variants = new ArrayList<>();
    Set<RelationshipInputForm> forms = relationship.inputForms().getOrDefault(operation, Set.of());
    if (forms.contains(RelationshipInputForm.GLOBAL_ID)) {
      String alternatives =
          relationship.globalIdPrefixesByTarget().values().stream()
              .map(ApiV2OpenApiRelationshipSchemas::escapeEcmaRegex)
              .collect(Collectors.joining("|"));
      variants.add(
          ordered(
              "type",
              "string",
              "pattern",
              "^(?:" + alternatives + ")\\d+$",
              "example",
              relationship.globalIdPrefixesByTarget().values().iterator().next() + "1",
              "description",
              "RSpace global identifier. Permitted prefixes: "
                  + String.join(", ", relationship.globalIdPrefixesByTarget().values())
                  + "."));
    }
    if (forms.contains(RelationshipInputForm.OBJECT)) {
      relationship
          .targetResources()
          .forEach(
              target -> {
                String referenceComponent = names.get(target) + "Reference";
                String prefix = relationship.globalIdPrefixesByTarget().get(target);
                variants.add(
                    prefix == null
                        ? ref(referenceComponent)
                        : globalReferenceSchema(referenceComponent, prefix, false));
              });
    }
    return variants.size() == 1 ? variants.get(0) : ordered("oneOf", variants);
  }

  private static Map<String, Object> globalReferenceSchema(
      String referenceComponent, String prefix, boolean globalIdRequired) {
    Map<String, Object> globalId =
        ordered("type", "object", "properties", ordered("globalId", globalIdSchema(prefix)));
    if (globalIdRequired) {
      globalId.put("required", List.of("globalId"));
    }
    return ordered("allOf", List.of(ref(referenceComponent), globalId));
  }

  private static Map<String, Object> expandedReferenceSchema(
      String target, Map<String, Object> value, String prefix) {
    List<String> required = new ArrayList<>(List.of("relationTo", "value"));
    Map<String, Object> properties =
        ordered("relationTo", ordered("type", "string", "const", target), "value", value);
    if (prefix != null) {
      required.add("globalId");
      properties.put("globalId", globalIdSchema(prefix));
    }
    return ordered("type", "object", "required", required, "properties", properties);
  }

  private static Map<String, Object> globalIdSchema(String prefix) {
    return ordered(
        "type",
        "string",
        "pattern",
        "^" + escapeEcmaRegex(prefix) + "\\d+$",
        "example",
        prefix + "1");
  }

  private static String escapeEcmaRegex(String value) {
    StringBuilder escaped = new StringBuilder();
    for (char character : value.toCharArray()) {
      if ("\\^$.*+?()[]{}|/".indexOf(character) >= 0) {
        escaped.append('\\');
      }
      escaped.append(character);
    }
    return escaped.toString();
  }
}
