package com.researchspace.documentconversion.ext;

import com.researchspace.documentconversion.spi.ConversionResult;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BoundedInputStream;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ConnectionRequestTimeoutException;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

/** HTTP boundary shared by the PDF and Word conversion clients. */
public final class ConversionSidecarHttpClient {

  private static final String ERROR_HEADER = "X-RSpace-Conversion-Error";
  private static final Logger LOG = LoggerFactory.getLogger(ConversionSidecarHttpClient.class);
  private static final ExecutorService REQUEST_EXECUTOR =
      Executors.newFixedThreadPool(
          4,
          new ThreadFactory() {
            private int sequence;

            @Override
            public synchronized Thread newThread(Runnable task) {
              Thread thread = new Thread(task, "conversion-sidecar-request-" + ++sequence);
              thread.setDaemon(true);
              return thread;
            }
          });

  @FunctionalInterface
  interface OutputValidator {
    void validate(Path output) throws IOException;
  }

  private final RestClient client;
  private final long maxInputBytes;
  private final long maxOutputBytes;
  private final Duration conversionTimeout;

  ConversionSidecarHttpClient(RestClient client, long maxOutputBytes) {
    this(client, Long.MAX_VALUE, maxOutputBytes, Duration.ofMinutes(3));
  }

  ConversionSidecarHttpClient(RestClient.Builder builder, long maxOutputBytes) {
    this(configured(builder).build(), maxOutputBytes);
  }

  ConversionSidecarHttpClient(RestClient client, long maxInputBytes, long maxOutputBytes) {
    this(client, maxInputBytes, maxOutputBytes, Duration.ofMinutes(3));
  }

  ConversionSidecarHttpClient(
      RestClient client, long maxInputBytes, long maxOutputBytes, Duration conversionTimeout) {
    this.client = client;
    this.maxInputBytes = maxInputBytes;
    this.maxOutputBytes = maxOutputBytes;
    this.conversionTimeout = conversionTimeout;
  }

  public ConversionSidecarHttpClient(
      String serviceUrl,
      Duration connectionRequestTimeout,
      Duration connectTimeout,
      Duration conversionTimeout,
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
        configured(
                RestClient.builder()
                    .baseUrl(origin.toString())
                    .requestFactory(new HttpComponentsClientHttpRequestFactory(apacheClient)))
            .build();
    this.maxInputBytes = maxInputBytes;
    this.maxOutputBytes = maxOutputBytes;
    this.conversionTimeout = conversionTimeout;
  }

  private static RestClient.Builder configured(RestClient.Builder builder) {
    return builder.defaultHeader(HttpHeaders.ACCEPT_ENCODING, "identity");
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
    Path partial = output.toPath().resolveSibling(output.getName() + ".partial");
    AtomicBoolean abandoned = new AtomicBoolean();
    Future<ConversionResult> request =
        REQUEST_EXECUTOR.submit(
            () ->
                performPostFile(
                    route, partName, input, output, expectedType, validator, partial, abandoned));
    try {
      ConversionResult result = request.get(conversionTimeout.toMillis(), TimeUnit.MILLISECONDS);
      if (!result.isSuccessful()) {
        deleteEmptyOutput(output);
      }
      return result;
    } catch (TimeoutException e) {
      synchronized (abandoned) {
        abandoned.set(true);
      }
      request.cancel(true);
      FileUtils.deleteQuietly(output);
      LOG.warn("Conversion sidecar request to {} exceeded its wall-clock timeout", route, e);
      return new ConversionResult(DocumentConversionError.TIMEOUT.code());
    } catch (InterruptedException e) {
      synchronized (abandoned) {
        abandoned.set(true);
      }
      request.cancel(true);
      Thread.currentThread().interrupt();
      FileUtils.deleteQuietly(output);
      LOG.warn("Conversion sidecar request to {} was interrupted", route, e);
      return new ConversionResult(DocumentConversionError.FAILED.code());
    } catch (ExecutionException e) {
      deleteEmptyOutput(output);
      DocumentConversionError error = errorForException(e);
      LOG.warn("Conversion sidecar request to {} failed with {}", route, error.code(), e);
      return new ConversionResult(error.code());
    } finally {
      FileUtils.deleteQuietly(partial.toFile());
    }
  }

  private ConversionResult performPostFile(
      String route,
      String partName,
      File input,
      File output,
      MediaType expectedType,
      OutputValidator validator,
      Path partial,
      AtomicBoolean abandoned) {
    var body = new LinkedMultiValueMap<String, Object>();
    body.add(partName, new FileSystemResource(input));
    try {
      return client
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
                synchronized (abandoned) {
                  if (abandoned.get() || Thread.currentThread().isInterrupted()) {
                    throw new IOException(DocumentConversionError.TIMEOUT.code());
                  }
                  Files.move(
                      partial,
                      output.toPath(),
                      StandardCopyOption.REPLACE_EXISTING,
                      StandardCopyOption.ATOMIC_MOVE);
                }
                return new ConversionResult(output, expectedType.toString());
              });
    } catch (Exception e) {
      throw new DocumentConversionRequestException(e);
    } finally {
      if (abandoned.get()) {
        FileUtils.deleteQuietly(partial.toFile());
      }
    }
  }

  private static final class DocumentConversionRequestException extends RuntimeException {
    private DocumentConversionRequestException(Throwable cause) {
      super(cause);
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
      case 413 -> DocumentConversionError.INPUT_TOO_LARGE;
      case 415 -> DocumentConversionError.UNSUPPORTED;
      case 422 -> DocumentConversionError.FAILED;
      case 429 -> DocumentConversionError.SERVICE_BUSY;
      case 502, 503 -> DocumentConversionError.SERVICE_UNAVAILABLE;
      case 504 -> DocumentConversionError.TIMEOUT;
      default -> DocumentConversionError.FAILED;
    };
  }

  static DocumentConversionError errorForException(Exception exception) {
    for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
      var logicalError = DocumentConversionError.fromCode(cause.getMessage());
      if (logicalError.isPresent()) {
        return logicalError.get();
      }
      if (cause instanceof SocketTimeoutException
          || cause instanceof HttpTimeoutException
          || cause instanceof ConnectionRequestTimeoutException) {
        return DocumentConversionError.TIMEOUT;
      }
      if (cause instanceof ConnectException
          || cause instanceof NoRouteToHostException
          || cause instanceof UnknownHostException) {
        return DocumentConversionError.SERVICE_UNAVAILABLE;
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
