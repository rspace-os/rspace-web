package com.researchspace.api.v2.controller;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v2.auth.ApiV2Authenticator;
import com.researchspace.api.v2.openapi.ApiV2OpenApiController;
import com.researchspace.api.v2.openapi.ApiV2OpenApiDocumentService;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.model.User;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

class ApiV2AuthenticationInterceptorTest {

  private final ApiV2Authenticator authenticator = mock(ApiV2Authenticator.class);
  private final ApiV2AuthenticationInterceptor interceptor =
      new ApiV2AuthenticationInterceptor(authenticator);
  private final MockHttpServletRequest request = new MockHttpServletRequest();
  private final MockHttpServletResponse response = new MockHttpServletResponse();

  @Test
  void skipsAuthenticationForPublicHandlers() throws Exception {
    HandlerMethod handler =
        new HandlerMethod(new PublicController(), PublicController.class.getMethod("read"));

    assertTrue(interceptor.preHandle(request, response, handler));
    verify(authenticator, never()).authenticate(request);
  }

  @Test
  void skipsAuthenticationForMethodLevelPublicHandlers() throws Exception {
    HandlerMethod handler =
        new HandlerMethod(
            new MethodPublicController(), MethodPublicController.class.getMethod("read"));

    assertTrue(interceptor.preHandle(request, response, handler));

    verify(authenticator, never()).authenticate(request);
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

    verify(authenticator, never()).authenticate(request);
    verify(authenticator, never()).authenticateIfPresent(request);
  }

  @Test
  void authenticatesProtectedHandlers() throws Exception {
    User user = mock(User.class);
    when(authenticator.authenticate(request)).thenReturn(user);
    HandlerMethod handler = protectedHandler();

    assertTrue(interceptor.preHandle(request, response, handler));
    assertSame(user, request.getAttribute("user"));
  }

  @Test
  void genericCrudDefersMissingAuthenticationToAccessFunctions() throws Exception {
    when(authenticator.authenticateIfPresent(request)).thenReturn(Optional.empty());

    assertTrue(interceptor.preHandle(request, response, crudHandler("list")));

    verify(authenticator).authenticateIfPresent(request);
    verify(authenticator, never()).authenticate(request);
  }

  @Test
  void genericCrudAddsAnAuthenticatedCallerWhenPresent() throws Exception {
    User user = mock(User.class);
    when(authenticator.authenticateIfPresent(request)).thenReturn(Optional.of(user));

    assertTrue(interceptor.preHandle(request, response, crudHandler("create")));

    assertSame(user, request.getAttribute("user"));
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

  @ApiV2Access(ApiV2Access.Mode.PUBLIC)
  static class PublicController {
    public void read() {}
  }

  static class ProtectedController {
    public void read() {}
  }

  static class MethodPublicController {
    @ApiV2Access(ApiV2Access.Mode.PUBLIC)
    public void read() {}
  }
}
