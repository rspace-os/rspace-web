package com.researchspace.api.v2.resource;

import java.util.Objects;

/** Singular mutation preconditions, lifecycle behavior and replay protection. */
public record MutationPolicy(
    VersionPolicy version, DeletePolicy delete, IdempotencyPolicy idempotency) {
  public static final MutationPolicy DEFAULT =
      new MutationPolicy(VersionPolicy.NONE, DeletePolicy.HARD, IdempotencyPolicy.NONE);

  public MutationPolicy {
    Objects.requireNonNull(version);
    Objects.requireNonNull(delete);
    Objects.requireNonNull(idempotency);
  }
}
