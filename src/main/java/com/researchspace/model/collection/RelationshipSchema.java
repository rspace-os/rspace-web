package com.researchspace.model.collection;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record RelationshipSchema(
    String name,
    List<String> targetResources,
    Map<String, String> globalIdPrefixesByTarget,
    boolean requiredOnCreate,
    boolean nullable,
    boolean readOnly,
    Set<WriteOperation> writeOperations,
    Map<WriteOperation, Set<RelationshipInputForm>> inputForms,
    boolean selfReferenceAllowed,
    OpenApiSchemaDocumentation openApi,
    AccessDocumentation readAccess,
    AccessDocumentation createAccess,
    AccessDocumentation updateAccess)
    implements ResourceFieldSchema {

  @Override
  public boolean nullableOnRead() {
    return true;
  }
}
