package com.researchspace.model.collection;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
    boolean readInherited,
    // Child rows never carry a guarantee; only the OpenAPI marker publishes it.
    @JsonIgnore boolean readGuaranteed,
    List<FieldSchema> fields)
    implements ResourceFieldSchema {}
