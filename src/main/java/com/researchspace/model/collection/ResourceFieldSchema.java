package com.researchspace.model.collection;

import java.util.Set;

/** Metadata shared by every field rendered in a resource document. */
public sealed interface ResourceFieldSchema permits FieldSchema, RelationshipSchema {

  String name();

  boolean requiredOnCreate();

  boolean nullable();

  default boolean nullableOnRead() {
    return nullable();
  }

  boolean readOnly();

  Set<WriteOperation> writeOperations();

  OpenApiSchemaDocumentation openApi();

  AccessDocumentation readAccess();

  AccessDocumentation createAccess();

  AccessDocumentation updateAccess();

  default Object defaultValue() {
    return null;
  }
}
