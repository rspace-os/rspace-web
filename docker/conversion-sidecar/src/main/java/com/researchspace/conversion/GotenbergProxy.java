package com.researchspace.conversion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

@Component
class GotenbergProxy {

  private final ConverterProperties properties;
  private final RestClient client;

  GotenbergProxy(ConverterProperties properties) {
    this.properties = properties;
    var requestFactory = new JdkClientHttpRequestFactory();
    requestFactory.setReadTimeout(properties.conversionTimeout().plusSeconds(5));
    this.client =
        RestClient.builder()
            .baseUrl("http://gotenberg:3000")
            .requestFactory(requestFactory)
            .defaultHeader(HttpHeaders.ACCEPT_ENCODING, "identity")
            .build();
  }

  ConvertedFile convert(Path uploadedFile, String extension, String correlationId) {
    Path directory = null;
    boolean completed = false;
    try {
      Files.createDirectories(properties.workingDirectory());
      directory = Files.createTempDirectory(properties.workingDirectory(), "pdf-");
      // Gotenberg does not list Markdown as an Office input. LibreOffice can still provide the
      // existing readable preview when the same UTF-8 content is handled by its text filter.
      String converterExtension = "md".equals(extension) ? "txt" : extension;
      Path input = directory.resolve("input." + converterExtension);
      Path output = directory.resolve("output.pdf");
      Files.move(uploadedFile, input, StandardCopyOption.REPLACE_EXISTING);
      var body = new LinkedMultiValueMap<String, Object>();
      body.add("files", new FileSystemResource(input));
      client
          .post()
          .uri("/forms/libreoffice/convert")
          .contentType(MediaType.MULTIPART_FORM_DATA)
          .header("Gotenberg-Trace", correlationId)
          .body(body)
          .exchange(
              (request, response) -> {
                if (response.getStatusCode() != HttpStatus.OK) {
                  throw new ConversionException(
                      HttpStatus.BAD_GATEWAY,
                      ConversionError.SERVICE_UNAVAILABLE,
                      "The PDF converter rejected the request");
                }
                if (!MediaType.APPLICATION_PDF.isCompatibleWith(
                    response.getHeaders().getContentType())) {
                  throw new ConversionException(
                      HttpStatus.BAD_GATEWAY,
                      ConversionError.OUTPUT_INVALID,
                      "The PDF converter returned an unexpected content type");
                }
                try (var inputStream = response.getBody()) {
                  copyBounded(inputStream, output);
                }
                return null;
              });
      if (!Files.isRegularFile(output)
          || Files.size(output) < 5
          || Files.size(output) > properties.maxOutputBytes()
          || !hasPdfSignature(output)) {
        throw new ConversionException(
            HttpStatus.BAD_GATEWAY,
            ConversionError.OUTPUT_INVALID,
            "The PDF converter returned an invalid PDF");
      }
      completed = true;
      return new ConvertedFile(directory, output, MediaType.APPLICATION_PDF_VALUE);
    } catch (ConversionException e) {
      throw e;
    } catch (Exception e) {
      throw new ConversionException(
          HttpStatus.BAD_GATEWAY,
          ConversionError.SERVICE_UNAVAILABLE,
          "The PDF converter is unavailable",
          e);
    } finally {
      if (!completed && directory != null) {
        deleteQuietly(directory);
      }
    }
  }

  private void copyBounded(java.io.InputStream input, Path output) throws IOException {
    try (var target = Files.newOutputStream(output)) {
      byte[] buffer = new byte[8192];
      long total = 0;
      int read;
      while ((read = input.read(buffer)) != -1) {
        total += read;
        if (total > properties.maxOutputBytes()) {
          throw new ConversionException(
              HttpStatus.PAYLOAD_TOO_LARGE,
              ConversionError.OUTPUT_TOO_LARGE,
              "The PDF output exceeds the configured limit");
        }
        target.write(buffer, 0, read);
      }
    }
  }

  private void deleteQuietly(Path directory) {
    try {
      new ConvertedFile(directory, directory, "").close();
    } catch (IOException ignored) {
      // Cleanup is best effort after the primary conversion failure.
    }
  }

  private boolean hasPdfSignature(Path output) throws IOException {
    try (var input = Files.newInputStream(output)) {
      return new String(input.readNBytes(5), java.nio.charset.StandardCharsets.US_ASCII)
          .equals("%PDF-");
    }
  }
}
