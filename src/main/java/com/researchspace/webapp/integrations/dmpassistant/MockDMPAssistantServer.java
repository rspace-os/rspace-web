package com.researchspace.webapp.integrations.dmpassistant;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Optional dev/demo aid: when the JVM is started with {@code -Dmock-dmp-assistant=true}, this
 * component starts a WireMock server that impersonates the Portage Network roadmap API and its
 * OAuth surface, then rewrites the {@code dmpassistant.base.url} system property so the live {@link
 * DMPAssistantController} and {@link DMPAssistantProviderImpl} beans pick up the mock URL at Spring
 * {@code @Value} resolution time.
 *
 * <p>This sidesteps the production Cloudflare block on {@code dmp-pgd.ca} and lets developers
 * exercise the Apps-page Connect flow, the plan browser, and every v2 endpoint end-to-end against a
 * fully local server. All responses are canned.
 *
 * <p>Ordering is guaranteed by {@code @DependsOn("mockDMPAssistantServer")} on the controller and
 * provider: Spring constructs and runs the {@code @PostConstruct} of this component (which sets the
 * system property) before the consumers' fields are injected, so their {@code @Value(
 * "${dmpassistant.base.url}")} resolves to the mock URL.
 */
@Slf4j
@Component("mockDMPAssistantServer")
public class MockDMPAssistantServer {

  public static final String ENABLE_PROPERTY = "mock-dmp-assistant";
  public static final String PORT_PROPERTY = "mock-dmp-assistant.port";
  public static final int DEFAULT_PORT = 9999;

  private WireMockServer server;

  @PostConstruct
  public void start() {
    if (!"true".equalsIgnoreCase(System.getProperty(ENABLE_PROPERTY))) {
      return;
    }
    int port = Integer.parseInt(System.getProperty(PORT_PROPERTY, String.valueOf(DEFAULT_PORT)));
    server =
        new WireMockServer(
            WireMockConfiguration.options()
                .port(port)
                .extensions(new ResponseTemplateTransformer(true))
                .notifier(new com.github.tomakehurst.wiremock.common.Slf4jNotifier(false)));

    registerOAuthStubs(server);
    registerApiV2Stubs(server);

    server.start();
    String baseUrl = "http://localhost:" + server.port();
    System.setProperty("dmpassistant.base.url", baseUrl);
    log.warn("=================================================================");
    log.warn("Mock DMP Assistant server started at {}", baseUrl);
    log.warn("dmpassistant.base.url overridden to {}", baseUrl);
    log.warn("All OAuth and /api/v2/* requests will be served from canned data.");
    log.warn("=================================================================");
  }

  @PreDestroy
  public void stop() {
    if (server != null && server.isRunning()) {
      server.stop();
      log.info("Mock DMP Assistant server stopped");
    }
  }

  private static void registerOAuthStubs(WireMockServer s) {
    s.stubFor(
        oauthAuthorize()
            .willReturn(
                aResponse()
                    .withStatus(302)
                    .withHeader(
                        "Location",
                        "{{request.query.redirect_uri}}?code=mock-auth-code"
                            + "&state={{request.query.state}}")
                    .withTransformers("response-template")));

    s.stubFor(post(urlPathEqualTo("/oauth/token")).willReturn(jsonOk(tokenBody())));
  }

  private static void registerApiV2Stubs(WireMockServer s) {
    s.stubFor(get(urlPathEqualTo("/api/v2/heartbeat")).willReturn(jsonOk(heartbeatBody())));
    s.stubFor(get(urlPathEqualTo("/api/v2/me")).willReturn(jsonOk(meBody())));
    s.stubFor(get(urlPathEqualTo("/api/v2/plans")).willReturn(jsonOk(planListBody())));
    s.stubFor(get(urlPathMatching("/api/v2/plans/[^/]+")).willReturn(jsonOk(singlePlanBody())));
    s.stubFor(
        post(urlPathEqualTo("/api/v2/plans")).willReturn(jsonOk(singlePlanBody()).withStatus(201)));
    s.stubFor(put(urlPathMatching("/api/v2/plans/[^/]+")).willReturn(jsonOk("{\"ok\":true}")));
    s.stubFor(get(urlPathEqualTo("/api/v2/templates")).willReturn(jsonOk(templateListBody())));
    s.stubFor(
        get(urlPathMatching("/api/v2/templates/[^/]+")).willReturn(jsonOk(singleTemplateBody())));
  }

