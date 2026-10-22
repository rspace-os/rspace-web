package com.researchspace.api.v2.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v2.json.ApiV2JsonBody;
import com.researchspace.api.v2.json.ApiV2JsonMappers;
import com.researchspace.service.MessageSourceUtils;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
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

  @Test
  void limitsApiV2BodyDepthAndStringLengthBeforeTheControllerRuns() throws Exception {
    MockMvc mvc = bodyMvc();
    String atLimit = "[".repeat(100) + "0" + "]".repeat(100);
    assertEquals(200, postBody(mvc, "/api/v2/test-body", atLimit).getStatus());
    for (String body :
        List.of("[" + atLimit + "]", "\"" + "x".repeat(2 * 1024 * 1024 + 1) + "\"")) {
      MockHttpServletResponse response = postBody(mvc, "/api/v2/test-body", body);
      assertEquals(400, response.getStatus());
      assertEquals(
          "errors.api.v2.invalidRequest",
          objectMapper.readTree(response.getContentAsString()).path("code").asText(),
          response.getContentAsString());
    }
  }

  @Test
  void leavesOrdinaryJsonNodeBindingAndCollectionDecimalsUnchanged() throws Exception {
    MockMvc mvc = bodyMvc();
    String nested = "[".repeat(101) + "0" + "]".repeat(101);
    assertEquals(200, postBody(mvc, "/api/v2/test-tree", nested).getStatus());
    assertEquals("15", postBody(mvc, "/api/v2/test-body", "15.0").getContentAsString());
  }

  @Test
  void preservesExplicitNullTreesWhileRejectingAnEmptyHttpBody() throws Exception {
    MockMvc mvc = bodyMvc();
    MockHttpServletResponse explicitNull = postBody(mvc, "/api/v2/test-body", "null");
    assertEquals(200, explicitNull.getStatus());
    assertEquals("null", explicitNull.getContentAsString());

    MockHttpServletResponse empty = postBody(mvc, "/api/v2/test-body", "");
    assertEquals(400, empty.getStatus());
    assertEquals(
        "errors.api.v2.invalidRequest",
        objectMapper.readTree(empty.getContentAsString()).path("code").asText());
  }

  private MockMvc bodyMvc() {
    StaticMessageSource source = new StaticMessageSource();
    source.addMessage("errors.api.v2.invalidRequest", Locale.ENGLISH, "Invalid request");
    MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
    converter
        .getObjectMapper()
        .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    ApiV2JsonMappers.register(converter);
    return MockMvcBuilders.standaloneSetup(new BodyController())
        .setMessageConverters(converter)
        .setControllerAdvice(new ApiV2ControllerAdvice(new MessageSourceUtils(source)))
        .build();
  }

  private static MockHttpServletResponse postBody(MockMvc mvc, String path, String body)
      throws Exception {
    return mvc.perform(
            MockMvcRequestBuilders.post(path)
                .locale(Locale.ENGLISH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andReturn()
        .getResponse();
  }

  @RestController
  static class BodyController {
    @PostMapping("/api/v2/test-body")
    JsonNode body(@RequestBody ApiV2JsonBody body) {
      return body.node();
    }

    @PostMapping("/api/v2/test-tree")
    JsonNode tree(@RequestBody JsonNode body) {
      return body;
    }
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
