package com.researchspace.service.impl;

import com.researchspace.core.util.IoUtils;
import com.researchspace.documentconversion.ext.DocumentConversionError;
import com.researchspace.documentconversion.spi.ConversionResult;
import com.researchspace.documentconversion.spi.Convertible;
import com.researchspace.documentconversion.spi.ConvertibleFile;
import com.researchspace.documentconversion.spi.DocumentConversionService;
import java.io.File;
import java.io.IOException;

/** Produces Office-document thumbnails through an intermediate validated PDF. */
public final class PdfThenPngConverter implements DocumentConversionService {

  private final DocumentConversionService pdfConverter;
  private final DocumentConversionService pdfToImageConverter;

  public PdfThenPngConverter(
      DocumentConversionService pdfConverter, DocumentConversionService pdfToImageConverter) {
    this.pdfConverter = pdfConverter;
    this.pdfToImageConverter = pdfToImageConverter;
  }

  @Override
  public ConversionResult convert(Convertible convertible, String outputExtension) {
    try {
      File output =
          File.createTempFile(
              "converted-thumbnail-", ".png", IoUtils.createOrGetSecureTempDirectory().toFile());
      return convert(convertible, outputExtension, output);
    } catch (IOException e) {
      return new ConversionResult(DocumentConversionError.OUTPUT_CREATE_FAILED.code());
    }
  }

  @Override
  public ConversionResult convert(Convertible convertible, String outputExtension, File output) {
    File intermediate = null;
    try {
      intermediate =
          File.createTempFile(
              "converted-thumbnail-source-",
              ".pdf",
              IoUtils.createOrGetSecureTempDirectory().toFile());
      ConversionResult pdf = pdfConverter.convert(convertible, "pdf", intermediate);
      if (!pdf.isSuccessful()) {
        return pdf;
      }
      return pdfToImageConverter.convert(new ConvertibleFile(intermediate), "png", output);
    } catch (IOException e) {
      return new ConversionResult(DocumentConversionError.OUTPUT_CREATE_FAILED.code());
    } finally {
      if (intermediate != null) {
        intermediate.delete();
      }
    }
  }

  @Override
  public boolean supportsConversion(Convertible convertible, String outputExtension) {
    return "png".equalsIgnoreCase(outputExtension)
        && pdfConverter.supportsConversion(convertible, "pdf");
  }
}
