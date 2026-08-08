package com.researchspace.api.v2.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.service.MessageSourceUtils;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Covers the gap a package-selected {@code @ControllerAdvice} cannot: Spring only consults such an
 * advice when a {@code HandlerMethod} exists, so handler-mapping failures would otherwise fall
 * through to the container's HTML error page instead of an RFC 9457 body.
 */
class ApiV2PreHandlerProblemResolverTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private ApiV2PreHandlerProblemResolver resolver;

  @BeforeEach
  void setUp() {
    StaticMessageSource source = new StaticMessageSource();
    source.addMessage(
        "errors.api.v2.methodNotAllowed", Locale.getDefault(), "Method not allowed detail");
    source.addMessage("errors.api.v2.notFound", Locale.getDefault(), "Not found detail");
    source.addMessage("errors.api.v2.unexpected", Locale.getDefault(), "Unexpected detail");
    resolver =
        new ApiV2PreHandlerProblemResolver(
            new ApiV2ControllerAdvice(new MessageSourceUtils(source)), objectMapper);
  }

  @Test
  void writesProblemJsonWithTheStatusAndAllowHeaderForAnUnsupportedMethod() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    assertNotNull(
        resolver.resolveException(
            request("PUT", "/api/v2/maintenances/1"),
            response,
            null,
            new HttpRequestMethodNotSupportedException("PUT", List.of("GET", "PATCH"))));

    assertEquals(405, response.getStatus());
    assertTrue(
        MediaType.valueOf(response.getContentType()).isCompatibleWith(ApiV2Problem.PROBLEM_JSON),
        "content type was " + response.getContentType());
    // RFC 9110 allows optional whitespace after the comma; Spring emits "GET, PATCH".
    assertEquals(
        List.of("GET", "PATCH"), List.of(response.getHeader(HttpHeaders.ALLOW).split("\\s*,\\s*")));
    JsonNode body = objectMapper.readTree(response.getContentAsString());
    assertEquals(405, body.get("status").asInt());
    assertEquals("Method not allowed detail", body.get("detail").asText());
  }

  @Test
  void writesProblemJsonForUnmatchedV2Paths() throws Exception {
    MockHttpServletResponse missingHandler = new MockHttpServletResponse();
    resolver.resolveException(
        request("GET", "/api/v2/nope"),
        missingHandler,
        null,
        new NoHandlerFoundException("GET", "/api/v2/nope", new HttpHeaders()));
    assertEquals(404, missingHandler.getStatus());
    assertTrue(
        MediaType.valueOf(missingHandler.getContentType())
            .isCompatibleWith(ApiV2Problem.PROBLEM_JSON));

    MockHttpServletResponse missingResource = new MockHttpServletResponse();
    resolver.resolveException(
        request("GET", "/api/v2/nope"),
        missingResource,
        null,
        new NoResourceFoundException(HttpMethod.GET, "/api/v2/nope"));
    assertEquals(404, missingResource.getStatus());
  }

  @Test
  void writesProblemJsonForTheExactApiV2Root() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    assertNotNull(
        resolver.resolveException(
            request("GET", "/api/v2"),
            response,
            null,
            new NoHandlerFoundException("GET", "/api/v2", new HttpHeaders())));

    assertEquals(404, response.getStatus());
    assertTrue(
        MediaType.valueOf(response.getContentType()).isCompatibleWith(ApiV2Problem.PROBLEM_JSON));
  }

  @Test
  void leavesNonV2RequestsToTheirOwnErrorHandling() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    assertNull(
        resolver.resolveException(
            request("PUT", "/api/v1/documents/1"),
            response,
            null,
            new HttpRequestMethodNotSupportedException("PUT")));

    assertEquals(200, response.getStatus());
    assertEquals("", response.getContentAsString());
  }

  @Test
  void defersToTheControllerAdviceWhenAHandlerMethodExists() throws Exception {
    HandlerMethod handler =
        new HandlerMethod(new StubController(), StubController.class.getMethod("read"));

    assertNull(
        resolver.resolveException(
            request("GET", "/api/v2/maintenances"),
            new MockHttpServletResponse(),
            handler,
            new IllegalStateException("boom")));
  }

  @Test
  void honoursTheServletContextPathWhenMatchingTheApiPrefix() throws Exception {
    MockHttpServletRequest request = request("PUT", "/rspace/api/v2/maintenances/1");
    request.setContextPath("/rspace");
    MockHttpServletResponse response = new MockHttpServletResponse();

    assertNotNull(
        resolver.resolveException(
            request, response, null, new HttpRequestMethodNotSupportedException("PUT")));

    assertEquals(405, response.getStatus());
  }

  private static MockHttpServletRequest request(String method, String uri) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
    request.setRequestURI(uri);
    return request;
  }

  static class StubController {
    public void read() {}
  }
}
