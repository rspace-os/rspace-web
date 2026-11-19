package com.researchspace.conversion;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

/** Removes this container's abandoned request files before accepting new work. */
@Component
final class ConversionWorkspace {
  private final ConverterProperties properties;

  ConversionWorkspace(ConverterProperties properties) {
    this.properties = properties;
  }

  @PostConstruct
  void initialize() throws IOException {
    clean(properties.workingDirectory(), "request-", "pdf-");
    clean(
        Path.of(System.getProperty("java.io.tmpdir")),
        "conversion-upload-",
        "OSL_PIPE_10001_rspace-");
    Files.createDirectories(properties.workingDirectory());
  }

  static void clean(Path directory, String... prefixes) throws IOException {
    if (!Files.isDirectory(directory)) {
      return;
    }
    try (var entries = Files.list(directory)) {
      for (Path entry : entries.toList()) {
        for (String prefix : prefixes) {
          if (entry.getFileName().toString().startsWith(prefix)) {
            new ConvertedFile(entry, entry, "").close();
            break;
          }
        }
      }
    }
  }
}
