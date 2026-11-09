package com.researchspace.model;

/** Published inside the transaction after a group membership or permission source changes. */
public record GroupPermissionsChangedEvent(Group group) {}
