package com.researchspace.webapp.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.security.SecureRandom;
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
  private static final String HELPDOCS_API_ORIGIN = "https://lighthouse.helpdocs.io";
  private static final String HELPDOCS_ASSET_ORIGIN = "https://cdn.helpdocs.io";
  private static final String MATHJAX_PATH = "/scripts/externalTinymcePlugins/mathjax/dialog.html";
  private static final Set<String> LEGACY_EVAL_PATHS =
      Set.of(
          "/scripts/externalTinymcePlugins/handsontable/dialog.html",
          "/scripts/externalTinymcePlugins/sketch/iframe.html");

  private final String policyHeader;
  private final String reportUri;
  private final SecureRandom secureRandom = new SecureRandom();

  @Autowired
  public ContentSecurityPolicyFilter(
      @Value("${security.csp.mode:report-only}") String mode,
      @Value("${security.csp.report-uri:}") String reportUri) {
    this.policyHeader = policyHeaderForMode(mode);
    this.reportUri = validateReportUri(reportUri);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    String nonce = generateNonce();
    request.setAttribute(NONCE_REQUEST_ATTRIBUTE, nonce);
    response.setHeader(policyHeader, policyForNonce(nonce, request));
    chain.doFilter(request, response);
  }

  private String generateNonce() {
    byte[] bytes = new byte[NONCE_BYTES];
    secureRandom.nextBytes(bytes);
    return Base64.getEncoder().withoutPadding().encodeToString(bytes);
  }

  private String policyForNonce(String nonce, HttpServletRequest request) {
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
    if (MATHJAX_PATH.equals(pathWithinApplication(request))) {
      scriptDirective += " https://cdnjs.cloudflare.com";
    }
    List<String> directives =
        List.of(
            "default-src 'self'",
            scriptDirective,
            "style-src-elem 'self' 'nonce-" + nonce + "'",
            "style-src-attr 'unsafe-inline'",
            "img-src 'self' data: blob:",
            "font-src 'self' data:",
            "connect-src 'self' " + HELPDOCS_API_ORIGIN,
            "object-src 'none'",
            "base-uri 'none'",
            "form-action 'self'",
            "frame-ancestors 'self'");
    String policy = String.join("; ", directives);
    return reportUri.isEmpty() ? policy : policy + "; report-uri " + reportUri;
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
}
