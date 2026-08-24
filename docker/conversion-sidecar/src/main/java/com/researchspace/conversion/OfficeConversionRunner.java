package com.researchspace.conversion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.UUID;
import org.jodconverter.core.document.DefaultDocumentFormatRegistry;
import org.jodconverter.core.document.DocumentFamily;
import org.jodconverter.core.document.DocumentFormat;
import org.jodconverter.core.office.OfficeException;
import org.jodconverter.core.office.OfficeUtils;
import org.jodconverter.local.LocalConverter;
import org.jodconverter.local.office.LocalOfficeManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
class OfficeConversionRunner {

  private final ConverterProperties properties;
  private final LibreOfficeSandbox sandbox;
  private final OfficeConversionLimiter limiter;

  OfficeConversionRunner(
      ConverterProperties properties, LibreOfficeSandbox sandbox, OfficeConversionLimiter limiter) {
    this.properties = properties;
    this.sandbox = sandbox;
    this.limiter = limiter;
  }

  ConvertedFile convert(Path uploadedFile, String inputExtension, String outputExtension) {
    return limiter.run(() -> convertWithinLimit(uploadedFile, inputExtension, outputExtension));
  }

  private ConvertedFile convertWithinLimit(
      Path uploadedFile, String inputExtension, String outputExtension) {
    Path requestDirectory = createRequestDirectory();
    Path input = requestDirectory.resolve("input." + inputExtension);
    Path output = requestDirectory.resolve("output." + outputExtension);
    LocalOfficeManager manager = null;
    try {
      Files.move(uploadedFile, input);
      Files.createDirectories(requestDirectory.resolve("home"));
      manager =
          LocalOfficeManager.builder()
              .officeHome(properties.officeHome().toFile())
              .workingDir(requestDirectory.toFile())
              .pipeNames("rspace-" + UUID.randomUUID())
              .runAsArgs(sandbox.commandPrefix(requestDirectory).toArray(String[]::new))
              .taskQueueTimeout(properties.conversionTimeout().toMillis())
              .taskExecutionTimeout(properties.conversionTimeout().toMillis())
              .build();
      manager.start();
      var builder = LocalConverter.builder().officeManager(manager);
      if ("html".equals(outputExtension)) {
        builder.storeProperty("FilterName", "HTML (StarWriter)").storeProperty("EmbedImages", true);
      }
      var converter = builder.build();
      if ("html".equals(inputExtension)) {
        DocumentFormat writerHtml =
            DocumentFormat.builder(DefaultDocumentFormatRegistry.HTML)
                .inputFamily(DocumentFamily.TEXT)
                .loadFilterName("HTML (StarWriter)")
                .build();
        converter
            .convert(input.toFile())
            .as(writerHtml)
            .to(output.toFile())
            .as(DefaultDocumentFormatRegistry.DOCX)
            .execute();
      } else {
        converter.convert(input.toFile()).to(output.toFile()).execute();
      }
      if (!Files.isRegularFile(output)) {
        throw new ConversionException(
            HttpStatus.BAD_GATEWAY,
            ConversionError.OUTPUT_INVALID,
            "The converted file is missing");
      }
      if (Files.size(output) > properties.maxOutputBytes()) {
        throw new ConversionException(
            HttpStatus.PAYLOAD_TOO_LARGE,
            ConversionError.OUTPUT_TOO_LARGE,
            "The converted file exceeds the output limit");
      }
      return new ConvertedFile(
          requestDirectory,
          output,
          "html".equals(outputExtension)
              ? "text/html; charset=UTF-8"
              : "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    } catch (ConversionException e) {
      deleteQuietly(requestDirectory);
      throw e;
    } catch (IOException e) {
      deleteQuietly(requestDirectory);
      throw new ConversionException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          ConversionError.FAILED,
          "The conversion could not be completed",
          e);
    } catch (OfficeException e) {
      deleteQuietly(requestDirectory);
      throw new ConversionException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          ConversionError.FAILED,
          "LibreOffice could not convert the document",
          e);
    } catch (RuntimeException e) {
      deleteQuietly(requestDirectory);
      throw new ConversionException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          ConversionError.FAILED,
          "The conversion could not be completed",
          e);
    } finally {
      OfficeUtils.stopQuietly(manager);
    }
  }

  private Path createRequestDirectory() {
    try {
      Files.createDirectories(properties.workingDirectory());
      Path directory = Files.createTempDirectory(properties.workingDirectory(), "request-");
      if (Files.getFileStore(directory).supportsFileAttributeView("posix")) {
        Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
      }
      return directory;
    } catch (IOException e) {
      throw new ConversionException(
          HttpStatus.SERVICE_UNAVAILABLE,
          ConversionError.SERVICE_UNAVAILABLE,
          "Conversion storage is unavailable",
          e);
    }
  }

  private void deleteQuietly(Path directory) {
    try {
      new ConvertedFile(directory, directory, "").close();
    } catch (IOException ignored) {
      // Cleanup is best effort after the primary conversion failure.
    }
  }
}
