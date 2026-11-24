package com.researchspace.service.metadata;

/** A record that the sidecar names as related, by its Global ID page URL and DataCite relation. */
public record SidecarRelatedIdentifier(String url, String relationType) {}
