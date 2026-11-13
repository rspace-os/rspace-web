package com.researchspace.api.v2.resource;

/** Explicit options for deleting one REST API v2 resource. */
public record ResourceDeleteOptions(Long expectedVersion, boolean permanent) {}
