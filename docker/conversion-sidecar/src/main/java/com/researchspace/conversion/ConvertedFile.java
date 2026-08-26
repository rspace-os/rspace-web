package com.researchspace.conversion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

record ConvertedFile(Path requestDirectory, Path file, String contentType, Runnable afterClose)
    implements AutoCloseable {

  ConvertedFile(Path requestDirectory, Path file, String contentType) {
    this(requestDirectory, file, contentType, () -> {});
  }

  ConvertedFile withCloseAction(Runnable closeAction) {
    return new ConvertedFile(
        requestDirectory,
        file,
        contentType,
        () -> {
          try {
            closeAction.run();
          } finally {
            afterClose.run();
          }
        });
  }

  @Override
  public void close() throws IOException {
    try {
      if (Files.exists(requestDirectory)) {
        try (var paths = Files.walk(requestDirectory)) {
          for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
            Files.deleteIfExists(path);
          }
        }
      }
    } finally {
      afterClose.run();
    }
  }
}
