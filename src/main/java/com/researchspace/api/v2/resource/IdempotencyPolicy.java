package com.researchspace.api.v2.resource;

/** Scope of singular create replay protection. */
public enum IdempotencyPolicy {
  NONE,
  CREATOR_SCOPED
}
