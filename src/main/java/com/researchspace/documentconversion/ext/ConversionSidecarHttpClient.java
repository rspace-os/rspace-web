package com.researchspace.documentconversion.ext;

import com.researchspace.documentconversion.spi.ConversionResult;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BoundedInputStream;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

/** HTTP boundary shared by the PDF and Word conversion clients. */
public final class ConversionSidecarHttpClient {

  private static final String ERROR_HEADER = "X-RSpace-Conversion-Error";

  @FunctionalInterface
  interface OutputValidator {
    void validate(Path output) throws IOException;
  }

  private final RestClient client;
  private final long maxInputBytes;
  private final long maxOutputBytes;

  ConversionSidecarHttpClient(RestClient client, long maxOutputBytes) {
    this(client, Long.MAX_VALUE, maxOutputBytes);
  }

  ConversionSidecarHttpClient(RestClient client, long maxInputBytes, long maxOutputBytes) {
    this.client = client;
    this.maxInputBytes = maxInputBytes;
    this.maxOutputBytes = maxOutputBytes;
  }

  public ConversionSidecarHttpClient(
      String serviceUrl,
      Duration connectionRequestTimeout,
      Duration connectTimeout,
      Duration responseTimeout,
      long maxInputBytes,
      long maxOutputBytes) {
    URI origin = validateOrigin(serviceUrl);
    RequestConfig requestConfig =
        RequestConfig.custom()
            .setConnectionRequestTimeout(Timeout.of(connectionRequestTimeout))
            .setConnectTimeout(Timeout.of(connectTimeout))
            .setResponseTimeout(Timeout.of(responseTimeout))
            .build();
    var apacheClient =
        HttpClients.custom()
            .setDefaultRequestConfig(requestConfig)
            .disableAutomaticRetries()
            .disableCookieManagement()
            .disableRedirectHandling()
            .disableContentCompression()
            .build();
    this.client =
        RestClient.builder()
            .baseUrl(origin.toString())
            .requestFactory(new HttpComponentsClientHttpRequestFactory(apacheClient))
            .defaultHeader(HttpHeaders.ACCEPT_ENCODING, "identity")
            .build();
    this.maxInputBytes = maxInputBytes;
    this.maxOutputBytes = maxOutputBytes;
  }

  public void requireCapabilities() {
    Map<?, ?> capabilities =
        client
            .get()
            .uri("/v1/capabilities")
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .body(Map.class);
    if (capabilities == null
        || !"rspace-conversion-sidecar".equals(capabilities.get("protocol"))
        || !Integer.valueOf(1).equals(capabilities.get("version"))
        || !(capabilities.get("roles") instanceof Collection<?> roles)
        || !roles.containsAll(Set.of("pdf", "word"))) {
      throw new IllegalStateException(
          "conversion.url does not identify a compatible RSpace conversion sidecar");
    }
  }

  ConversionResult postFile(
      String route,
      String partName,
      File input,
      File output,
      MediaType expectedType,
      OutputValidator validator) {
    if (!input.isFile() || input.length() > maxInputBytes) {
      deleteEmptyOutput(output);
      return new ConversionResult(
          input.isFile()
              ? DocumentConversionError.INPUT_TOO_LARGE.code()
              : DocumentConversionError.INPUT_INVALID.code());
    }
    var body = new LinkedMultiValueMap<String, Object>();
    body.add(partName, new FileSystemResource(input));
    Path partial = output.toPath().resolveSibling(output.getName() + ".partial");
    try {
      ConversionResult result =
          client
              .post()
              .uri(route)
              .contentType(MediaType.MULTIPART_FORM_DATA)
              .accept(expectedType)
              .body(body)
              .exchange(
                  (request, response) -> {
                    if (!response.getStatusCode().is2xxSuccessful()) {
                      int status = response.getStatusCode().value();
                      String errorCode = response.getHeaders().getFirst(ERROR_HEADER);
                      DocumentConversionError error =
                          DocumentConversionError.fromCode(errorCode)
                              .orElseGet(() -> errorForStatus(status));
                      return new ConversionResult(error.code());
                    }
                    MediaType actualType = response.getHeaders().getContentType();
                    if (actualType == null || !expectedType.isCompatibleWith(actualType)) {
                      return new ConversionResult(DocumentConversionError.OUTPUT_INVALID.code());
                    }
                    try (InputStream inputStream = response.getBody()) {
                      copyBounded(inputStream, partial, maxOutputBytes);
                    }
                    validator.validate(partial);
                    Files.move(
                        partial,
                        output.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
                    return new ConversionResult(output, expectedType.toString());
                  });
      if (!result.isSuccessful()) {
        deleteEmptyOutput(output);
      }
      return result;
    } catch (Exception e) {
      deleteEmptyOutput(output);
      return new ConversionResult(errorForException(e).code());
    } finally {
      FileUtils.deleteQuietly(partial.toFile());
    }
  }

  private static URI validateOrigin(String serviceUrl) {
    URI origin;
    try {
      origin = URI.create(serviceUrl);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("conversion.url is invalid", e);
    }
    String host = origin.getHost();
    if (!origin.isAbsolute()
        || !"http".equals(origin.getScheme())
        || host == null
        || origin.getUserInfo() != null
        || origin.getQuery() != null
        || origin.getFragment() != null
        || (origin.getPath() != null
            && !origin.getPath().isEmpty()
            && !"/".equals(origin.getPath()))) {
      throw new IllegalStateException("conversion.url must be an absolute HTTP origin");
    }
    String normalized = origin.toString();
    return URI.create(
        normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized);
  }

  private static DocumentConversionError errorForStatus(int status) {
    return switch (status) {
      case 401 -> DocumentConversionError.SERVICE_UNAVAILABLE;
      case 413 -> DocumentConversionError.INPUT_TOO_LARGE;
      case 415 -> DocumentConversionError.UNSUPPORTED;
      case 422 -> DocumentConversionError.FAILED;
      case 429 -> DocumentConversionError.SERVICE_BUSY;
      case 502, 503 -> DocumentConversionError.SERVICE_UNAVAILABLE;
      case 504 -> DocumentConversionError.TIMEOUT;
      default -> DocumentConversionError.FAILED;
    };
  }

  private static DocumentConversionError errorForException(Exception exception) {
    for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
      var logicalError = DocumentConversionError.fromCode(cause.getMessage());
      if (logicalError.isPresent()) {
        return logicalError.get();
      }
    }
    return DocumentConversionError.FAILED;
  }

  private static void deleteEmptyOutput(File output) {
    if (output != null && output.length() == 0) {
      FileUtils.deleteQuietly(output);
    }
  }

  private static void copyBounded(InputStream input, Path output, long limit) throws IOException {
    try (var bounded =
            BoundedInputStream.builder()
                .setInputStream(input)
                .setMaxCount(limit + 1)
                .setPropagateClose(false)
                .get();
        var target = Files.newOutputStream(output)) {
      IOUtils.copyLarge(bounded, target);
      if (bounded.getCount() > limit) {
        throw new IOException(DocumentConversionError.OUTPUT_TOO_LARGE.code());
      }
    }
  }
}
