package com.researchspace.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ConversionAuthenticationFilterTest {

  private static final String TOKEN = "0123456789abcdef0123456789abcdef";

  @TempDir Path directory;

  @Test
  void authenticatesConversionAndSetsDeployment() throws Exception {
    Files.writeString(directory.resolve("deployment-a"), TOKEN);
    var filter = new ConversionAuthenticationFilter(properties(), new ObjectMapper());
    var request = new MockHttpServletRequest("POST", "/v1/convert/html");
    request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN);
    var response = new MockHttpServletResponse();
    var invoked = new AtomicBoolean();

    filter.doFilter(
        request,
        response,
        (filteredRequest, ignored) -> {
          invoked.set(true);
          assertEquals(
              "deployment-a",
              filteredRequest.getAttribute(ConversionAuthenticationFilter.DEPLOYMENT_ATTRIBUTE));
        });

    assertEquals(true, invoked.get());
    assertEquals(200, response.getStatus());
  }

  @Test
  void rejectsMissingCredentialBeforeController() throws Exception {
    Files.writeString(directory.resolve("deployment-a"), TOKEN);
    var filter = new ConversionAuthenticationFilter(properties(), new ObjectMapper());
    var request = new MockHttpServletRequest("POST", "/forms/libreoffice/convert");
    var response = new MockHttpServletResponse();

    filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {});

    assertEquals(401, response.getStatus());
    assertEquals(
        ConversionError.AUTHENTICATION_FAILED.code(), response.getHeader(ConversionError.HEADER));
    assertEquals("Bearer", response.getHeader(HttpHeaders.WWW_AUTHENTICATE));
    assertTrue(response.getContentAsString().contains("conversion.authentication-failed"));
    assertNull(request.getAttribute(ConversionAuthenticationFilter.DEPLOYMENT_ATTRIBUTE));
  }

  @Test
  void rejectsDuplicateTokensAtStartup() throws Exception {
    Files.writeString(directory.resolve("deployment-a"), TOKEN);
    Files.writeString(directory.resolve("deployment-b"), TOKEN);

    assertThrows(
        IllegalStateException.class,
        () -> new ConversionAuthenticationFilter(properties(), new ObjectMapper()));
  }

  private ConverterProperties properties() {
    return new ConverterProperties(
        Path.of("/office"),
        Path.of("/work"),
        Duration.ofSeconds(1),
        2,
        1,
        1024,
        Path.of("/bin/true"),
        directory);
  }
}
