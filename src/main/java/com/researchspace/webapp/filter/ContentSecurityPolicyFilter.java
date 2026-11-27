package com.researchspace.webapp.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Adds the application Content Security Policy and its per-response nonce. */
@Component("cspFilter")
public class ContentSecurityPolicyFilter extends OncePerRequestFilter {

  public static final String NONCE_REQUEST_ATTRIBUTE = "cspNonce";

  private static final int NONCE_BYTES = 16;
  private static final String REPORT_ONLY_HEADER = "Content-Security-Policy-Report-Only";
  private static final String ENFORCED_HEADER = "Content-Security-Policy";
  private static final String REPORTING_ENDPOINTS_HEADER = "Reporting-Endpoints";
  private static final String REPORTING_ENDPOINT_NAME = "csp";
  private static final String HELPDOCS_API_ORIGIN = "https://lighthouse.helpdocs.io";
  private static final String HELPDOCS_ASSET_ORIGIN = "https://cdn.helpdocs.io";
  private static final String OFFICE_APPS_ORIGIN = "https://*.officeapps.live.com";
  private static final String EGNYTE_ORIGIN = "https://*.egnyte.com";
  private static final List<String> SUPPORTED_FRAME_ORIGINS =
      List.of(
          "https://accounts.google.com",
          "https://www.google.com",
          "https://recaptcha.google.com",
          "https://www.recaptcha.net",
          "https://www.youtube.com",
          "https://www.youtube-nocookie.com",
          "https://www.jove.com",
          "https://av.tib.eu");
  private static final List<String> TEAMS_FRAME_ANCESTORS =
      List.of(
          "https://teams.microsoft.com", "https://*.teams.microsoft.com", "https://*.skype.com");
  private static final String MATHJAX_PATH = "/scripts/externalTinymcePlugins/mathjax/dialog.html";
  private static final String EGNYTE_PATH = "/scripts/externalTinymcePlugins/egnyte/dialog.html";
  private static final Set<String> LEGACY_EVAL_PATHS =
      Set.of(
          "/scripts/externalTinymcePlugins/handsontable/dialog.html",
          "/scripts/externalTinymcePlugins/sketch/iframe.html");

  private final String policyHeader;
  private final String reportUri;
  private final String analyticsOrigin;
  private final String collaboraOrigin;
  private final String egnyteAdditionalOrigin;
  private final SecureRandom secureRandom = new SecureRandom();

  @Autowired
  public ContentSecurityPolicyFilter(
      @Value("${security.csp.mode:report-only}") String mode,
      @Value("${security.csp.report-uri:}") String reportUri,
      @Value("${analytics.server.host:}") String analyticsServerHost,
      @Value("${analytics.server.key:}") String analyticsServerKey,
      @Value("${collabora.wopi.discovery.url:}") String collaboraDiscoveryUrl,
      @Value("${security.csp.egnyte.additional-origin:}") String egnyteAdditionalOrigin) {
    this.policyHeader = policyHeaderForMode(mode);
    this.reportUri = validateReportUri(reportUri);
    this.analyticsOrigin =
        StringUtils.isBlank(analyticsServerKey)
            ? ""
            : originFromConfiguredUrl(analyticsServerHost, "analytics.server.host", false);
    this.collaboraOrigin =
        originFromConfiguredUrl(collaboraDiscoveryUrl, "collabora.wopi.discovery.url", true);
    this.egnyteAdditionalOrigin =
        originFromConfiguredUrl(
            egnyteAdditionalOrigin, "security.csp.egnyte.additional-origin", false);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    String nonce = generateNonce();
    request.setAttribute(NONCE_REQUEST_ATTRIBUTE, nonce);
    response.setHeader(policyHeader, policyForNonce(nonce, request));
    if (!reportUri.isEmpty()) {
      response.setHeader(
          REPORTING_ENDPOINTS_HEADER, REPORTING_ENDPOINT_NAME + "=\"" + reportUri + "\"");
    }
    chain.doFilter(request, response);
  }

