package com.researchspace.model.collection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds the public schema document for a collection description. */
final class CollectionSchemaBuilder {

  static <T> ResourceSchema build(
      String resourceName,
      Class<T> entityType,
      Map<String, ResourceMember<T>> fields,
      Map<String, FilterSelector<T>> publicFilterSelectors,
      String idField,
      List<Sort> defaultSort,
      AccessPolicy accessPolicy) {
    List<FieldSchema> fieldSchemas = new ArrayList<>();
    List<RelationshipSchema> relationshipSchemas = new ArrayList<>();
    for (ResourceMember<T> member : fields.values()) {
      if (member instanceof Field<T, ?> field) {
        fieldSchemas.add(field.schema(accessPolicy));
      } else if (member instanceof Relationship<T> relationship) {
        relationshipSchemas.add(relationshipSchema(relationship, accessPolicy));
      }
    }
    return new ResourceSchema(
        resourceName,
        entityType,
        idField,
        List.copyOf(fieldSchemas),
        List.copyOf(relationshipSchemas),
        publicFilterSelectors.values().stream()
            .map(
                selector ->
                    new FilterSchema(
                        selector.name(), selector.operators(), selector.supportsWildcards()))
            .toList(),
        defaultSort,
        new AccessPolicySchema(
            documented(accessPolicy.readAccess()),
            documented(accessPolicy.createAccess()),
            documented(accessPolicy.updateAccess()),
            documented(accessPolicy.deleteAccess()),
            documented(accessPolicy.softDeleteAccess())));
  }

  private static <T> RelationshipSchema relationshipSchema(
      Relationship<T> relationship, AccessPolicy accessPolicy) {
    return new RelationshipSchema(
        relationship.name(),
        relationship.targets().stream().map(RelationshipTarget::resourceName).toList(),
        globalIdPrefixesByTarget(relationship.targets()),
        relationship.isRequiredOnCreate(),
        relationship.nullable(),
        !relationship.writableOn(WriteOperation.CREATE)
            && !relationship.writableOn(WriteOperation.UPDATE),
        relationship.writeOperations,
        relationship.inputForms,
        relationship.selfReferenceAllowed(),
        relationship.openApi,
        documented(relationship.readAccess, accessPolicy.readAccess()),
        documented(relationship.writeAccess, accessPolicy.createAccess()),
        documented(relationship.writeAccess, accessPolicy.updateAccess()));
  }

  private static Map<String, String> globalIdPrefixesByTarget(List<RelationshipTarget<?>> targets) {
    Map<String, String> prefixes = new LinkedHashMap<>();
    targets.stream()
        .filter(target -> target.globalIdPrefix() != null)
        .forEach(target -> prefixes.put(target.resourceName(), target.globalIdPrefix()));
    return Collections.unmodifiableMap(prefixes);
  }

  static AccessDocumentation documented(AccessFunction function) {
    return function
        .documentation()
        .orElseThrow(() -> new IllegalStateException("Access function is not documented"));
  }

  static AccessDocumentation documented(AccessFunction function, AccessFunction inheritedFunction) {
    return function == AccessFunction.INHERITED
        ? documented(inheritedFunction)
        : documented(function);
  }
}
