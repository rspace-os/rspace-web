package com.researchspace.documentconversion.ext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.core.env.Environment;

/** Creates the document-conversion clients from deployment properties. */
public final class ConversionSidecarClientFactory {

  private ConversionSidecarClientFactory() {}

  public record Clients(PdfConversionClient pdf, JodConverterClient word) {}

  public static Clients create(Environment environment) {
    String serviceUrl = environment.getProperty("conversion.url", "");
    if (serviceUrl.isBlank()) {
      throw new IllegalStateException("conversion.url must be set");
    }
    ConversionSidecarHttpClient sidecar =
        new ConversionSidecarHttpClient(
            serviceUrl,
            duration(environment, "conversion.connectionRequestTimeoutMs", 2_000),
            duration(environment, "conversion.connectTimeoutMs", 5_000),
            duration(environment, "conversion.responseTimeoutMs", 185_000),
            longProperty(environment, "conversion.maxInputBytes", 209_715_200),
            longProperty(environment, "conversion.maxOutputBytes", 314_572_800),
            readBearerToken(environment));
    sidecar.requireCapabilities();
    return new Clients(
        new PdfConversionClient(sidecar),
        new JodConverterClient(
            sidecar, longProperty(environment, "conversion.maxHtmlBytes", 52_428_800)));
  }

  private static Duration duration(Environment environment, String key, long defaultValue) {
    return Duration.ofMillis(longProperty(environment, key, defaultValue));
  }

  private static long longProperty(Environment environment, String key, long defaultValue) {
    return environment.getProperty(key, Long.class, defaultValue);
  }

  static String readBearerToken(Environment environment) {
    String filename = environment.getProperty("conversion.bearerTokenFile", "");
    if (filename.isBlank()) {
      throw new IllegalStateException("conversion.bearerTokenFile must be set");
    }
    try {
      String token = Files.readString(Path.of(filename), StandardCharsets.UTF_8).strip();
      if (token.getBytes(StandardCharsets.UTF_8).length < 32) {
        throw new IllegalStateException(
            "conversion.bearerTokenFile must contain at least 32 UTF-8 bytes");
      }
      return token;
    } catch (IOException e) {
      throw new IllegalStateException("conversion.bearerTokenFile could not be read", e);
    }
  }
}
