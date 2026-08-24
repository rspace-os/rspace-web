package com.researchspace.documentconversion.ext;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.researchspace.documentconversion.spi.ConvertibleFile;
import java.io.File;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PdfConversionClientTest {

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

  @Test
  void convertsOfficeDocumentThroughFixedPdfRoute() throws Exception {
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
    File input = new File("src/test/resources/TestResources/PowerPasteTesting_RSpace.docx");
    File output = Files.createTempFile("converted-", ".pdf").toFile();

    var result = client.convert(new ConvertibleFile(input), "pdf", output);

    assertTrue(result.isSuccessful());
    assertArrayEquals(pdf, Files.readAllBytes(output.toPath()));
    server.verify();
  }
}