  private String generateNonce() {
    byte[] bytes = new byte[NONCE_BYTES];
    secureRandom.nextBytes(bytes);
    return Base64.getEncoder().withoutPadding().encodeToString(bytes);
  }

  private String policyForNonce(String nonce, HttpServletRequest request) {
    String path = pathWithinApplication(request);
    String scriptDirective =
        "script-src 'self' 'nonce-"
            + nonce
            + "' 'report-sample' "
            + HELPDOCS_API_ORIGIN
            + " "
            + HELPDOCS_ASSET_ORIGIN;
    if (requiresLegacyEval(request)) {
      scriptDirective += " 'unsafe-eval'";
    }
    if (MATHJAX_PATH.equals(path)) {
      scriptDirective += " https://cdnjs.cloudflare.com";
    }

    List<String> connectSources =
        new ArrayList<>(List.of("'self'", HELPDOCS_API_ORIGIN, "https://www.protocols.io"));
    addIfConfigured(connectSources, analyticsOrigin);

    List<String> frameSources = new ArrayList<>();
    frameSources.add("'self'");
    frameSources.addAll(SUPPORTED_FRAME_ORIGINS);

    List<String> formActionSources = new ArrayList<>(List.of("'self'"));
    if (isOfficeOnlinePath(path)) {
      frameSources.add(OFFICE_APPS_ORIGIN);
      formActionSources.add(OFFICE_APPS_ORIGIN);
    } else if (isCollaboraOnlinePath(path)) {
      addIfConfigured(frameSources, collaboraOrigin);
      addIfConfigured(formActionSources, collaboraOrigin);
    }
    if (EGNYTE_PATH.equals(path)) {
      connectSources.add(EGNYTE_ORIGIN);
      addIfConfigured(connectSources, egnyteAdditionalOrigin);
    }
    if (EGNYTE_PATH.equals(path) || isStructuredDocumentPath(path)) {
      frameSources.add(EGNYTE_ORIGIN);
      addIfConfigured(frameSources, egnyteAdditionalOrigin);
    }

    List<String> frameAncestorSources = new ArrayList<>(List.of("'self'"));
    if (isTeamsEmbed(request, path)) {
      frameAncestorSources.addAll(TEAMS_FRAME_ANCESTORS);
    }

    List<String> directives =
        List.of(
            "default-src 'self'",
            scriptDirective,
            "style-src-elem 'self' 'nonce-" + nonce + "'",
            "style-src-attr 'unsafe-inline'",
            "img-src 'self' data: blob:",
            "font-src 'self' data:",
            "connect-src " + String.join(" ", connectSources),
            "frame-src " + String.join(" ", frameSources),
            "object-src 'none'",
            "base-uri 'none'",
            "form-action " + String.join(" ", formActionSources),
            "frame-ancestors " + String.join(" ", frameAncestorSources));
    String policy = String.join("; ", directives);
    return reportUri.isEmpty()
        ? policy
        : policy + "; report-uri " + reportUri + "; report-to " + REPORTING_ENDPOINT_NAME;
  }

  private static void addIfConfigured(List<String> sources, String source) {
    if (!source.isEmpty() && !sources.contains(source)) {
      sources.add(source);
    }
  }

  private static boolean isOfficeOnlinePath(String path) {
    return path.startsWith("/officeOnline/");
  }

  private static boolean isCollaboraOnlinePath(String path) {
    return path.startsWith("/collaboraOnline/");
  }

  private static boolean isStructuredDocumentPath(String path) {
    return path.startsWith("/workspace/editor/structuredDocument/");
  }

  private static boolean isTeamsEmbed(HttpServletRequest request, String path) {
    return path.startsWith("/msteams/")
        || (path.startsWith("/workspace/editor/structuredDocument/")
            && "true".equals(request.getParameter("msTeamsDocView")));
  }

