package com.researchspace.service.impl;

import static com.researchspace.core.util.MediaUtils.APPLICATION_MSWORD;
import static com.researchspace.core.util.MediaUtils.APPLICATION_PDF;
import static com.researchspace.core.util.MediaUtils.IMAGE_X_PNG;

import com.researchspace.core.util.IoUtils;
import com.researchspace.documentconversion.spi.ConversionResult;
import com.researchspace.documentconversion.spi.Convertible;
import com.researchspace.documentconversion.spi.DocumentConversionService;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

public class DummyConversionService implements DocumentConversionService {
  Logger logger = LoggerFactory.getLogger(DummyConversionService.class);

  @Value("${sample.pdf}")
  private String pdfPath;

  @Value("${sample.png}")
  private String imagePath;

  @Value("${sample.doc}")
  private String docPath;

  public void setDocPath(String docPath) {
    this.docPath = docPath;
  }

  @Override
  public ConversionResult convert(Convertible toConvert, String outputExtension) {
    return convert(toConvert, outputExtension, null);
  }

  @Override
  public ConversionResult convert(Convertible toConvert, String outputExtension, File outfile) {
    if (isToDocConversion(outputExtension)) {
      return returnExampleFile(pdfPath, "pdf", APPLICATION_PDF, outfile);
    } else if (isImageConversion(outputExtension)) {
      return returnExampleFile(imagePath, "png", IMAGE_X_PNG, outfile);
    } else if (isToWord(outputExtension)) {
      return returnExampleFile(docPath, "doc", APPLICATION_MSWORD, outfile);
    }
    throw new UnsupportedOperationException("invalid extension:" + outputExtension);
  }

  private boolean isToWord(String outputExtension) {
    return outputExtension.equalsIgnoreCase("doc");
  }

  private ConversionResult returnExampleFile(
      String path, String outputExtension, String mimeType, File outfile) {

    try (FileInputStream fis = new FileInputStream(path)) {
      if (outfile == null) {
        File secureTmpDir = IoUtils.createOrGetSecureTempDirectory().toFile();
        outfile =
            File.createTempFile(
                FilenameUtils.getBaseName(path), "." + outputExtension, secureTmpDir);
      }

      try (FileOutputStream fos = new FileOutputStream(outfile)) {
        IOUtils.copy(fis, fos);
      }
      return new ConversionResult(outfile, mimeType);
    } catch (IOException e) {
      logger.warn("Error creating temp file.", e);
      return new ConversionResult(e.getMessage());
    }
  }

  public void setPdfPath(String path) {
    this.pdfPath = path;
  }

  public void setImagePath(String imagePath) {
    this.imagePath = imagePath;
  }

  private boolean isImageConversion(String outputExtension) {
    return outputExtension.equalsIgnoreCase("png");
  }

  private boolean isToDocConversion(String outputExtension) {
    return outputExtension.equalsIgnoreCase("pdf") || outputExtension.equalsIgnoreCase("html");
  }

  @Override
  public boolean supportsConversion(Convertible from, String to) {
    String fromExtension = FilenameUtils.getExtension(from.getName());
    return (isImageConversion(to) || isToDocConversion(to) || isToWord(to))
        && (fromExtension.equalsIgnoreCase("doc")
            || fromExtension.equalsIgnoreCase("docx")
            || fromExtension.equalsIgnoreCase("odt")
            || fromExtension.equalsIgnoreCase("pdf")
            || fromExtension.equalsIgnoreCase("rtf")
            || fromExtension.equalsIgnoreCase("txt")
            || fromExtension.equalsIgnoreCase("html"));
  }
}
