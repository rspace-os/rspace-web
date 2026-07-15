package com.researchspace.service.metadata;

/** A single S3 object to reference from a sidecar, as read from the folder listing. */
public record SidecarFileEntry(String key, Long sizeBytes, String etag, String storageClass) {}
