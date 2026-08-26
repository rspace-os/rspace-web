package com.researchspace.conversion;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ConversionController.class)
class ConversionControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private ArchiveValidator archiveValidator;
  @MockitoBean private OfficeConversionRunner officeRunner;
  @MockitoBean private GotenbergProxy gotenbergProxy;
  @MockitoBean private OfficeConversionLimiter limiter;
  @TempDir Path directory;

  @Test
  void capabilitiesDeclareBothRequiredRoles() throws Exception {
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                "/v1/capabilities"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.protocol").value("rspace-conversion-sidecar"))
        .andExpect(jsonPath("$.roles[0]").value("pdf"))
        .andExpect(jsonPath("$.roles[1]").value("word"));
  }

  @Test
  void wordImportUsesMvcMultipartBindingAndStreamsOutput() throws Exception {
    when(officeRunner.convert(any(), eq("doc"), eq("html")))
        .thenReturn(output("output.html", "text/html; charset=UTF-8", "output"));

    mvc.perform(multipart("/v1/convert/html").file(file("file", "legacy.doc")))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "text/html;charset=UTF-8"));

    verify(archiveValidator, never()).validate(any(), any());
    verify(officeRunner).convert(any(), eq("doc"), eq("html"));
  }

  @Test
  void pdfRouteRejectsCallerGotenbergControls() throws Exception {
    mvc.perform(
            multipart("/forms/libreoffice/convert")
                .file(file("files", "document.docx"))
                .header("Gotenberg-Output-Filename", "chosen.pdf"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(ConversionError.INPUT_INVALID.code()));

    verify(gotenbergProxy, never()).convert(any(), any(), any());
  }

  @Test
  void pdfRouteValidatesPackageAndGeneratesCorrelationId() throws Exception {
    AtomicBoolean released = new AtomicBoolean();
    OfficeConversionLimiter.Permit permit =
        new OfficeConversionLimiter.Permit(() -> released.set(true));
    when(limiter.acquirePdf()).thenReturn(permit);
    when(gotenbergProxy.convert(any(), eq("docx"), any()))
        .thenReturn(output("output.pdf", MediaType.APPLICATION_PDF_VALUE, "%PDF-output"));

    mvc.perform(multipart("/forms/libreoffice/convert").file(file("files", "document.docx")))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE));

    verify(archiveValidator).validate(any(), eq("docx"));
    verify(gotenbergProxy)
        .convert(any(), eq("docx"), org.mockito.ArgumentMatchers.argThat(this::isUuid));
    assertTrue(released.get());
  }

  @Test
  void conversionRoutesRejectExtraPartsThroughExceptionAdvice() throws Exception {
    mvc.perform(
            multipart("/v1/convert/html")
                .file(file("file", "document.docx"))
                .file(file("extra", "extra.docx")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(ConversionError.INPUT_INVALID.code()));
  }

  private MockMultipartFile file(String partName, String filename) {
    return new MockMultipartFile(
        partName, filename, MediaType.APPLICATION_OCTET_STREAM_VALUE, new byte[] {1, 2, 3});
  }

  private ConvertedFile output(String filename, String contentType, String body) throws Exception {
    Path requestDirectory = Files.createTempDirectory(directory, "result-");
    Path output = requestDirectory.resolve(filename);
    Files.writeString(output, body);
    return new ConvertedFile(requestDirectory, output, contentType);
  }

  private boolean isUuid(String value) {
    try {
      java.util.UUID.fromString(value);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
