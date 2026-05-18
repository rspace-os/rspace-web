package com.researchspace.webapp.integrations.dmpassistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

/**
 * Tests {@link MockDMPAssistantServer} — the WireMock-backed dev stub that activates with {@code
 * -Dmock-dmp-assistant=true} and impersonates the Portage Network roadmap API + its OAuth surface.
 */
class MockDMPAssistantServerTest {

  private MockDMPAssistantServer mock;
  private final RestTemplate rest = new RestTemplate();

  @AfterEach
  void tearDown() {
    if (mock != null) {
      mock.stop();
    }
    System.clearProperty(MockDMPAssistantServer.ENABLE_PROPERTY);
    System.clearProperty("dmpassistant.base.url");
  }

  @Test
  void doesNothingWhenEnableFlagIsAbsent() {
    System.clearProperty(MockDMPAssistantServer.ENABLE_PROPERTY);
    mock = new MockDMPAssistantServer();
    mock.start();
    assertNull(
        System.getProperty("dmpassistant.base.url"),
        "Base URL should not be overridden when mock flag is absent");
  }

  @Test
  void startsWireMockAndOverridesBaseUrlWhenEnabled() {
    System.setProperty(MockDMPAssistantServer.ENABLE_PROPERTY, "true");
    mock = new MockDMPAssistantServer();
    mock.start();
    String url = System.getProperty("dmpassistant.base.url");
    assertNotNull(url, "Mock server should set dmpassistant.base.url");
    assertTrue(url.startsWith("http://localhost:"), "Mock URL should be on localhost: " + url);
  }

  @Test
  void heartbeatStubReturnsCannedResponse() {
    enableAndStart();
    JsonNode body = getJson("/api/v2/heartbeat");
    assertEquals(200, body.get("code").asInt());
    assertEquals("OK", body.get("message").asText());
  }

  @Test
  void meStubReturnsCannedProfile() {
    enableAndStart();
    JsonNode body = getJson("/api/v2/me");
    assertNotNull(body.get("email"));
    assertNotNull(body.get("firstname"));
  }

  @Test
  void listPlansStubReturnsCannedListing() {
    enableAndStart();
    JsonNode body = getJson("/api/v2/plans?page=1&per_page=20");
    assertNotNull(body.get("items"));
    assertTrue(body.get("items").size() > 0, "Mock should return at least one plan");
    assertNotNull(body.get("items").get(0).get("dmp"));
    assertNotNull(body.get("items").get(0).get("dmp").get("title"));
  }

  @Test
  void getPlanByIdStubReturnsCannedPlan() {
    enableAndStart();
    JsonNode body = getJson("/api/v2/plans/42");
    assertNotNull(body.get("dmp"));
  }

  @Test
  void listTemplatesStubReturnsCannedListing() {
    enableAndStart();
    JsonNode body = getJson("/api/v2/templates");
    assertNotNull(body.get("items"));
    assertTrue(body.get("items").size() > 0);
  }

  @Test
  void oauthAuthorizeRedirectsBackToCallerWithFakeCode() {
    enableAndStart();
    String base = System.getProperty("dmpassistant.base.url");
    RestTemplate noFollow = new RestTemplate();
    noFollow.setRequestFactory(new NoRedirectRequestFactory());
    HttpHeaders headers = new HttpHeaders();
    ResponseEntity<String> response =
        noFollow.exchange(
            base
                + "/oauth/authorize?client_id=mock-client"
                + "&redirect_uri=http://localhost:8080/apps/dmpassistant/callback"
                + "&response_type=code&scope=read+write",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            String.class);
    assertEquals(HttpStatus.FOUND, response.getStatusCode(), "Should 302-redirect");
    String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
    assertNotNull(location);
    assertTrue(
        location.startsWith("http://localhost:8080/apps/dmpassistant/callback"),
        "Location should redirect to the supplied redirect_uri: " + location);
    assertTrue(location.contains("code="), "Location should carry a fake auth code: " + location);
  }

  @Test
  void oauthTokenReturnsFakeAccessToken() throws Exception {
    enableAndStart();
    String base = System.getProperty("dmpassistant.base.url");
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "authorization_code");
    form.add("code", "mock-auth-code");
    form.add("client_id", "mock");
    form.add("client_secret", "mock");
    form.add("redirect_uri", "http://localhost:8080/apps/dmpassistant/callback");
    ResponseEntity<String> response =
        rest.exchange(
            base + "/oauth/token", HttpMethod.POST, new HttpEntity<>(form, headers), String.class);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    JsonNode token = new ObjectMapper().readTree(response.getBody());
    assertNotNull(token.get("access_token"));
    assertNotNull(token.get("refresh_token"));
    assertTrue(token.get("expires_in").asLong() > 0);
  }

  private void enableAndStart() {
    System.setProperty(MockDMPAssistantServer.ENABLE_PROPERTY, "true");
    mock = new MockDMPAssistantServer();
    mock.start();
  }

  private JsonNode getJson(String path) {
    String base = System.getProperty("dmpassistant.base.url");
    return rest.getForObject(base + path, JsonNode.class);
  }

  private static class NoRedirectRequestFactory
      extends org.springframework.http.client.SimpleClientHttpRequestFactory {
    @Override
    protected void prepareConnection(java.net.HttpURLConnection conn, String httpMethod)
        throws java.io.IOException {
      super.prepareConnection(conn, httpMethod);
      conn.setInstanceFollowRedirects(false);
    }
  }
}
