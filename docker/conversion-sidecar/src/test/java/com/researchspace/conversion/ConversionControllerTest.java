package com.researchspace.conversion;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockMultipartHttpServletRequest;

class ConversionControllerTest {

  @TempDir java.nio.file.Path directory;

  private final ArchiveValidator archiveValidator = mock(ArchiveValidator.class);
  private final OfficeConversionRunner officeRunner = mock(OfficeConversionRunner.class);
  private final GotenbergProxy gotenbergProxy = mock(GotenbergProxy.class);
  private final ConversionController controller =
      new ConversionController(archiveValidator, officeRunner, gotenbergProxy);

  @Test
  void capabilitiesDeclareBothRequiredRoles() {
    assertEquals("rspace-conversion-sidecar", controller.capabilities().get("protocol"));
    assertEquals(java.util.List.of("pdf", "word"), controller.capabilities().get("roles"));
  }

  @Test
  void wordImportAcceptsValidRequestWithoutAuthorization() throws Exception {
    MockMultipartHttpServletRequest request = request("file", "legacy.doc");
    java.nio.file.Path outputFile = directory.resolve("output.html");
    java.nio.file.Files.writeString(outputFile, "output");
    ConvertedFile output = new ConvertedFile(directory, outputFile, "text/html; charset=UTF-8");
    when(officeRunner.convert(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq("doc"),
            org.mockito.ArgumentMatchers.eq("html")))
        .thenAnswer(
            invocation -> {
              java.nio.file.Files.deleteIfExists(invocation.getArgument(0));
              return output;
            });

    controller.toHtml(request);

    verify(archiveValidator, never())
        .validate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    verify(officeRunner)
        .convert(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq("doc"),
            org.mockito.ArgumentMatchers.eq("html"));
  }

  @Test
  void pdfRouteRejectsCallerGotenbergControls() {
    MockMultipartHttpServletRequest request = request("files", "document.docx");
    request.addHeader("Gotenberg-Output-Filename", "chosen.pdf");

    ConversionException exception =
        assertThrows(ConversionException.class, () -> controller.toPdf(request));

    assertEquals(ConversionError.INPUT_INVALID, exception.error());
    verify(gotenbergProxy, never())
        .convert(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  void pdfRouteGeneratesGotenbergCorrelationId() throws Exception {
    MockMultipartHttpServletRequest request = request("files", "document.docx");
    java.nio.file.Path outputFile = directory.resolve("output.pdf");
    java.nio.file.Files.writeString(outputFile, "%PDF-output");
    ConvertedFile output = new ConvertedFile(directory, outputFile, "application/pdf");
    when(gotenbergProxy.convert(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq("docx"),
            org.mockito.ArgumentMatchers.any()))
        .thenAnswer(
            invocation -> {
              java.nio.file.Files.deleteIfExists(invocation.getArgument(0));
              return output;
            });

    controller.toPdf(request);

    ArgumentCaptor<String> correlationId = ArgumentCaptor.forClass(String.class);
    verify(gotenbergProxy)
        .convert(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq("docx"),
            correlationId.capture());
    assertDoesNotThrow(() -> java.util.UUID.fromString(correlationId.getValue()));
  }

  @Test
  void conversionRoutesRejectExtraParts() {
    MockMultipartHttpServletRequest request = request("file", "document.docx");
    request.addFile(
        new MockMultipartFile("extra", "extra.docx", "application/octet-stream", new byte[] {1}));

    ConversionException exception =
        assertThrows(ConversionException.class, () -> controller.toHtml(request));

    assertEquals(ConversionError.INPUT_INVALID, exception.error());
  }

  private MockMultipartHttpServletRequest request(String partName, String filename) {
    var request = new MockMultipartHttpServletRequest();
    request.addFile(
        new MockMultipartFile(
            partName, filename, "application/octet-stream", new byte[] {1, 2, 3}));
    return request;
  }
}
