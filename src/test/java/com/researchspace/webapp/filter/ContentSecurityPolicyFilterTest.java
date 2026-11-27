package com.researchspace.webapp.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

class ContentSecurityPolicyFilterTest {

  private static final String REPORT_ONLY_HEADER = "Content-Security-Policy-Report-Only";
  private static final String ENFORCED_HEADER = "Content-Security-Policy";
  private static final String REPORTING_ENDPOINTS_HEADER = "Reporting-Endpoints";
  private static final Path WEB_XML = Path.of("src", "main", "webapp", "WEB-INF", "web.xml");

  @Test
  void setsReportOnlyPolicyAndRequestNonceBeforeTheChain() throws Exception {
    ContentSecurityPolicyFilter filter = filter("report-only", "");
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/workspace");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(
        request,
        response,
        (servletRequest, servletResponse) -> {
          HttpServletRequest requestInChain = (HttpServletRequest) servletRequest;
          HttpServletResponse responseInChain = (HttpServletResponse) servletResponse;
          String nonce =
              (String)
                  requestInChain.getAttribute(ContentSecurityPolicyFilter.NONCE_REQUEST_ATTRIBUTE);

          assertNotNull(nonce);
          assertThat(nonce).matches("[A-Za-z0-9+/]{22}");
          assertThat(responseInChain.getHeader(REPORT_ONLY_HEADER))
              .isEqualTo(expectedPolicy(nonce));
        });

