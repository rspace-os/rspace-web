package com.researchspace.model.collection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds the public schema document for a collection description. */
final class CollectionSchemaBuilder<T> {
  private final String resourceName;
  private final Class<T> entityType;
  private final Map<String, Field<T, ?>> fields;
  private final Map<String, Relationship<T>> relationships;
  private final Map<String, FilterSelector<T>> filterSelectors;
  private final String idField;
  private final List<Sort> defaultSort;
  private final AccessPolicy accessPolicy;

  CollectionSchemaBuilder(
      String resourceName,
      Class<T> entityType,
      Map<String, Field<T, ?>> fields,
      Map<String, Relationship<T>> relationships,
      Map<String, FilterSelector<T>> filterSelectors,
      String idField,
      List<Sort> defaultSort,
      AccessPolicy accessPolicy) {
    this.resourceName = resourceName;
    this.entityType = entityType;
    this.fields = fields;
    this.relationships = relationships;
    this.filterSelectors = filterSelectors;
    this.idField = idField;
    this.defaultSort = defaultSort;
    this.accessPolicy = accessPolicy;
  }

  ResourceSchema build() {
    return new ResourceSchema(
        resourceName,
        entityType,
        idField,
        fields.values().stream().map(field -> field.schema(accessPolicy)).toList(),
        relationships.values().stream().map(this::relationshipSchema).toList(),
        filterSelectors.values().stream()
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

  private RelationshipSchema relationshipSchema(Relationship<T> relationship) {
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
