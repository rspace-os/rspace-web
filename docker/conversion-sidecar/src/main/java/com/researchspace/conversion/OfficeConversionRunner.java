package com.researchspace.conversion;

import com.sun.star.document.MacroExecMode;
import com.sun.star.document.UpdateDocMode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jodconverter.core.document.DefaultDocumentFormatRegistry;
import org.jodconverter.core.document.DocumentFamily;
import org.jodconverter.core.document.DocumentFormat;
import org.jodconverter.core.office.OfficeException;
import org.jodconverter.core.office.OfficeManager;
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

  OfficeConversionRunner(ConverterProperties properties, LibreOfficeSandbox sandbox) {
    this.properties = properties;
    this.sandbox = sandbox;
  }

  ConvertedFile convert(
      Path uploadedFile,
      String inputExtension,
      String outputExtension,
      ConversionDeadline deadline) {
    Path requestDirectory = createRequestDirectory();
    var processes = new LibreOfficeSandbox.RequestProcesses(requestDirectory);
    boolean completed = false;
    try {
      deadline.abortOnTimeout(() -> deadline.cleanupBeforeRelease(processes.terminate()));
      ConvertedFile result =
          convertBeforeDeadline(
              uploadedFile, inputExtension, outputExtension, deadline, requestDirectory, processes);
      completed = true;
      return result;
    } catch (RuntimeException e) {
      LOG.warn("Word conversion did not complete", e);
      throw e;
    } finally {
      processes.noMoreStarts();
      if (deadline.isExpired()) {
        Thread.interrupted();
        var terminated = processes.terminate();
        deadline.cleanupBeforeRelease(terminated.thenRun(() -> deleteQuietly(requestDirectory)));
        try {
          terminated.get(1, TimeUnit.SECONDS);
        } catch (Exception e) {
          LOG.warn("Timed out waiting for the request's LibreOffice processes to exit", e);
        }
        deadline.check();
      } else if (!completed) {
        deleteQuietly(requestDirectory);
      }
    }
  }

  // Package-visible so lifecycle tests can simulate native conversion hangs.
  ConvertedFile convertBeforeDeadline(
      Path uploadedFile,
      String inputExtension,
      String outputExtension,
      ConversionDeadline deadline,
      Path requestDirectory,
      LibreOfficeSandbox.RequestProcesses processes) {
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
              .processTimeout(properties.conversionTimeout().toMillis())
              .taskQueueTimeout(properties.conversionTimeout().toMillis())
              .taskExecutionTimeout(properties.conversionTimeout().toMillis())
              .build();
      deadline.check();
      LocalOfficeManager startingManager = manager;
      var started =
          processes.start(
              () -> {
                try {
                  startingManager.start();
                } catch (OfficeException e) {
                  LOG.warn("LibreOffice startup failed", e);
                  throw new CompletionException(e);
                }
              });
      try {
        started.get();
      } catch (InterruptedException e) {
        LOG.warn("Interrupted while waiting for LibreOffice startup", e);
        Thread.currentThread().interrupt();
        deadline.check();
        throw new OfficeException("LibreOffice startup was interrupted", e);
      } catch (ExecutionException e) {
        LOG.warn("LibreOffice startup did not complete", e);
        throw new OfficeException("LibreOffice startup failed", e);
      }
      deadline.check();
      convertDocument(input, inputExtension, output, outputExtension, manager);
      deadline.check();
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
      deadline.check();
      LOG.warn("LibreOffice conversion was rejected", e);
      throw e;
    } catch (IOException e) {
      deadline.check();
      LOG.error("LibreOffice conversion failed while accessing request storage", e);
      throw new ConversionException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          ConversionError.FAILED,
          "The conversion could not be completed",
          e);
    } catch (OfficeException e) {
      deadline.check();
      LOG.warn("LibreOffice rejected the conversion", e);
      throw new ConversionException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          ConversionError.FAILED,
          "LibreOffice could not convert the document",
          e);
    } catch (RuntimeException e) {
      deadline.check();
      LOG.error("LibreOffice conversion failed unexpectedly", e);
      throw new ConversionException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          ConversionError.FAILED,
          "The conversion could not be completed",
          e);
    } finally {
      // UNO termination can block indefinitely; never wait past the request deadline.
      LocalOfficeManager officeManager = manager;
      Path alias = pipeAlias;
      var stopped =
          processes.stop(
              () -> {
                try {
                  OfficeUtils.stopQuietly(officeManager);
                } finally {
                  deletePipeAlias(alias);
                }
              });
      if (!deadline.isExpired()) {
        try {
          stopped.get(deadline.remainingNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
          LOG.warn("Interrupted while stopping LibreOffice", e);
          Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
          LOG.warn("Could not stop LibreOffice before the request deadline", e);
        }
      }
    }
  }

  // Keep the JOD format decision testable without launching native LibreOffice.
  void convertDocument(
      Path input, String inputExtension, Path output, String outputExtension, OfficeManager manager)
      throws OfficeException {
    var builder =
        LocalConverter.builder()
            .officeManager(manager)
            .loadProperty("MacroExecutionMode", MacroExecMode.NEVER_EXECUTE)
            .loadProperty("UpdateDocMode", UpdateDocMode.NO_UPDATE);
    if ("html".equals(outputExtension)) {
      builder.storeProperty("FilterName", "HTML (StarWriter)").storeProperty("EmbedImages", true);
    }
    var converter = builder.build();
    DocumentFormat writerHtml = writerHtmlFormat(inputExtension);
    if (writerHtml != null) {
      converter
          .convert(input.toFile())
          .as(writerHtml)
          .to(output.toFile())
          .as(DefaultDocumentFormatRegistry.DOCX)
          .execute();
    } else {
      converter.convert(input.toFile()).to(output.toFile()).execute();
    }
  }

  private static DocumentFormat writerHtmlFormat(String extension) {
    return Set.of("html", "htm").contains(extension)
        ? DocumentFormat.builder(DefaultDocumentFormatRegistry.HTML)
            .inputFamily(DocumentFamily.TEXT)
            .loadFilterName("HTML (StarWriter)")
            .build()
        : null;
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
    } catch (IOException | RuntimeException e) {
      LOG.warn("Could not remove a failed LibreOffice conversion directory", e);
    }
  }
}
