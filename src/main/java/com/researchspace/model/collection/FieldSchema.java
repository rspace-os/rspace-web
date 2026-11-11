package com.researchspace.model.collection;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record FieldSchema(
    String name,
    String property,
    CollectionFieldType.Schema type,
    Map<String, Object> writeConstraints,
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
    AccessDocumentation updateAccess,
    List<FieldSchema> fields)
    implements ResourceFieldSchema {}
