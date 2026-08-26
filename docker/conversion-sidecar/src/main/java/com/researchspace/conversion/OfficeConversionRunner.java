package com.researchspace.conversion;

import com.sun.star.document.MacroExecMode;
import com.sun.star.document.UpdateDocMode;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
class OfficeConversionRunner {

  private static final Logger LOG = LoggerFactory.getLogger(OfficeConversionRunner.class);

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
    OfficeConversionLimiter.Permit permit = limiter.acquireWord();
    try {
      return convertWithinLimit(uploadedFile, inputExtension, outputExtension)
          .withCloseAction(permit::close);
    } catch (RuntimeException e) {
      permit.close();
      LOG.warn("Word conversion failed after capacity was acquired", e);
      throw e;
    }
  }

  private ConvertedFile convertWithinLimit(
      Path uploadedFile, String inputExtension, String outputExtension) {
    Path requestDirectory = createRequestDirectory();
    Path input = requestDirectory.resolve("input." + inputExtension);
    Path output = requestDirectory.resolve("output." + outputExtension);
    LocalOfficeManager manager = null;
    Path pipeAlias = null;
    try {
      Files.move(uploadedFile, input);
      Files.createDirectories(requestDirectory.resolve("home"));
      Files.createDirectories(requestDirectory.resolve("tmp"));
      Files.createDirectories(requestDirectory.resolve("ipc"));
      String pipeName = "rspace-" + UUID.randomUUID();
      pipeAlias = createPipeAlias(requestDirectory, pipeName);
      manager =
          LocalOfficeManager.builder()
              .officeHome(properties.officeHome().toFile())
              .workingDir(requestDirectory.toFile())
              .pipeNames(pipeName)
              .runAsArgs(sandbox.commandPrefix(requestDirectory).toArray(String[]::new))
              .startFailFast(true)
              .taskQueueTimeout(properties.conversionTimeout().toMillis())
              .taskExecutionTimeout(properties.conversionTimeout().toMillis())
              .build();
      manager.start();
      var builder =
          LocalConverter.builder()
              .officeManager(manager)
              .loadProperty("MacroExecutionMode", MacroExecMode.NEVER_EXECUTE)
              .loadProperty("UpdateDocMode", UpdateDocMode.NO_UPDATE);
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
      LOG.warn("LibreOffice conversion was rejected", e);
      throw e;
    } catch (IOException e) {
      deleteQuietly(requestDirectory);
      LOG.error("LibreOffice conversion failed while accessing request storage", e);
      throw new ConversionException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          ConversionError.FAILED,
          "The conversion could not be completed",
          e);
    } catch (OfficeException e) {
      deleteQuietly(requestDirectory);
      LOG.warn("LibreOffice rejected the conversion", e);
      throw new ConversionException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          ConversionError.FAILED,
          "LibreOffice could not convert the document",
          e);
    } catch (RuntimeException e) {
      deleteQuietly(requestDirectory);
      LOG.error("LibreOffice conversion failed unexpectedly", e);
      throw new ConversionException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          ConversionError.FAILED,
          "The conversion could not be completed",
          e);
    } finally {
      OfficeUtils.stopQuietly(manager);
      deletePipeAlias(pipeAlias);
    }
  }

  private Path createPipeAlias(Path requestDirectory, String pipeName) {
    try {
      String uid = Files.getAttribute(Path.of("/proc/self"), "unix:uid").toString();
      Path socketDirectory = Path.of("/tmp");
      Path alias = socketDirectory.resolve("OSL_PIPE_" + uid + "_" + pipeName);
      Path socket = requestDirectory.resolve("ipc").resolve("OSL_PIPE_" + pipeName);
      Files.createSymbolicLink(alias, socket.toAbsolutePath());
      return alias;
    } catch (IOException | UnsupportedOperationException e) {
      LOG.error("Could not create the LibreOffice pipe alias", e);
      throw new ConversionException(
          HttpStatus.SERVICE_UNAVAILABLE,
          ConversionError.SERVICE_UNAVAILABLE,
          "LibreOffice IPC is unavailable",
          e);
    }
  }

  private void deletePipeAlias(Path alias) {
    if (alias == null) {
      return;
    }
    try {
      Files.deleteIfExists(alias);
    } catch (IOException e) {
      LOG.warn("Could not remove LibreOffice pipe alias {}", alias, e);
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
      LOG.error("Could not create conversion request storage", e);
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
    } catch (IOException e) {
      LOG.warn("Could not remove a failed LibreOffice conversion directory", e);
    }
  }
}
