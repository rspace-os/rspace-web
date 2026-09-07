package com.researchspace.model;

/** A user and their memberships have been flushed in the current transaction. */
public record UserSavedEvent(User user) {}