    assertNull(response.getHeader(ENFORCED_HEADER));
    assertThat(response.getHeader(REPORT_ONLY_HEADER)).doesNotContain("report-uri");
  }

  @Test
  void generatesANewNonceForEachResponse() throws Exception {
    ContentSecurityPolicyFilter filter = filter("report-only", "");

    String firstNonce = runAndCaptureNonce(filter);
    String secondNonce = runAndCaptureNonce(filter);

    assertNotEquals(firstNonce, secondNonce);
  }

  @Test
  void selectsEnforcedHeaderAndAddsValidatedReportUri() throws Exception {
    ContentSecurityPolicyFilter filter = filter("enforce", "https://reports.example.org/csp");
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/workspace");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (servletRequest, servletResponse) -> {});

    String nonce =
        (String) request.getAttribute(ContentSecurityPolicyFilter.NONCE_REQUEST_ATTRIBUTE);
    assertNull(response.getHeader(REPORT_ONLY_HEADER));
    assertThat(response.getHeader(ENFORCED_HEADER))
        .isEqualTo(
            expectedPolicy(nonce) + "; report-uri https://reports.example.org/csp; report-to csp");
    assertEquals(
        "csp=\"https://reports.example.org/csp\"", response.getHeader(REPORTING_ENDPOINTS_HEADER));
  }

  @Test
  void acceptsSameOriginAbsolutePathReportUri() throws Exception {
    ContentSecurityPolicyFilter filter = filter("report-only", "/csp/report");
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/workspace");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (servletRequest, servletResponse) -> {});

    String nonce =
        (String) request.getAttribute(ContentSecurityPolicyFilter.NONCE_REQUEST_ATTRIBUTE);
    assertThat(response.getHeader(REPORT_ONLY_HEADER))
        .isEqualTo(expectedPolicy(nonce) + "; report-uri /csp/report; report-to csp");
    assertEquals("csp=\"/csp/report\"", response.getHeader(REPORTING_ENDPOINTS_HEADER));
  }

  @Test
  void allowsUnsafeEvalOnlyOnTheTwoConfiguredTinyMcePaths() throws Exception {
    ContentSecurityPolicyFilter filter = filter("report-only", "");

    assertThat(
            policyFor(
                filter,
                "/rspace/scripts/externalTinymcePlugins/handsontable/dialog.html",
                "/rspace"))
        .contains("'unsafe-eval'");
    assertThat(
            policyFor(
                filter, "/rspace/scripts/externalTinymcePlugins/sketch/iframe.html", "/rspace"))
        .contains("'unsafe-eval'");
    assertThat(
            policyFor(
                filter,
                "/rspace/scripts/externalTinymcePlugins/handsontable/other.html",
                "/rspace"))
        .doesNotContain("'unsafe-eval'");
    assertThat(policyFor(filter, "/scripts/externalTinymcePlugins/sketch/iframe.html.more", ""))
        .doesNotContain("'unsafe-eval'");
    assertThat(policyFor(filter, "/scripts/externalTinymcePlugins/sketch/iframe.html", "/rspace"))
        .doesNotContain("'unsafe-eval'");
  }

  @Test
  void allowsThePinnedMathJaxCdnOnlyOnItsStaticPluginDocument() throws Exception {
    ContentSecurityPolicyFilter filter = filter("report-only", "");

    assertThat(
            policyFor(
                filter, "/rspace/scripts/externalTinymcePlugins/mathjax/dialog.html", "/rspace"))
        .contains("https://cdnjs.cloudflare.com")
        .doesNotContain("'unsafe-eval'");
    assertThat(policyFor(filter, "/workspace", "")).doesNotContain("https://cdnjs.cloudflare.com");
  }

  @Test
  void rejectsInvalidModesAndUnsafeReportUris() {
    assertThrows(IllegalArgumentException.class, () -> filter("disabled", ""));
    assertThrows(
        IllegalArgumentException.class,
        () -> filter("report-only", "http://reports.example.org/csp"));
    assertThrows(
        IllegalArgumentException.class,
        () -> filter("report-only", "https://user:pass@reports.example.org/csp"));
    assertThrows(
        IllegalArgumentException.class, () -> filter("report-only", "//reports.example.org/csp"));
    assertThrows(IllegalArgumentException.class, () -> filter("report-only", "/csp; object-src *"));
    assertThrows(
        IllegalArgumentException.class, () -> filter("report-only", "/csp\r\nX-Injected: value"));
  }

  @Test
  void addsSupportedAndConfiguredIntegrationOriginsWithoutBroadSchemes() throws Exception {
    ContentSecurityPolicyFilter filter =
        new ContentSecurityPolicyFilter(
            "report-only",
            "",
            "https://analytics.example.org/api/project",
            "analytics-key",
            "http://collabora.example.org:9980/hosting/discovery",
            "http://localhost:9242/egnyte-picker");

    String workspacePolicy = policyFor(filter, "/workspace", "");
    assertThat(workspacePolicy)
        .contains(
            "connect-src 'self' https://lighthouse.helpdocs.io https://www.protocols.io"
                + " https://analytics.example.org")
        .contains(
            "frame-src 'self' https://accounts.google.com https://www.google.com "
                + "https://recaptcha.google.com https://www.recaptcha.net "
                + "https://www.youtube.com https://www.youtube-nocookie.com "
                + "https://www.jove.com https://av.tib.eu")
        .doesNotContain("connect-src https:", "frame-src https:");

    assertThat(policyFor(filter, "/officeOnline/GL123/edit", ""))
        .contains("frame-src ")
        .contains(" https://*.officeapps.live.com")
        .contains("form-action 'self' https://*.officeapps.live.com");
    assertThat(policyFor(filter, "/collaboraOnline/GL123/edit", ""))
        .contains(" http://collabora.example.org:9980")
        .contains("form-action 'self' http://collabora.example.org:9980");
    assertThat(policyFor(filter, "/scripts/externalTinymcePlugins/egnyte/dialog.html", ""))
        .contains("connect-src ")
        .contains("frame-src ")
        .contains(" https://*.egnyte.com")
        .contains(" http://localhost:9242");
    assertThat(policyFor(filter, "/workspace/editor/structuredDocument/GL123", ""))
        .contains("frame-src ")
        .contains(" https://*.egnyte.com")
        .contains(" http://localhost:9242")
        .doesNotContain(
            "connect-src 'self' https://lighthouse.helpdocs.io https://www.protocols.io"
                + " https://analytics.example.org https://*.egnyte.com");
    assertThat(workspacePolicy)
        .doesNotContain("https://*.officeapps.live.com")
        .doesNotContain("http://collabora.example.org:9980")
        .doesNotContain("https://*.egnyte.com")
        .doesNotContain("http://localhost:9242");
  }

  @Test
  void allowsTeamsAncestorsOnlyForTeamsEmbeddingRoutes() throws Exception {
    ContentSecurityPolicyFilter filter = filter("report-only", "");

    assertThat(policyFor(filter, "/msteams/tabConfig", ""))
        .contains(
            "frame-ancestors 'self' https://teams.microsoft.com "
                + "https://*.teams.microsoft.com https://*.skype.com");

    MockHttpServletRequest teamsDocument =
        new MockHttpServletRequest("GET", "/workspace/editor/structuredDocument/123");
    teamsDocument.setParameter("msTeamsDocView", "true");
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(teamsDocument, response, (servletRequest, servletResponse) -> {});
    assertThat(response.getHeader(REPORT_ONLY_HEADER))
        .contains("frame-ancestors 'self' https://teams.microsoft.com");

    assertThat(policyFor(filter, "/workspace/editor/structuredDocument/123", ""))
        .contains("frame-ancestors 'self'")
        .doesNotContain("https://teams.microsoft.com");
  }

  @Test
  void rejectsUnsafeConfiguredIntegrationUrls() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ContentSecurityPolicyFilter(
                "report-only", "", "http://analytics.example.org", "analytics-key", "", ""));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ContentSecurityPolicyFilter(
                "report-only",
                "",
                "",
                "",
                "https://user:pass@collabora.example.org/discovery",
                ""));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ContentSecurityPolicyFilter(
                "report-only", "", "", "", "", "http://egnyte.example.org"));
  }

  @Test
  void mapsCspAcrossAllRoutesAfterSlackAndBeforeShiro() throws Exception {
    Document webXml;
    try (InputStream input = Files.newInputStream(WEB_XML)) {
      webXml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input);
    }

    NodeList filters = webXml.getElementsByTagName("filter");
    String cspFilterClass = null;
    for (int i = 0; i < filters.getLength(); i++) {
      Element filter = (Element) filters.item(i);
      String name = filter.getElementsByTagName("filter-name").item(0).getTextContent().trim();
      if ("cspFilter".equals(name)) {
        cspFilterClass =
            filter.getElementsByTagName("filter-class").item(0).getTextContent().trim();
      }
    }

    List<String> filterNames = new ArrayList<>();
    NodeList mappings = webXml.getElementsByTagName("filter-mapping");
    Element cspMapping = null;
    for (int i = 0; i < mappings.getLength(); i++) {
      Element mapping = (Element) mappings.item(i);
      String name = mapping.getElementsByTagName("filter-name").item(0).getTextContent().trim();
      filterNames.add(name);
      if ("cspFilter".equals(name)) {
        cspMapping = mapping;
      }
    }

    assertEquals("org.springframework.web.filter.DelegatingFilterProxy", cspFilterClass);
    assertNotNull(cspMapping);
    assertTrue(filterNames.indexOf("slackSignatureFilter") < filterNames.indexOf("cspFilter"));
    assertTrue(filterNames.indexOf("cspFilter") < filterNames.indexOf("shiroFilter"));
    assertEquals(Set.of("/*"), textValues(cspMapping.getElementsByTagName("url-pattern")));
    assertEquals(
        EnumSet.of(DispatcherType.REQUEST),
        dispatcherTypes(cspMapping.getElementsByTagName("dispatcher")));
  }

  private static String runAndCaptureNonce(ContentSecurityPolicyFilter filter) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/workspace");
    filter.doFilter(
        request, new MockHttpServletResponse(), (servletRequest, servletResponse) -> {});
    return (String) request.getAttribute(ContentSecurityPolicyFilter.NONCE_REQUEST_ATTRIBUTE);
  }

  private static ContentSecurityPolicyFilter filter(String mode, String reportUri) {
    return new ContentSecurityPolicyFilter(mode, reportUri, "", "", "", "");
  }

  private static String policyFor(
      ContentSecurityPolicyFilter filter, String requestUri, String contextPath) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", requestUri);
    request.setContextPath(contextPath);
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, (servletRequest, servletResponse) -> {});
    return response.getHeader(REPORT_ONLY_HEADER);
  }

  private static String expectedPolicy(String nonce) {
    return "default-src 'self'; "
        + "script-src 'self' 'nonce-"
        + nonce
        + "' 'report-sample' https://lighthouse.helpdocs.io https://cdn.helpdocs.io; "
        + "style-src-elem 'self' 'nonce-"
        + nonce
        + "'; "
        + "style-src-attr 'unsafe-inline'; "
        + "img-src 'self' data: blob:; "
        + "font-src 'self' data:; "
        + "connect-src 'self' https://lighthouse.helpdocs.io https://www.protocols.io; "
        + "frame-src 'self' https://accounts.google.com https://www.google.com "
        + "https://recaptcha.google.com https://www.recaptcha.net https://www.youtube.com "
        + "https://www.youtube-nocookie.com https://www.jove.com https://av.tib.eu; "
        + "object-src 'none'; "
        + "base-uri 'none'; "
        + "form-action 'self'; "
        + "frame-ancestors 'self'";
  }

  private static Set<String> textValues(NodeList nodes) {
    Set<String> values = new LinkedHashSet<>();
    for (int i = 0; i < nodes.getLength(); i++) {
      values.add(nodes.item(i).getTextContent().trim());
    }
    return values;
  }

  private static Set<DispatcherType> dispatcherTypes(NodeList nodes) {
    Set<DispatcherType> dispatchers = EnumSet.noneOf(DispatcherType.class);
    for (int i = 0; i < nodes.getLength(); i++) {
      dispatchers.add(DispatcherType.valueOf(nodes.item(i).getTextContent().trim()));
    }
    return dispatchers;
  }
}
