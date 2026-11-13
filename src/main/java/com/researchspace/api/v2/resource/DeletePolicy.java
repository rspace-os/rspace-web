package com.researchspace.api.v2.resource;

import java.util.Objects;

/** Lifecycle behavior and availability of explicit permanent deletion. */
public record DeletePolicy(Kind kind, boolean permanentOption) {
  public enum Kind {
    HARD,
    SOFT
  }

  public static final DeletePolicy HARD = new DeletePolicy(Kind.HARD, false);
  public static final DeletePolicy SOFT = new DeletePolicy(Kind.SOFT, false);

  public DeletePolicy {
    Objects.requireNonNull(kind);
  }

  public DeletePolicy withPermanentOption() {
    return new DeletePolicy(kind, true);
  }
}
