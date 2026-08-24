package com.researchspace.conversion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

record ConvertedFile(Path requestDirectory, Path file, String contentType)
    implements AutoCloseable {

  @Override
  public void close() throws IOException {
    if (!Files.exists(requestDirectory)) {
      return;
    }
    try (var paths = Files.walk(requestDirectory)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(path);
      }
    }
  }
}
