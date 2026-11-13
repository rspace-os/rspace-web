package com.researchspace.api.v2.resource;

import com.researchspace.model.collection.AccessFunction;
import java.util.Objects;

/** Coarse access policy applied before a REST API v2 controller handler is invoked. */
public record ApiV2EndpointSpec(
    Class<?> handlerType, AccessFunction access, ApiV2AuthenticationMode authenticationMode) {

  public ApiV2EndpointSpec(Class<?> handlerType, AccessFunction access) {
    this(handlerType, access, ApiV2AuthenticationMode.API_CREDENTIALS);
  }

  public ApiV2EndpointSpec {
    Objects.requireNonNull(handlerType, "Handler type");
    access = AccessFunction.requireDocumented(access);
    Objects.requireNonNull(authenticationMode, "Authentication mode");
  }
}