  private static boolean requiresLegacyEval(HttpServletRequest request) {
    return LEGACY_EVAL_PATHS.contains(pathWithinApplication(request));
  }

  private static String pathWithinApplication(HttpServletRequest request) {
    String requestUri = request.getRequestURI();
    String contextPath = request.getContextPath();
    if (contextPath == null || contextPath.isEmpty()) {
      return requestUri;
    }
    if (!requestUri.startsWith(contextPath)) {
      return "";
    }
    String path = requestUri.substring(contextPath.length());
    return path.startsWith("/") ? path : "";
  }

  private static String policyHeaderForMode(String mode) {
    return switch (StringUtils.trimToEmpty(mode)) {
      case "report-only" -> REPORT_ONLY_HEADER;
      case "enforce" -> ENFORCED_HEADER;
      default ->
          throw new IllegalArgumentException(
              "security.csp.mode must be 'report-only' or 'enforce'");
    };
  }

  private static String validateReportUri(String configuredUri) {
    String candidate = StringUtils.trimToEmpty(configuredUri);
    if (candidate.isEmpty()) {
      return "";
    }
    if (candidate.chars().anyMatch(c -> Character.isISOControl(c) || Character.isWhitespace(c))
        || candidate.indexOf(';') >= 0
        || candidate.indexOf(',') >= 0) {
      throw new IllegalArgumentException(
          "security.csp.report-uri must be a single URI without whitespace or policy separators");
    }

    URI uri;
    try {
      uri = URI.create(candidate);
    } catch (IllegalArgumentException invalidUri) {
      throw new IllegalArgumentException("security.csp.report-uri must be a valid URI", invalidUri);
    }
    if (uri.getFragment() != null) {
      throw new IllegalArgumentException("security.csp.report-uri must not contain a fragment");
    }

    if (uri.isAbsolute()) {
      if (!"https".equals(uri.getScheme().toLowerCase(Locale.ROOT))
          || uri.getHost() == null
          || uri.getUserInfo() != null) {
        throw new IllegalArgumentException(
            "security.csp.report-uri must be an HTTPS URL without user information");
      }
      return candidate;
    }

    if (!candidate.startsWith("/")
        || candidate.startsWith("//")
        || uri.getRawAuthority() != null
        || uri.getRawPath() == null
        || !uri.getRawPath().startsWith("/")) {
      throw new IllegalArgumentException(
          "security.csp.report-uri must be a same-origin absolute path or HTTPS URL");
    }
    return candidate;
  }

  private static String originFromConfiguredUrl(
      String configuredUrl, String propertyName, boolean allowHttp) {
    String candidate = StringUtils.trimToEmpty(configuredUrl);
    if (candidate.isEmpty()) {
      return "";
    }

    URI uri;
    try {
      uri = URI.create(candidate);
    } catch (IllegalArgumentException invalidUri) {
      throw new IllegalArgumentException(propertyName + " must be a valid URL", invalidUri);
    }

    String scheme = uri.getScheme();
    String host = uri.getHost();
    if (scheme == null
        || host == null
        || uri.getUserInfo() != null
        || !("https".equalsIgnoreCase(scheme)
            || (allowHttp && "http".equalsIgnoreCase(scheme))
            || isLoopbackHttp(scheme, host))) {
      throw new IllegalArgumentException(
          propertyName
              + (allowHttp
                  ? " must be an HTTP or HTTPS URL"
                  : " must be an HTTPS URL or an HTTP loopback URL"));
    }

    String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
    String normalizedHost = host.toLowerCase(Locale.ROOT);
    return normalizedScheme
        + "://"
        + (normalizedHost.indexOf(':') >= 0 ? "[" + normalizedHost + "]" : normalizedHost)
        + (uri.getPort() >= 0 ? ":" + uri.getPort() : "");
  }

  private static boolean isLoopbackHttp(String scheme, String host) {
    return "http".equalsIgnoreCase(scheme)
        && ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host));
  }
}
