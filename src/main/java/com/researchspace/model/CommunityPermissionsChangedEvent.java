package com.researchspace.model;

/** Published inside the transaction after a community membership or permission source changes. */
public record CommunityPermissionsChangedEvent(Community community) {}
