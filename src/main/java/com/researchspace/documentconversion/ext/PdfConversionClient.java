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
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;

/** Converts supported Office documents to PDF through the conversion sidecar. */
public final class PdfConversionClient extends AbstractDocumentConversionService
    implements DocumentConversionService {

  private static final Logger LOG = LoggerFactory.getLogger(PdfConversionClient.class);

  private static final Set<String> INPUTS =
      Set.of(
          "csv", "doc", "docx", "md", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "rtf",
          "txt");
  private static final MediaType PDF = MediaType.APPLICATION_PDF;
  private final ConversionSidecarHttpClient client;

  public PdfConversionClient(ConversionSidecarHttpClient client) {
    this.client = client;
  }

  @Override
  public ConversionResult convert(Convertible convertible, String outputExtension) {
    File output = null;
    try {
      output =
          File.createTempFile(
              "converted-office-", ".pdf", IoUtils.createOrGetSecureTempDirectory().toFile());
      ConversionResult result = convert(convertible, outputExtension, output);
      if (!result.isSuccessful()) {
        FileUtils.deleteQuietly(output);
      }
      return result;
    } catch (IOException e) {
      LOG.error("Could not create PDF conversion output", e);
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
      String inputExtension = extension(convertible);
      if ("pdf".equals(inputExtension)) {
        Files.copy(input.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING);
        SafePdfValidator.validate(output.toPath());
        return new ConversionResult(output, PDF.toString());
      }
      if (Set.of("docx", "odt").contains(inputExtension)) {
        SafeOfficeArchiveValidator.validateInput(input.toPath(), inputExtension);
      }
    } catch (Exception e) {
      LOG.warn("PDF conversion input validation failed", e);
      FileUtils.deleteQuietly(output);
      return new ConversionResult(DocumentConversionError.INPUT_INVALID.code());
    }
    return client.postFile(
        "/forms/libreoffice/convert", "files", input, output, PDF, SafePdfValidator::validate);
  }

  @Override
  public boolean supportsConversion(Convertible convertible, String outputExtension) {
    String input = extension(convertible);
    return "pdf".equalsIgnoreCase(outputExtension)
        && ("pdf".equals(input) || INPUTS.contains(input));
  }

  private static String extension(Convertible convertible) {
    return FilenameUtils.getExtension(convertible.getName()).toLowerCase(Locale.ROOT);
  }
}
