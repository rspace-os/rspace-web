package com.researchspace.conversion;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("converter")
public record ConverterProperties(
    Path officeHome,
    Path workingDirectory,
    Duration conversionTimeout,
    int maxConcurrentOfficeConversions,
    int maxConcurrentOfficeConversionsPerDeployment,
    long maxOutputBytes,
    Path sandboxExecutable,
    Path credentialsDirectory) {

  public ConverterProperties {
    if (maxConcurrentOfficeConversions < 1) {
      throw new IllegalArgumentException(
          "converter.max-concurrent-office-conversions must be positive");
    }
    if (maxConcurrentOfficeConversionsPerDeployment < 1) {
      throw new IllegalArgumentException(
          "converter.max-concurrent-office-conversions-per-deployment must be positive");
    }
  }
}
