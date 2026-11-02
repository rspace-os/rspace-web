package com.researchspace.model.collection;

public record AccessPolicySchema(
    AccessDocumentation readAccess,
    AccessDocumentation createAccess,
    AccessDocumentation updateAccess,
    AccessDocumentation deleteAccess,
    AccessDocumentation softDeleteAccess) {}
