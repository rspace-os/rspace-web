package com.researchspace.model.collection;

import java.util.Objects;

/** A relationship reference after the REST layer confirms that its target is readable. */
public record ResolvedResourceReference<K, ID>(ResourceReference<K, ID> reference, Object entity) {

  public ResolvedResourceReference {
    Objects.requireNonNull(reference, "Resource reference");
    Objects.requireNonNull(entity, "Resolved entity");
  }

  public <R> R entityAs(Class<R> entityType) {
    return Objects.requireNonNull(entityType, "Entity type").cast(entity);
  }
}
