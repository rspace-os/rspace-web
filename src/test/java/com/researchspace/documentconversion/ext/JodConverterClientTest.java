package com.researchspace.documentconversion.ext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.researchspace.documentconversion.spi.ConvertibleFile;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class JodConverterClientTest {

  @Test
  void rejectsHtmlAboveConfiguredLimit() throws Exception {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://converter.test");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("http://converter.test/v1/convert/html"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess("<html><body>too long</body></html>", MediaType.TEXT_HTML));
    var client =
        new JodConverterClient(
            new ConversionSidecarHttpClient(builder.build(), 10L * 1024 * 1024), 10);
    File input = new File("src/test/resources/TestResources/PowerPasteTesting_RSpace.docx");
    File output = Files.createTempFile("converted-", ".html").toFile();

    var result = client.convert(new ConvertibleFile(input), "html", output);

    assertFalse(result.isSuccessful());
    assertFalse(output.exists());
    server.verify();
  }

  @Test
  void supportsEveryExistingWordImportFormat() {
    var client =
        new JodConverterClient(
            new ConversionSidecarHttpClient(
                RestClient.builder().baseUrl("http://converter.test").build(), 1024),
            50L * 1024 * 1024);

    for (String extension : new String[] {"doc", "docx", "odt", "rtf", "txt"}) {
      assertTrue(
          client.supportsConversion(new ConvertibleFile(new File("document." + extension)), "html"),
          extension);
    }
  }

  @Test
  void importsDocxThroughFixedHtmlRoute() throws Exception {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://converter.test");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("http://converter.test/v1/convert/html"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess("<html><body>converted</body></html>", MediaType.TEXT_HTML));
    var client =
        new JodConverterClient(
            new ConversionSidecarHttpClient(builder.build(), 10L * 1024 * 1024), 50L * 1024 * 1024);
    File input = new File("src/test/resources/TestResources/PowerPasteTesting_RSpace.docx");
    File output = Files.createTempFile("converted-", ".html").toFile();

    var result = client.convert(new ConvertibleFile(input), "html", output);

    assertTrue(result.isSuccessful());
    assertTrue(Files.readString(output.toPath(), StandardCharsets.UTF_8).contains("converted"));
    server.verify();
  }
}
