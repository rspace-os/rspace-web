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

  private static final AccessFunction DEFAULT_ACCESS = AccessFunction.authenticated();

  private final Map<Class<?>, AccessFunction> accessByHandlerType;

  public ApiV2EndpointCatalog(List<ApiV2EndpointSpec> specs) {
    Map<Class<?>, AccessFunction> access = new LinkedHashMap<>();
    for (ApiV2EndpointSpec spec : specs) {
      if (access.putIfAbsent(spec.handlerType(), spec.access()) != null) {
        throw new IllegalArgumentException(
            "Duplicate REST API v2 endpoint spec for " + spec.handlerType().getName());
      }
    }
    accessByHandlerType = Map.copyOf(access);
  }

  public void authorize(HttpServletRequest request, Object handler, User caller) {
    AccessResult result =
        access(handler)
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
    return access(handler).documentation().orElseThrow().authenticationRequirement()
        == AuthenticationRequirement.PUBLIC;
  }

  private AccessFunction access(Object handler) {
    if (!(handler instanceof HandlerMethod handlerMethod)) {
      return DEFAULT_ACCESS;
    }
    return accessByHandlerType.getOrDefault(handlerMethod.getBeanType(), DEFAULT_ACCESS);
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
