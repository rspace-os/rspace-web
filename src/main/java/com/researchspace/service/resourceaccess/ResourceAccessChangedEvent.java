package com.researchspace.service.resourceaccess;

/** One completed assignment mutation, published before its transaction commits. */
public record ResourceAccessChangedEvent(Object protectedResource) {}
