package com.researchspace.conversion;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("converter")
public record ConverterProperties(
    Path officeHome,
    Path workingDirectory,
    Duration conversionTimeout,
    Duration connectionRequestTimeout,
    Duration connectTimeout,
    Duration responseTimeout,
    int maxConcurrentOfficeConversions,
    long maxOutputBytes,
    Path sandboxExecutable) {

  public ConverterProperties {
    if (maxConcurrentOfficeConversions < 1) {
      throw new IllegalArgumentException(
          "converter.max-concurrent-office-conversions must be positive");
    }
  }
}
