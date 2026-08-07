package com.researchspace.api.v2.controller;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v2.auth.ApiV2AuthenticationException;
import com.researchspace.api.v2.auth.ApiV2Authenticator;
import com.researchspace.api.v2.auth.ApiV2BrowserSessionAuthenticator;
import com.researchspace.api.v2.openapi.ApiV2OpenApiController;
import com.researchspace.api.v2.openapi.ApiV2OpenApiDocumentService;
import com.researchspace.api.v2.resource.ApiV2AuthenticationMode;
import com.researchspace.api.v2.resource.ApiV2EndpointCatalog;
import com.researchspace.api.v2.resource.ApiV2EndpointSpec;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.model.User;
import com.researchspace.model.collection.AccessFunction;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

class ApiV2AuthenticationInterceptorTest {

  private final ApiV2Authenticator authenticator = mock(ApiV2Authenticator.class);
  private final ApiV2BrowserSessionAuthenticator browserSessionAuthenticator =
      mock(ApiV2BrowserSessionAuthenticator.class);
  private final ApiV2EndpointCatalog endpoints =
      new ApiV2EndpointCatalog(
          List.of(
              new ApiV2EndpointSpec(PublicController.class, AccessFunction.anyone()),
              new ApiV2EndpointSpec(
                  BrowserSessionController.class,
                  AccessFunction.authenticated(),
                  ApiV2AuthenticationMode.BROWSER_SESSION),
              new ApiV2EndpointSpec(ApiV2CrudController.class, AccessFunction.anyone()),
              new ApiV2EndpointSpec(ApiV2OpenApiController.class, AccessFunction.anyone())));
  private final ApiV2AuthenticationInterceptor interceptor =
      new ApiV2AuthenticationInterceptor(authenticator, browserSessionAuthenticator, endpoints);
  private final MockHttpServletRequest request = new MockHttpServletRequest();
  private final MockHttpServletResponse response = new MockHttpServletResponse();

  @Test
  void allowsAnAnonymousCallerWhenTheEndpointSpecIsPublic() throws Exception {
    when(authenticator.authenticateIfPresent(request)).thenReturn(Optional.empty());
    HandlerMethod handler =
        new HandlerMethod(new PublicController(), PublicController.class.getMethod("read"));

    assertTrue(interceptor.preHandle(request, response, handler));
    verify(authenticator).authenticateIfPresent(request);
  }

  @Test
  void openApiDocumentIsPublic() throws Exception {
    ApiV2OpenApiController controller =
        new ApiV2OpenApiController(
            mock(ApiV2OpenApiDocumentService.class), new ObjectMapper(), new MockEnvironment());
    HandlerMethod handler =
        new HandlerMethod(
            controller,
            ApiV2OpenApiController.class.getMethod("openApi", HttpServletRequest.class));

    assertTrue(interceptor.preHandle(request, response, handler));

    verify(authenticator).authenticateIfPresent(request);
  }

  @Test
  void rejectsAnAnonymousCallerUsingTheDefaultEndpointPolicy() throws Exception {
    when(authenticator.authenticateIfPresent(request)).thenReturn(Optional.empty());

    assertThrows(
        ApiV2AuthenticationException.class,
        () -> interceptor.preHandle(request, response, protectedHandler()));
  }

  @Test
  void attachesAnAuthenticatedCallerBeforeApplyingTheDefaultPolicy() throws Exception {
    User user = mock(User.class);
    when(authenticator.authenticateIfPresent(request)).thenReturn(Optional.of(user));
    HandlerMethod handler = protectedHandler();

    assertTrue(interceptor.preHandle(request, response, handler));
    assertSame(user, request.getAttribute("user"));
  }

  @Test
  void genericCrudDefersMissingAuthenticationToAccessFunctions() throws Exception {
    when(authenticator.authenticateIfPresent(request)).thenReturn(Optional.empty());

    assertTrue(interceptor.preHandle(request, response, crudHandler("list")));

    verify(authenticator).authenticateIfPresent(request);
  }

  @Test
  void genericCrudAddsAnAuthenticatedCallerWhenPresent() throws Exception {
    User user = mock(User.class);
    when(authenticator.authenticateIfPresent(request)).thenReturn(Optional.of(user));

    assertTrue(interceptor.preHandle(request, response, crudHandler("create")));

    assertSame(user, request.getAttribute("user"));
  }

  @Test
  void usesOnlyBrowserSessionAuthenticationForTheTokenEndpoint() throws Exception {
    User user = mock(User.class);
    when(browserSessionAuthenticator.authenticateIfPresent(request)).thenReturn(Optional.of(user));
    HandlerMethod handler =
        new HandlerMethod(
            new BrowserSessionController(), BrowserSessionController.class.getMethod("create"));

    assertTrue(interceptor.preHandle(request, response, handler));

    assertSame(user, request.getAttribute("user"));
    verify(browserSessionAuthenticator).authenticateIfPresent(request);
    verifyNoInteractions(authenticator);
  }

  private static HandlerMethod protectedHandler() throws Exception {
    return new HandlerMethod(
        new ProtectedController(), ProtectedController.class.getMethod("read"));
  }

  private HandlerMethod crudHandler(String methodName) {
    Method method =
        Arrays.stream(ApiV2CrudController.class.getDeclaredMethods())
            .filter(candidate -> candidate.getName().equals(methodName))
            .findFirst()
            .orElseThrow();
    return new HandlerMethod(new ApiV2CrudController(mock(ApiV2ResourceCatalog.class)), method);
  }

  static class PublicController {
    public void read() {}
  }

  static class ProtectedController {
    public void read() {}
  }

  static class BrowserSessionController {
    public void create() {}
  }
}
