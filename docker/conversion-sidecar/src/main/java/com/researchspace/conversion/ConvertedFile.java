package com.researchspace.conversion;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ConvertedFile implements AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(ConvertedFile.class);

  private final Path requestDirectory;
  private final Path file;
  private final String contentType;
  private final Runnable afterClose;
  private final ConversionDeadline deadline;
  private final AtomicBoolean closed = new AtomicBoolean();

  ConvertedFile(Path requestDirectory, Path file, String contentType) {
    this(requestDirectory, file, contentType, () -> {}, null);
  }

  private ConvertedFile(
      Path requestDirectory,
      Path file,
      String contentType,
      Runnable afterClose,
      ConversionDeadline deadline) {
    this.requestDirectory = requestDirectory;
    this.file = file;
    this.contentType = contentType;
    this.afterClose = afterClose;
    this.deadline = deadline;
  }

  Path requestDirectory() {
    return requestDirectory;
  }

  Path file() {
    return file;
  }

  String contentType() {
    return contentType;
  }

  ConvertedFile withDeadline(ConversionDeadline deadline) {
    return new ConvertedFile(requestDirectory, file, contentType, afterClose, deadline);
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
        },
        deadline);
  }

  void transferTo(OutputStream output) throws IOException {
    try (var input = Files.newInputStream(file)) {
      if (deadline != null) {
        deadline.resume(
            () -> {
              try {
                // The writing thread must fail; closing servlet output can finish a truncated 200.
                input.close();
              } catch (IOException e) {
                LOG.warn("Could not abort response transfer", e);
              }
            });
      }
      byte[] buffer = new byte[8192];
      int read;
      while ((read = input.read(buffer)) != -1) {
        if (deadline != null) {
          deadline.check();
        }
        output.write(buffer, 0, read);
      }
      if (deadline != null) {
        deadline.check();
      }
    } catch (IOException e) {
      LOG.warn("Conversion response transfer failed", e);
      if (deadline != null) {
        deadline.check();
      }
      throw e;
    }
  }

  @Override
  public void close() throws IOException {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    try {
      Files.walkFileTree(
          requestDirectory,
          new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attributes)
                throws IOException {
              Files.deleteIfExists(path);
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path path, IOException failure)
                throws IOException {
              if (!(failure instanceof NoSuchFileException)) {
                throw failure;
              }
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path path, IOException failure)
                throws IOException {
              if (failure != null && !(failure instanceof NoSuchFileException)) {
                throw failure;
              }
              Files.deleteIfExists(path);
              return FileVisitResult.CONTINUE;
            }
          });
    } finally {
      afterClose.run();
    }
  }
}
