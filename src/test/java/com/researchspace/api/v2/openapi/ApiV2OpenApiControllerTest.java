package com.researchspace.api.v2.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ApiV2OpenApiControllerTest {

  private final ApiV2OpenApiDocumentService documents = mock(ApiV2OpenApiDocumentService.class);
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void productionCachesTheDocumentAndSupportsConditionalGets() throws Exception {
    when(documents.generate()).thenReturn(Map.of("openapi", "3.1.0"));
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles("prod");
    MockMvc mvc =
        MockMvcBuilders.standaloneSetup(
                new ApiV2OpenApiController(documents, objectMapper, environment))
            .build();

    MockHttpServletResponse first =
        mvc.perform(get("/api/v2/openapi.json")).andReturn().getResponse();
    String etag = first.getHeader(HttpHeaders.ETAG);
    MockHttpServletResponse conditional =
        mvc.perform(get("/api/v2/openapi.json").header(HttpHeaders.IF_NONE_MATCH, etag))
            .andReturn()
            .getResponse();

    assertEquals(HttpStatus.OK.value(), first.getStatus());
    assertNotNull(etag);
    String cacheControl = first.getHeader(HttpHeaders.CACHE_CONTROL);
    assertTrue(cacheControl.contains("private"));
    assertTrue(cacheControl.contains("max-age=3600"));
    assertTrue(cacheControl.contains("must-revalidate"));
    assertEquals(HttpStatus.NOT_MODIFIED.value(), conditional.getStatus());
    assertEquals(etag, conditional.getHeader(HttpHeaders.ETAG));
    assertEquals(cacheControl, conditional.getHeader(HttpHeaders.CACHE_CONTROL));
    assertEquals("", conditional.getContentAsString());
    verify(documents).generate();
  }

  @Test
  void developmentDisablesHttpAndApplicationCaching() {
    when(documents.generate()).thenReturn(Map.of("openapi", "3.1.0"));
    ApiV2OpenApiController controller =
        new ApiV2OpenApiController(documents, objectMapper, new MockEnvironment());

    ResponseEntity<Map<String, Object>> first = controller.openApi();
    ResponseEntity<Map<String, Object>> second = controller.openApi();

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

    ResponseEntity<Map<String, Object>> response = controller.openApi();

    assertEquals("no-store", response.getHeaders().getCacheControl());
    assertNull(response.getHeaders().getETag());
  }
}
