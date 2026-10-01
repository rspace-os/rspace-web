package com.researchspace.model.collection;

import java.util.Set;

public record FieldSchema(
    String name,
    String property,
    CollectionFieldType.Schema type,
    boolean requiredOnCreate,
    boolean nullable,
    boolean readOnly,
    Set<WriteOperation> writeOperations,
    Object defaultValue,
    Set<Operator> filterOperators,
    boolean supportsWildcards,
    boolean sortable,
    OpenApiSchemaDocumentation openApi,
    AccessDocumentation readAccess,
    AccessDocumentation createAccess,
    AccessDocumentation updateAccess)
    implements ResourceFieldSchema {}
