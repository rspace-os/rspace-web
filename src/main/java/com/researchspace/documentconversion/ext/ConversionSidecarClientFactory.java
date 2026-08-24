package com.researchspace.documentconversion.ext;

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
            longProperty(environment, "conversion.maxOutputBytes", 314_572_800));
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
}
