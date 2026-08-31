package com.researchspace.service.resourceaccess;

/** Mutation capabilities that are meaningful on a resource access document. */
public record ResourceAccessCallerCapabilities(
    boolean canManageAssignments, boolean canManageOwners, boolean canLeave) {}
