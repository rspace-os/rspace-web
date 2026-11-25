package com.researchspace.documentconversion.ext;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/** Creates the document-conversion clients from deployment properties. */
public final class ConversionSidecarClientFactory {

  private static final Logger LOG = LoggerFactory.getLogger(ConversionSidecarClientFactory.class);

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
    try {
      sidecar.requireCapabilities();
    } catch (ResourceAccessException | HttpServerErrorException e) {
      // An unreachable or failing sidecar must not stop RSpace starting; conversions fail until it
      // is back. Any other answer (4xx, non-JSON, wrong protocol) means conversion.url names the
      // wrong service, and still fails startup.
      LOG.warn(
          "Could not reach the conversion sidecar at startup; document conversions will fail"
              + " until it is reachable",
          e);
    }
    return new Clients(
        new PdfConversionClient(sidecar), new JodConverterClient(sidecar, settings.maxHtmlBytes()));
  }
}
