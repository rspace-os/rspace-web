package com.researchspace.api.v2.resource;

import com.researchspace.service.resourceaccess.ProtectedResourceAccess;
import java.util.Objects;

/** Optional registered access support for one protected REST API v2 resource. */
public record ResourceAccessSpec<T, ID>(ProtectedResourceAccess<T, ID> protectedResource) {

  public ResourceAccessSpec {
    Objects.requireNonNull(protectedResource, "Protected resource access");
  }
}
