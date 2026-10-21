package com.researchspace.documentconversion.ext;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;

/** Creates the document-conversion clients from deployment properties. */
public final class ConversionSidecarClientFactory {

  private ConversionSidecarClientFactory() {}

  public record Clients(PdfConversionClient pdf, JodConverterClient word) {}

  public record Settings(
      long connectionRequestTimeoutMs,
      long connectTimeoutMs,
      long conversionTimeoutMs,
      long responseTimeoutMs,
      long maxInputBytes,
      long maxOutputBytes,
      long maxHtmlBytes) {
    public Settings(
        @Value("${conversion.connectionRequestTimeoutMs:2000}") long connectionRequestTimeoutMs,
        @Value("${conversion.connectTimeoutMs:5000}") long connectTimeoutMs,
        @Value("${conversion.conversionTimeoutMs:180000}") long conversionTimeoutMs,
        @Value("${conversion.responseTimeoutMs:185000}") long responseTimeoutMs,
        @Value("${conversion.maxInputBytes:209715200}") long maxInputBytes,
        @Value("${conversion.maxOutputBytes:314572800}") long maxOutputBytes,
        @Value("${conversion.maxHtmlBytes:52428800}") long maxHtmlBytes) {
      this.connectionRequestTimeoutMs = connectionRequestTimeoutMs;
      this.connectTimeoutMs = connectTimeoutMs;
      this.conversionTimeoutMs = conversionTimeoutMs;
      this.responseTimeoutMs = responseTimeoutMs;
      this.maxInputBytes = maxInputBytes;
      this.maxOutputBytes = maxOutputBytes;
      this.maxHtmlBytes = maxHtmlBytes;
    }
  }

  public static Clients create(String serviceUrl, Settings settings) {
    if (serviceUrl.isBlank()) {
      throw new IllegalStateException("conversion.url must be set");
    }
    ConversionSidecarHttpClient sidecar =
        new ConversionSidecarHttpClient(
            serviceUrl,
            Duration.ofMillis(settings.connectionRequestTimeoutMs()),
            Duration.ofMillis(settings.connectTimeoutMs()),
            Duration.ofMillis(settings.conversionTimeoutMs()),
            Duration.ofMillis(settings.responseTimeoutMs()),
            settings.maxInputBytes(),
            settings.maxOutputBytes());
    sidecar.requireCapabilities();
    return new Clients(
        new PdfConversionClient(sidecar), new JodConverterClient(sidecar, settings.maxHtmlBytes()));
  }
}
