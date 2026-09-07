package com.researchspace.documentconversion.ext;

import com.researchspace.core.util.IoUtils;
import com.researchspace.documentconversion.spi.AbstractDocumentConversionService;
import com.researchspace.documentconversion.spi.ConversionResult;
import com.researchspace.documentconversion.spi.Convertible;
import com.researchspace.documentconversion.spi.DocumentConversionService;
import com.researchspace.documentconversion.validation.SafeOfficeArchiveValidator;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;

/** Converts Word import and export formats through the sidecar's JODConverter worker. */
public final class JodConverterClient extends AbstractDocumentConversionService
    implements DocumentConversionService {

  private static final Logger LOG = LoggerFactory.getLogger(JodConverterClient.class);

  private static final Set<String> WORD_INPUTS = Set.of("doc", "docx", "odt", "ott", "rtf", "txt");
  private static final MediaType DOCX =
      MediaType.parseMediaType(
          "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
  private final ConversionSidecarHttpClient client;
  private final long maxHtmlBytes;

  public JodConverterClient(ConversionSidecarHttpClient client, long maxHtmlBytes) {
    this.client = client;
    this.maxHtmlBytes = maxHtmlBytes;
  }

  @Override
  public ConversionResult convert(Convertible convertible, String outputExtension) {
    File output = null;
    try {
      String suffix = "." + outputExtension.toLowerCase(Locale.ROOT);
      output =
          File.createTempFile(
              "converted-word-", suffix, IoUtils.createOrGetSecureTempDirectory().toFile());
      ConversionResult result = convert(convertible, outputExtension, output);
      if (!result.isSuccessful()) {
        FileUtils.deleteQuietly(output);
      }
      return result;
    } catch (IOException e) {
      LOG.error("Could not create Word conversion output", e);
      FileUtils.deleteQuietly(output);
      return new ConversionResult(DocumentConversionError.OUTPUT_CREATE_FAILED.code());
    }
  }

  @Override
  public ConversionResult convert(Convertible convertible, String outputExtension, File output) {
    if (!supportsConversion(convertible, outputExtension)) {
      return new ConversionResult(DocumentConversionError.UNSUPPORTED.code());
    }
    File input;
    try {
      input = new File(URI.create(convertible.getFileUri()));
      String inputExtension =
          FilenameUtils.getExtension(convertible.getName()).toLowerCase(Locale.ROOT);
      if (Set.of("docx", "odt", "ott").contains(inputExtension)) {
        SafeOfficeArchiveValidator.validateInput(input.toPath(), inputExtension);
      }
    } catch (Exception e) {
      LOG.warn("Word conversion input validation failed", e);
      deleteEmptyOutput(output);
      return new ConversionResult(DocumentConversionError.INPUT_INVALID.code());
    }
    boolean toHtml = "html".equalsIgnoreCase(outputExtension);
    return client.postFile(
        toHtml ? "/v1/convert/html" : "/v1/convert/docx",
        "file",
        input,
        output,
        toHtml ? MediaType.TEXT_HTML : DOCX,
        toHtml
            ? path -> validateHtml(path, maxHtmlBytes)
            : SafeOfficeArchiveValidator::validateDocx);
  }

  private static void deleteEmptyOutput(File output) {
    if (output != null && output.length() == 0) {
      FileUtils.deleteQuietly(output);
    }
  }

  @Override
  public boolean supportsConversion(Convertible convertible, String outputExtension) {
    String input = FilenameUtils.getExtension(convertible.getName()).toLowerCase(Locale.ROOT);
    String output = outputExtension.toLowerCase(Locale.ROOT);
    return (WORD_INPUTS.contains(input) && "html".equals(output))
        || (("html".equals(input) || "htm".equals(input)) && "docx".equals(output));
  }

  private static void validateHtml(Path output, long maxHtmlBytes) throws IOException {
    if (Files.size(output) > maxHtmlBytes) {
      throw new IOException(DocumentConversionError.OUTPUT_TOO_LARGE.code());
    }
    String prefix = Files.readString(output, StandardCharsets.UTF_8);
    if (!prefix.toLowerCase(Locale.ROOT).contains("<html")) {
      throw new IOException(DocumentConversionError.OUTPUT_INVALID.code());
    }
  }
}
