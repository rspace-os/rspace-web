package com.researchspace.api.v2.resource;

import java.util.Objects;
import java.util.Optional;

/** Result of deleting one resource, distinguishing a retained soft-deleted row from removal. */
public final class ResourceDeleteResult<T> {

  private final T resource;
  private final boolean permanentlyDeleted;

  private ResourceDeleteResult(T resource, boolean permanentlyDeleted) {
    this.resource = resource;
    this.permanentlyDeleted = permanentlyDeleted;
  }

  public static <T> ResourceDeleteResult<T> retained(T resource) {
    return new ResourceDeleteResult<>(Objects.requireNonNull(resource, "Resource"), false);
  }

  public static <T> ResourceDeleteResult<T> permanentlyDeleted() {
    return new ResourceDeleteResult<>(null, true);
  }

  public Optional<T> resource() {
    return Optional.ofNullable(resource);
  }

  public boolean isPermanentlyDeleted() {
    return permanentlyDeleted;
  }
}
