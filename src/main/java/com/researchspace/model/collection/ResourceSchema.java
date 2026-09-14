package com.researchspace.model.collection;

import java.util.List;
import java.util.stream.Stream;

public record ResourceSchema(
    String name,
    Class<?> entityType,
    String idField,
    List<FieldSchema> fields,
    List<RelationshipSchema> relationships,
    List<FilterSchema> filters,
    List<Sort> defaultSort,
    AccessPolicySchema access) {

  /** All fields that can appear in a resource document, in stable schema order. */
  public List<ResourceFieldSchema> documentFields() {
    return Stream.<ResourceFieldSchema>concat(fields.stream(), relationships.stream()).toList();
  }
}
