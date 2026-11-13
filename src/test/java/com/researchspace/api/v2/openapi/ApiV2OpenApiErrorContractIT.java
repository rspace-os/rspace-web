package com.researchspace.api.v2.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.controller.FilesApiController;
import com.researchspace.api.v2.auth.ApiV2AuthenticationException;
import com.researchspace.api.v2.auth.ApiV2Authenticator;
import com.researchspace.api.v2.auth.ApiV2BrowserSessionAuthenticator;
import com.researchspace.api.v2.controller.ApiV2AuthenticationInterceptor;
import com.researchspace.api.v2.controller.ApiV2ControllerAdvice;
import com.researchspace.api.v2.controller.ApiV2PreAuthenticationThrottlingInterceptor;
import com.researchspace.api.v2.controller.ApiV2Problem;
import com.researchspace.api.v2.controller.UsersV2Controller;
import com.researchspace.api.v2.resource.ApiV2EndpointCatalog;
import com.researchspace.api.v2.throttling.APIRequestThrottler;
import com.researchspace.core.util.throttling.TooManyRequestsException;
import com.researchspace.webapp.controller.MVCTestBase;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.method.ControllerAdviceBean;

class ApiV2OpenApiErrorContractIT extends MVCTestBase {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  void selectedOpenApiHandlerRendersInvalidCredentialsAsUnauthorizedProblem() throws Exception {
    APIRequestThrottler preAuthenticationThrottler = mock(APIRequestThrottler.class);
    when(preAuthenticationThrottler.proceed(anyString())).thenReturn(true);
    ApiV2Authenticator authenticator = mock(ApiV2Authenticator.class);
    when(authenticator.authenticateIfPresent(any(HttpServletRequest.class)))
        .thenThrow(new ApiV2AuthenticationException());

    MvcResult result =
        openApiMockMvc(preAuthenticationThrottler, authenticator)
            .perform(
                get("/api/v2/openapi.json").header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
            .andReturn();

    assertProblem(result, 401, "errors.api.v2.authenticationRequired");
    assertEquals("Bearer", result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE));
    verify(preAuthenticationThrottler).proceed(anyString());
    verify(authenticator).authenticateIfPresent(any(HttpServletRequest.class));
  }

  @Test
  void selectedOpenApiHandlerRendersPreAuthenticationThrottleAsTooManyRequestsProblem()
      throws Exception {
    APIRequestThrottler preAuthenticationThrottler = mock(APIRequestThrottler.class);
    when(preAuthenticationThrottler.proceed(anyString()))
        .thenThrow(new TooManyRequestsException("test limit"));
    ApiV2Authenticator authenticator = mock(ApiV2Authenticator.class);

    MvcResult result =
        openApiMockMvc(preAuthenticationThrottler, authenticator)
            .perform(get("/api/v2/openapi.json"))
            .andReturn();

    assertProblem(result, 429, "errors.api.v2.tooManyRequests");
    verify(preAuthenticationThrottler).proceed(anyString());
    verifyNoInteractions(authenticator);
  }

  @Test
  void adviceIncludesOpenApiAndV2ControllersButExcludesV1Controllers() {
    ControllerAdviceBean adviceBean = adviceBean();

    assertTrue(adviceBean.isApplicableToBeanType(ApiV2OpenApiController.class));
    assertTrue(adviceBean.isApplicableToBeanType(UsersV2Controller.class));
    assertFalse(adviceBean.isApplicableToBeanType(FilesApiController.class));
  }

  private MockMvc openApiMockMvc(
      APIRequestThrottler preAuthenticationThrottler, ApiV2Authenticator authenticator) {
    ApiV2OpenApiController controller =
        new ApiV2OpenApiController(
            mock(ApiV2OpenApiDocumentService.class), OBJECT_MAPPER, new MockEnvironment());
    return MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(wac.getBean(ApiV2ControllerAdvice.class))
        .addInterceptors(
            new ApiV2PreAuthenticationThrottlingInterceptor(preAuthenticationThrottler),
            new ApiV2AuthenticationInterceptor(
                authenticator,
                mock(ApiV2BrowserSessionAuthenticator.class),
                wac.getBean(ApiV2EndpointCatalog.class)))
        .build();
  }

  private ControllerAdviceBean adviceBean() {
    DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
    beanFactory.registerSingleton(
        "apiV2ControllerAdvice", wac.getBean(ApiV2ControllerAdvice.class));
    return new ControllerAdviceBean(
        "apiV2ControllerAdvice",
        beanFactory,
        ApiV2ControllerAdvice.class.getAnnotation(ControllerAdvice.class));
  }

  private static void assertProblem(MvcResult result, int status, String code) throws Exception {
    assertEquals(status, result.getResponse().getStatus());
    assertEquals(
        ApiV2Problem.PROBLEM_JSON, MediaType.parseMediaType(result.getResponse().getContentType()));
    JsonNode problem = OBJECT_MAPPER.readTree(result.getResponse().getContentAsByteArray());
    assertEquals(status, problem.path("status").asInt());
    assertEquals(code, problem.path("code").asText());
    assertTrue(problem.path("title").isTextual());
  }
}
