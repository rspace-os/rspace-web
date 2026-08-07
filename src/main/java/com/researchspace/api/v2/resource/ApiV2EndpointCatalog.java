package com.researchspace.api.v2.resource;

import com.researchspace.api.v2.auth.ApiV2AuthenticationException;
import com.researchspace.model.User;
import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.AccessContext.Operation;
import com.researchspace.model.collection.AccessDocumentation.AuthenticationRequirement;
import com.researchspace.model.collection.AccessFunction;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.AccessResult;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.web.method.HandlerMethod;

/** Resolves and enforces the access function for REST API v2 controller handlers. */
public final class ApiV2EndpointCatalog {

  private static final ApiV2EndpointSpec DEFAULT_SPEC =
      new ApiV2EndpointSpec(Object.class, AccessFunction.authenticated());

  private final Map<Class<?>, ApiV2EndpointSpec> specsByHandlerType;

  public ApiV2EndpointCatalog(List<ApiV2EndpointSpec> specs) {
    Map<Class<?>, ApiV2EndpointSpec> registered = new LinkedHashMap<>();
    for (ApiV2EndpointSpec spec : specs) {
      if (registered.putIfAbsent(spec.handlerType(), spec) != null) {
        throw new IllegalArgumentException(
            "Duplicate REST API v2 endpoint spec for " + spec.handlerType().getName());
      }
    }
    specsByHandlerType = Map.copyOf(registered);
  }

  public void authorize(HttpServletRequest request, Object handler, User caller) {
    AccessResult result =
        spec(handler)
            .access()
            .check(
                new AccessContext(caller, operation(request.getMethod()), request.getRequestURI()));
    if (result instanceof AccessResult.Denied denied) {
      if (AccessPolicy.AUTHENTICATION_REQUIRED.equals(denied.reasonCode())) {
        throw new ApiV2AuthenticationException();
      }
      throw new AuthorizationException("REST API v2 endpoint access refused");
    }
    if (result.constraintOrEmpty().isPresent()) {
      throw new IllegalStateException("An endpoint access function cannot constrain resource rows");
    }
  }

  public boolean isPublic(Object handler) {
    return spec(handler).access().documentation().orElseThrow().authenticationRequirement()
        == AuthenticationRequirement.PUBLIC;
  }

  public ApiV2AuthenticationMode authenticationMode(Object handler) {
    return spec(handler).authenticationMode();
  }

  private ApiV2EndpointSpec spec(Object handler) {
    if (!(handler instanceof HandlerMethod handlerMethod)) {
      return DEFAULT_SPEC;
    }
    return specsByHandlerType.getOrDefault(handlerMethod.getBeanType(), DEFAULT_SPEC);
  }

  private static Operation operation(String method) {
    return switch (method) {
      case "POST" -> Operation.CREATE;
      case "PUT", "PATCH" -> Operation.UPDATE;
      case "DELETE" -> Operation.DELETE;
      default -> Operation.READ;
    };
  }
}
