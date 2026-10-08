package com.researchspace.api.v2.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

class ApiV2OpenApiControllerTest {

  private final ApiV2OpenApiDocumentService documents = mock(ApiV2OpenApiDocumentService.class);
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void productionCachesTheDocumentAndSupportsConditionalGets() {
    when(documents.generate()).thenReturn(Map.of("openapi", "3.1.0"));
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles("prod");
    ApiV2OpenApiController controller =
        new ApiV2OpenApiController(documents, objectMapper, environment);

    ResponseEntity<Map<String, Object>> first = controller.openApi(new MockHttpServletRequest());
    String etag = first.getHeaders().getETag();
    MockHttpServletRequest conditionalRequest = new MockHttpServletRequest();
    conditionalRequest.addHeader(HttpHeaders.IF_NONE_MATCH, etag);
    ResponseEntity<Map<String, Object>> conditional = controller.openApi(conditionalRequest);

    assertEquals(HttpStatus.OK, first.getStatusCode());
    assertNotNull(etag);
    assertTrue(first.getHeaders().getCacheControl().contains("private"));
    assertTrue(first.getHeaders().getCacheControl().contains("max-age=3600"));
    assertTrue(first.getHeaders().getCacheControl().contains("must-revalidate"));
    assertEquals(HttpStatus.NOT_MODIFIED, conditional.getStatusCode());
    assertEquals(etag, conditional.getHeaders().getETag());
    assertNull(conditional.getBody());
    verify(documents).generate();
  }

  @Test
  void developmentDisablesHttpAndApplicationCaching() {
    when(documents.generate()).thenReturn(Map.of("openapi", "3.1.0"));
    ApiV2OpenApiController controller =
        new ApiV2OpenApiController(documents, objectMapper, new MockEnvironment());

    ResponseEntity<Map<String, Object>> first = controller.openApi(new MockHttpServletRequest());
    ResponseEntity<Map<String, Object>> second = controller.openApi(new MockHttpServletRequest());

    assertEquals("no-store", first.getHeaders().getCacheControl());
    assertNull(first.getHeaders().getETag());
    assertEquals("no-store", second.getHeaders().getCacheControl());
    verify(documents, times(2)).generate();
  }

  @Test
  void developmentProfileWinsIfProductionAndDevelopmentProfilesAreBothActive() {
    when(documents.generate()).thenReturn(Map.of("openapi", "3.1.0"));
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles("prod", "dev");
    ApiV2OpenApiController controller =
        new ApiV2OpenApiController(documents, objectMapper, environment);

    ResponseEntity<Map<String, Object>> response = controller.openApi(new MockHttpServletRequest());

    assertEquals("no-store", response.getHeaders().getCacheControl());
    assertNull(response.getHeaders().getETag());
  }
}
