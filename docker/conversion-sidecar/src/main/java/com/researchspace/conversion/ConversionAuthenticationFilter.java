package com.researchspace.conversion;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Authenticates conversion requests against deployment-specific bearer token files. */
@Component
final class ConversionAuthenticationFilter extends OncePerRequestFilter {

  static final String DEPLOYMENT_ATTRIBUTE = "rspace.conversion.deployment";

  private final List<Credential> credentials;
  private final ObjectMapper objectMapper;

  ConversionAuthenticationFilter(ConverterProperties properties, ObjectMapper objectMapper) {
    credentials = readCredentials(properties.credentialsDirectory());
    this.objectMapper = objectMapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return !(path.startsWith("/v1/") || path.equals("/forms/libreoffice/convert"));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
    byte[] supplied =
        authorization != null && authorization.startsWith("Bearer ")
            ? authorization.substring(7).getBytes(StandardCharsets.UTF_8)
            : new byte[0];
    String deploymentId = null;
    for (Credential credential : credentials) {
      if (MessageDigest.isEqual(credential.token(), supplied)) {
        deploymentId = credential.deploymentId();
      }
    }
    if (deploymentId == null) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.setHeader(ConversionError.HEADER, ConversionError.AUTHENTICATION_FAILED.code());
      response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
      response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
      String code = ConversionError.AUTHENTICATION_FAILED.code();
      ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, code);
      problem.setType(URI.create("urn:rspace:conversion:error:" + code));
      problem.setTitle(code);
      problem.setProperty("code", code);
      problem.setProperty("requestId", UUID.randomUUID().toString());
      objectMapper.writeValue(response.getOutputStream(), problem);
      return;
    }
    request.setAttribute(DEPLOYMENT_ATTRIBUTE, deploymentId);
    chain.doFilter(request, response);
  }

  private static List<Credential> readCredentials(Path directory) {
    if (!Files.isDirectory(directory)) {
      throw new IllegalStateException("The conversion credentials directory is unavailable");
    }
    try (var files = Files.list(directory)) {
      List<Credential> loaded =
          files
              .filter(Files::isRegularFile)
              .sorted()
              .map(ConversionAuthenticationFilter::readCredential)
              .toList();
      if (loaded.isEmpty()) {
        throw new IllegalStateException("No conversion credentials are configured");
      }
      long distinctTokens =
          loaded.stream()
              .map(credential -> Base64.getEncoder().encodeToString(credential.token()))
              .distinct()
              .count();
      if (distinctTokens != loaded.size()) {
        throw new IllegalStateException("Conversion bearer tokens must be unique");
      }
      return loaded;
    } catch (IOException e) {
      throw new IllegalStateException("Conversion credentials could not be read", e);
    }
  }

  private static Credential readCredential(Path file) {
    String deploymentId = file.getFileName().toString();
    if (!deploymentId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) {
      throw new IllegalStateException("Invalid conversion deployment identifier: " + deploymentId);
    }
    try {
      byte[] token =
          Files.readString(file, StandardCharsets.UTF_8).strip().getBytes(StandardCharsets.UTF_8);
      if (token.length < 32) {
        throw new IllegalStateException(
            "Conversion bearer tokens must contain at least 32 UTF-8 bytes");
      }
      return new Credential(deploymentId, token);
    } catch (IOException e) {
      throw new IllegalStateException(
          "Conversion credential could not be read for deployment " + deploymentId, e);
    }
  }

  private record Credential(String deploymentId, byte[] token) {}
}
