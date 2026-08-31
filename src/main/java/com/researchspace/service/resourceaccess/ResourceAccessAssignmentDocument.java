package com.researchspace.service.resourceaccess;

/** One read-side assignment row. */
public record ResourceAccessAssignmentDocument(
    ResourceAccessGranteeDocument grantee, String role) {}
