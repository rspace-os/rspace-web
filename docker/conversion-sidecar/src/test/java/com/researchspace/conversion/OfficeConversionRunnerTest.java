package com.researchspace.conversion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.jodconverter.core.document.DocumentFamily;
import org.jodconverter.core.job.SourceDocumentSpecs;
import org.jodconverter.core.office.OfficeManager;
import org.jodconverter.core.task.OfficeTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockMultipartHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

class OfficeConversionRunnerTest {
  @TempDir Path directory;

  @ParameterizedTest
  @ValueSource(strings = {"html", "htm"})
  void htmlAliasesUseWriterForDocxExport(String extension) throws Exception {
    var timeout = Duration.ofSeconds(5);
    var properties =
        new ConverterProperties(
            directory, directory, timeout, timeout, timeout, timeout, 1, 10000, directory);
    var runner = new OfficeConversionRunner(properties, mock(LibreOfficeSandbox.class));
    var manager = mock(OfficeManager.class);
    when(manager.isRunning()).thenReturn(true);
    Path input = Files.writeString(directory.resolve("input." + extension), "<p>Document</p>");

    runner.convertDocument(input, extension, directory.resolve("output.docx"), "docx", manager);

    var task = ArgumentCaptor.forClass(OfficeTask.class);
    verify(manager).execute(task.capture());
    var source =
        assertInstanceOf(
            SourceDocumentSpecs.class, ReflectionTestUtils.getField(task.getValue(), "source"));
    assertEquals(DocumentFamily.TEXT, source.getFormat().getInputFamily());
    assertEquals("HTML (StarWriter)", source.getFormat().getLoadProperties().get("FilterName"));
  }

  @Test
  @EnabledOnOs(OS.LINUX)
  void timeoutKillsOnlyThisRequestTreeBeforeReleasingItsPermit() throws Exception {
    var timeout = Duration.ofMillis(500);
    var properties =
        new ConverterProperties(
            directory, directory, timeout, timeout, timeout, timeout, 1, 10000, directory);
    var root = new AtomicReference<Process>();
    var descendants = new AtomicReference<List<ProcessHandle>>(List.of());
    var other = new AtomicReference<Process>();
    var released = new CountDownLatch(1);
    var releasedWhileAlive = new AtomicBoolean();
    var limiter = mock(OfficeConversionLimiter.class);
    when(limiter.acquireWord())
        .thenReturn(
            new OfficeConversionLimiter.Permit(
                () -> {
                  releasedWhileAlive.set(
                      root.get().isAlive()
                          || descendants.get().stream().anyMatch(ProcessHandle::isAlive));
                  released.countDown();
                }));
    var runner =
        new OfficeConversionRunner(properties, mock(LibreOfficeSandbox.class)) {
          @Override
          ConvertedFile convertBeforeDeadline(
              Path upload,
              String input,
              String output,
              ConversionDeadline deadline,
              Path workspace,
              LibreOfficeSandbox.RequestProcesses processes) {
            try {
              other.set(new ProcessBuilder("sh", "-c", "sleep 30", workspace + "-other").start());
              root.set(
                  new ProcessBuilder("sh", "-c", "sleep 30 & wait", workspace.toString()).start());
              long until = System.nanoTime() + Duration.ofMillis(200).toNanos();
              while (descendants.get().isEmpty() && System.nanoTime() < until) {
                descendants.set(root.get().descendants().toList());
                Thread.sleep(5);
              }
              new CountDownLatch(1).await();
              throw new AssertionError("Conversion should have been interrupted");
            } catch (InterruptedException e) {
              LoggerFactory.getLogger(getClass()).warn("Test conversion interrupted", e);
              Thread.currentThread().interrupt();
              deadline.check();
              throw new AssertionError(e);
            } catch (IOException e) {
              LoggerFactory.getLogger(getClass()).warn("Test process failed", e);
              throw new UncheckedIOException(e);
            }
          }
        };
    var controller =
        new ConversionController(
            properties, new ArchiveValidator(), runner, mock(GotenbergProxy.class), limiter);
    var request = new MockMultipartHttpServletRequest();
    request.addFile(
        new MockMultipartFile("file", "input.doc", "application/octet-stream", new byte[] {1}));
    try {
      long started = System.nanoTime();
      var exception = assertThrows(ConversionException.class, () -> controller.toHtml(request));
      assertEquals(HttpStatus.GATEWAY_TIMEOUT, exception.status());
      assertTrue(Duration.ofNanos(System.nanoTime() - started).toMillis() < 2000);
      assertTrue(released.await(2, TimeUnit.SECONDS));
      assertFalse(releasedWhileAlive.get());
      assertFalse(root.get().isAlive());
      assertFalse(descendants.get().isEmpty());
      assertTrue(other.get().isAlive(), "A request with a shared path prefix must survive");
      assertFalse(Thread.currentThread().isInterrupted());
    } finally {
      if (root.get() != null) {
        root.get().descendants().forEach(ProcessHandle::destroyForcibly);
        root.get().destroyForcibly();
      }
      if (other.get() != null) {
        other.get().descendants().forEach(ProcessHandle::destroyForcibly);
        other.get().destroyForcibly();
        other.get().waitFor(2, TimeUnit.SECONDS);
      }
    }
  }
}
