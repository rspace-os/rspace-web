package com.researchspace.documentconversion.ext;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.researchspace.documentconversion.spi.ConvertibleFile;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PdfConversionClientTest {

  @TempDir Path directory;

  @Test
  void rejectsInputAboveConfiguredLimitBeforeSendingIt() throws Exception {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://converter.test");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    var client =
        new PdfConversionClient(
            new ConversionSidecarHttpClient(builder.build(), 1, 10L * 1024 * 1024));
    File input = new File("src/test/resources/TestResources/PowerPasteTesting_RSpace.docx");
    File output = Files.createTempFile("converted-", ".pdf").toFile();

    var result = client.convert(new ConvertibleFile(input), "pdf", output);

    assertFalse(result.isSuccessful());
    assertFalse(output.exists());
    server.verify();
  }

  @Test
  void supportsEveryGalleryPreviewFormat() {
    var client =
        new PdfConversionClient(
            new ConversionSidecarHttpClient(
                RestClient.builder().baseUrl("http://converter.test").build(), 1024));

    for (String extension :
        new String[] {
          "csv", "doc", "docx", "md", "odt", "rtf", "txt", "xls", "xlsx", "ods", "pdf", "ppt",
          "pptx", "odp"
        }) {
      assertTrue(
          client.supportsConversion(new ConvertibleFile(new File("document." + extension)), "pdf"),
          extension);
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "PowerPasteTesting_RSpace.docx",
        "simpleExcel.xlsx",
        "ODS.ods",
        "ODP.pptx",
        "ODP.odp"
      })
  void convertsOfficeDocumentThroughFixedPdfRoute(String fixture) throws Exception {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://converter.test");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    byte[] pdf =
        Files.readAllBytes(
            new File("src/test/resources/TestResources/smartscotland3.pdf").toPath());
    server
        .expect(requestTo("http://converter.test/forms/libreoffice/convert"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess(pdf, MediaType.APPLICATION_PDF));
    var client =
        new PdfConversionClient(
            new ConversionSidecarHttpClient(builder.build(), 10L * 1024 * 1024));
    File input = new File("src/test/resources/TestResources/" + fixture);
    File output = Files.createTempFile("converted-", ".pdf").toFile();

    var result = client.convert(new ConvertibleFile(input), "pdf", output);

    assertTrue(result.isSuccessful());
    assertArrayEquals(pdf, Files.readAllBytes(output.toPath()));
    server.verify();
  }

  @ParameterizedTest
  @CsvSource({"docx,pdf", "docx,html", "odt,pdf", "xlsx,pdf", "pptx,pdf", "ods,pdf", "odp,pdf"})
  void rejectsTooManyArchiveEntriesWithSizeError(String inputExtension, String outputExtension)
      throws Exception {
    Path input = directory.resolve("many." + inputExtension);
    try (var zip = new ZipOutputStream(Files.newOutputStream(input))) {
      for (int i = 0; i < 10_001; i++) {
        zip.putNextEntry(new ZipEntry("entry" + i));
        zip.closeEntry();
      }
    }
    var sidecar = mock(ConversionSidecarHttpClient.class);
    var adapter =
        "pdf".equals(outputExtension)
            ? new PdfConversionClient(sidecar)
            : new JodConverterClient(sidecar, 1024);
    File output = directory.resolve("converted." + outputExtension).toFile();

    var result = adapter.convert(new ConvertibleFile(input.toFile()), outputExtension, output);

    verifyNoInteractions(sidecar);
    assertEquals(DocumentConversionError.INPUT_TOO_LARGE.code(), result.getErrorMsg());
    assertFalse(output.exists());
  }
}
