package com.researchspace.webapp.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Authenticates Slack commands before any filter can consume their signed form body. */
@Component("slackSignatureFilter")
public class SlackSignatureFilter extends OncePerRequestFilter {
  private static final int MAX_BODY_BYTES = 65536;
  private final String signingSecret;
  private final Clock clock;

  @Autowired
  public SlackSignatureFilter(@Value("${slack.signing.secret:}") String signingSecret) {
    this(signingSecret, Clock.systemUTC());
  }

  SlackSignatureFilter(String signingSecret, Clock clock) {
    this.signingSecret = signingSecret;
    this.clock = clock;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (!"POST".equals(request.getMethod())
        || signingSecret.isBlank()
        || request.getQueryString() != null
        || request.getContentType() == null
        || !MediaType.APPLICATION_FORM_URLENCODED_VALUE.equalsIgnoreCase(
            request.getContentType().split(";", 2)[0].trim())) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }
    byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
    if (body.length > MAX_BODY_BYTES || !validSignature(request, body)) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }
    Map<String, String[]> parameters = new LinkedHashMap<>();
    try {
      for (String pair : new String(body, StandardCharsets.UTF_8).split("&")) {
        if (pair.isEmpty()) continue;
        String[] parts = pair.split("=", 2);
        String name = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
        String value = URLDecoder.decode(parts.length == 2 ? parts[1] : "", StandardCharsets.UTF_8);
        if (parameters.putIfAbsent(name, new String[] {value}) != null) {
          response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
          return;
        }
      }
    } catch (IllegalArgumentException invalidForm) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }
    chain.doFilter(new SignedFormRequest(request, parameters), response);
  }

  private boolean validSignature(HttpServletRequest request, byte[] body) throws ServletException {
    String timestamp = request.getHeader("X-Slack-Request-Timestamp");
    String signature = request.getHeader("X-Slack-Signature");
    if (timestamp == null
        || !timestamp.matches("[0-9]{1,12}")
        || signature == null
        || !signature.matches("v0=[0-9a-f]{64}")) {
      return false;
    }
    long seconds = Long.parseLong(timestamp);
    if (Math.abs(clock.instant().getEpochSecond() - seconds) > 300) return false;
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      mac.update(("v0:" + timestamp + ":").getBytes(StandardCharsets.UTF_8));
      byte[] expected = mac.doFinal(body);
      return MessageDigest.isEqual(expected, HexFormat.of().parseHex(signature.substring(3)));
    } catch (GeneralSecurityException failure) {
      throw new ServletException("Slack request signature verification unavailable", failure);
    }
  }

  private static class SignedFormRequest extends HttpServletRequestWrapper {
    private final Map<String, String[]> parameters;

    SignedFormRequest(HttpServletRequest request, Map<String, String[]> parameters) {
      super(request);
      this.parameters = Collections.unmodifiableMap(parameters);
    }

    @Override
    public Map<String, String[]> getParameterMap() {
      return parameters;
    }

    @Override
    public Enumeration<String> getParameterNames() {
      return Collections.enumeration(parameters.keySet());
    }

    @Override
    public String[] getParameterValues(String name) {
      String[] values = parameters.get(name);
      return values == null ? null : values.clone();
    }

    @Override
    public String getParameter(String name) {
      String[] values = parameters.get(name);
      return values == null ? null : values[0];
    }
  }
}
