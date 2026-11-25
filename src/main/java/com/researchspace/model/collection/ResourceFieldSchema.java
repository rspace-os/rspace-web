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

  /**
   * True when the member's read access is {@link AccessFunction#INHERITED}: it has no read
   * restriction of its own, so it is readable whenever its document is. {@link #readAccess()} holds
   * the resolved documentation and cannot tell the two apart.
   */
  boolean readInherited();

  /**
   * True when the member is declared read-guaranteed ({@code Field.readGuaranteed()}, {@code
   * Relationship.readGuaranteed()} or the matching annotation attribute). Implies {@link
   * #readInherited()}.
   */
  boolean readGuaranteed();

  default Object defaultValue() {
    return null;
  }
}
