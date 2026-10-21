package com.researchspace.conversion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockMultipartHttpServletRequest;

class ConversionControllerLifecycleTest {
  @TempDir Path directory;

  private ConverterProperties properties(Duration timeout) {
    return new ConverterProperties(
        directory, directory, timeout, timeout, timeout, timeout, 1, 1000000, directory);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void rejectsBusyRequestsBeforeArchiveValidation(boolean pdf) {
    var properties = properties(Duration.ofSeconds(5));
    var limiter = new OfficeConversionLimiter(properties, new SimpleMeterRegistry());
    var validator = mock(ArchiveValidator.class);
    var controller =
        new ConversionController(
            properties,
            validator,
            mock(OfficeConversionRunner.class),
            mock(GotenbergProxy.class),
            limiter);
    try (var occupied = pdf ? limiter.acquirePdf() : limiter.acquireWord()) {
      var request = request(pdf ? "files" : "file", "input.docx", new byte[] {1});
      var exception =
          assertThrows(
              ConversionException.class,
              () -> {
                if (pdf) {
                  controller.toPdf(request);
                } else {
                  controller.toHtml(request);
                }
              });
      assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.status());
      verifyNoInteractions(validator);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void releasesCapacityWhenValidationFails(boolean pdf) {
    var properties = properties(Duration.ofSeconds(5));
    var limiter = new OfficeConversionLimiter(properties, new SimpleMeterRegistry());
    var runner = mock(OfficeConversionRunner.class);
    var proxy = mock(GotenbergProxy.class);
    var controller =
        new ConversionController(properties, new ArchiveValidator(), runner, proxy, limiter);
    var request = request(pdf ? "files" : "file", "input.docx", new byte[] {1});
    assertThrows(
        ConversionException.class,
        () -> {
          if (pdf) {
            controller.toPdf(request);
          } else {
            controller.toHtml(request);
          }
        });
    assertTrue(pdf ? limiter.hasPdfCapacity() : limiter.hasWordCapacity());
    verifyNoInteractions(runner, proxy);
  }

  @Test
  void rejectsPackagedPdfBombBeforeProxy() throws Exception {
    var properties = properties(Duration.ofSeconds(5));
    var limiter = new OfficeConversionLimiter(properties, new SimpleMeterRegistry());
    var proxy = mock(GotenbergProxy.class);
    var controller =
        new ConversionController(
            properties, new ArchiveValidator(), mock(OfficeConversionRunner.class), proxy, limiter);
    byte[] archive;
    try (var bytes = new ByteArrayOutputStream();
        var zip = new ZipOutputStream(bytes)) {
      zip.putNextEntry(new ZipEntry("payload.xml"));
      zip.write(new byte[1024 * 1024]);
      zip.close();
      archive = bytes.toByteArray();
    }
    var request = request("files", "input.xlsx", archive);
    var exception = assertThrows(ConversionException.class, () -> controller.toPdf(request));
    assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, exception.status());
    verifyNoInteractions(proxy);
    assertTrue(limiter.hasPdfCapacity());
  }

  @Test
  void removesPartialUploadWhenTransferFails() throws Exception {
    var properties = properties(Duration.ofSeconds(5));
    var limiter = new OfficeConversionLimiter(properties, new SimpleMeterRegistry());
    var controller =
        new ConversionController(
            properties,
            new ArchiveValidator(),
            mock(OfficeConversionRunner.class),
            mock(GotenbergProxy.class),
            limiter);
    var partial = new AtomicReference<Path>();
    var request = new MockMultipartHttpServletRequest();
    request.addFile(
        new MockMultipartFile("file", "input.doc", "application/octet-stream", new byte[] {1}) {
          @Override
          public void transferTo(Path destination) throws IOException {
            partial.set(destination);
            Files.writeString(destination, "partial upload");
            throw new IOException("copy failed");
          }
        });
    assertThrows(ConversionException.class, () -> controller.toHtml(request));
    assertNotNull(partial.get());
    assertFalse(Files.exists(partial.get()));
    assertTrue(limiter.hasWordCapacity());
  }

  @Test
  void responseTransferUsesRemainingConversionDeadlineAndReleasesCapacity() throws Exception {
    var properties = properties(Duration.ofMillis(400));
    var limiter = new OfficeConversionLimiter(properties, new SimpleMeterRegistry());
    var runner = mock(OfficeConversionRunner.class);
    var controller =
        new ConversionController(
            properties, new ArchiveValidator(), runner, mock(GotenbergProxy.class), limiter);
    Path resultDirectory = Files.createDirectory(directory.resolve("result"));
    Path output = Files.write(resultDirectory.resolve("output.html"), new byte[1024 * 1024]);
    when(runner.convert(any(), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              Thread.sleep(200);
              return new ConvertedFile(resultDirectory, output, "text/html");
            });
    long started = System.nanoTime();
    var response = controller.toHtml(request("file", "input.doc", new byte[] {1}));
    assertFalse(limiter.hasWordCapacity());
    assertEquals(Files.size(output), response.getHeaders().getContentLength());
    var target =
        new OutputStream() {
          final CountDownLatch closed = new CountDownLatch(1);

          @Override
          public void write(int value) {
            fail("Expected buffered writes");
          }

          @Override
          public void write(byte[] bytes, int offset, int length) throws IOException {
            try {
              Thread.sleep(100);
            } catch (InterruptedException e) {
              LoggerFactory.getLogger(getClass()).warn("Test transfer interrupted", e);
              Thread.currentThread().interrupt();
              throw new IOException(e);
            }
          }

          @Override
          public void close() {
            closed.countDown();
          }
        };
    var exception =
        assertThrows(ConversionException.class, () -> response.getBody().writeTo(target));
    assertEquals(HttpStatus.GATEWAY_TIMEOUT, exception.status());
    assertTrue(Duration.ofNanos(System.nanoTime() - started).toMillis() < 650);
    assertFalse(target.closed.await(100, TimeUnit.MILLISECONDS));
    assertTrue(limiter.hasWordCapacity());
    assertFalse(Files.exists(resultDirectory));
    assertFalse(Thread.currentThread().isInterrupted());
  }

  private MockMultipartHttpServletRequest request(String part, String name, byte[] bytes) {
    var request = new MockMultipartHttpServletRequest();
    request.addFile(new MockMultipartFile(part, name, "application/octet-stream", bytes));
    return request;
  }
}