  private static MappingBuilder oauthAuthorize() {
    return get(urlPathEqualTo("/oauth/authorize"));
  }

  private static ResponseDefinitionBuilder jsonOk(String body) {
    return aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody(body);
  }

  private static String tokenBody() {
    return "{"
        + "\"access_token\":\"mock-access-token\","
        + "\"refresh_token\":\"mock-refresh-token\","
        + "\"token_type\":\"Bearer\","
        + "\"expires_in\":7200,"
        + "\"scope\":\"read write\""
        + "}";
  }

  private static String heartbeatBody() {
    return "{\"application\":\"DMP Assistant (mock)\",\"code\":200,\"message\":\"OK\"}";
  }

  private static String meBody() {
    return "{"
        + "\"firstname\":\"Mock\","
        + "\"surname\":\"User\","
        + "\"email\":\"mock.user@example.org\","
        + "\"organisation\":\"Mock University\""
        + "}";
  }

  private static String planListBody() {
    return "{\"items\":["
        + planItem(
            "1",
            "Genomic biodiversity of native Canadian flora",
            "Alice Researcher",
            "Mock University",
            "2026-01-15T10:00:00Z",
            "2026-04-22T14:30:00Z")
        + ","
        + planItem(
            "2",
            "Long-term arctic permafrost monitoring data plan",
            "Bob Scientist",
            "Mock Institute",
            "2026-02-01T09:00:00Z",
            "2026-04-10T11:00:00Z")
        + ","
        + planItem(
            "3",
            "Open-access laboratory notebook archive",
            null,
            null,
            "2026-03-15T12:00:00Z",
            "2026-05-01T16:00:00Z")
        + "],\"total_items\":3}";
  }

  private static String singlePlanBody() {
    return "{\"dmp\":{"
        + "\"title\":\"Genomic biodiversity of native Canadian flora\","
        + "\"dmp_id\":{\"identifier\":\"1\"},"
        + "\"contact\":{\"name\":\"Alice Researcher\","
        + "\"affiliation\":{\"name\":\"Mock University\"}},"
        + "\"created\":\"2026-01-15T10:00:00Z\","
        + "\"modified\":\"2026-04-22T14:30:00Z\","
        + "\"language\":\"eng\","
        + "\"ethical_issues_exist\":\"unknown\","
        + "\"dataset\":[{\"title\":\"Sequencing dataset 2026-Q1\"}]"
        + "}}";
  }

  private static String templateListBody() {
    return "{\"items\":["
        + "{\"id\":1,\"title\":\"Mock NSERC template\"},"
        + "{\"id\":2,\"title\":\"Mock SSHRC template\"}"
        + "],\"total_items\":2}";
  }

  private static String singleTemplateBody() {
    return "{\"id\":1,\"title\":\"Mock NSERC template\","
        + "\"description\":\"A canned template for the dev mock server.\"}";
  }

  private static String planItem(
      String id,
      String title,
      String contactName,
      String contactAffiliation,
      String created,
      String modified) {
    StringBuilder sb = new StringBuilder("{\"dmp\":{");
    sb.append("\"title\":\"").append(title).append("\",");
    sb.append("\"dmp_id\":{\"identifier\":\"").append(id).append("\"},");
    if (contactName != null) {
      sb.append("\"contact\":{\"name\":\"")
          .append(contactName)
          .append("\",\"affiliation\":{\"name\":\"")
          .append(contactAffiliation)
          .append("\"}},");
    }
    sb.append("\"created\":\"").append(created).append("\",");
    sb.append("\"modified\":\"").append(modified).append("\"}}");
    return sb.toString();
  }
}
