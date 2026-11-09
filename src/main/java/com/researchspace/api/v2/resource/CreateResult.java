package com.researchspace.api.v2.resource;

/** Result of a singular create, including whether it replayed an earlier request. */
public record CreateResult<T>(T resource, boolean replayed) {}
