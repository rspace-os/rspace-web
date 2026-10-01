package com.researchspace.api.v2.resource;

import com.researchspace.service.resourceaccess.ProtectedResourceAccess;
import java.util.Objects;
import java.util.Optional;

/** Optional registered access support for one protected REST API v2 resource. */
public record ResourceAccessSpec<T, ID>(
    ProtectedResourceAccess<T, ID> protectedResource,
    Optional<Class<?>> capabilitiesType,
    Optional<Class<?>> ownerHealthType) {

  public ResourceAccessSpec {
    Objects.requireNonNull(protectedResource, "Protected resource access");
    Objects.requireNonNull(capabilitiesType, "Capabilities type");
    Objects.requireNonNull(ownerHealthType, "Owner-health type");
  }

  public ResourceAccessSpec(ProtectedResourceAccess<T, ID> protectedResource) {
    this(protectedResource, Optional.empty(), Optional.empty());
  }

  public ResourceAccessSpec(
      ProtectedResourceAccess<T, ID> protectedResource,
      Class<?> capabilitiesType,
      Class<?> ownerHealthType) {
    this(
        protectedResource,
        Optional.of(Objects.requireNonNull(capabilitiesType, "Capabilities type")),
        Optional.of(Objects.requireNonNull(ownerHealthType, "Owner-health type")));
  }
}
