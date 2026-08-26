package com.researchspace.service.impl;

import com.researchspace.core.util.IoUtils;
import com.researchspace.core.util.imageutils.ImageUtils;
import com.researchspace.documentconversion.ext.DocumentConversionError;
import com.researchspace.documentconversion.ext.SafePdfValidator;
import com.researchspace.documentconversion.spi.ConversionResult;
import com.researchspace.documentconversion.spi.Convertible;
import com.researchspace.documentconversion.spi.DocumentConversionService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Specifically converts PDF files to thumbnail images */
public class PDFToImageConverter implements DocumentConversionService {

  private static final Logger LOG = LoggerFactory.getLogger(PDFToImageConverter.class);

  @Override
  public ConversionResult convert(Convertible toConvert, String outputExtension, File outfile) {
    File pdfFile;
    try {
      pdfFile = new File(new URI(toConvert.getFileUri()));
    } catch (URISyntaxException e2) {
      LOG.warn("PDF thumbnail input URI is invalid", e2);
      return new ConversionResult(DocumentConversionError.INPUT_INVALID.code());
    }

    try (PDDocument document = Loader.loadPDF(pdfFile)) {
      SafePdfValidator.validate(document);
      PDFRenderer pdfRenderer = new PDFRenderer(document);
      PDPageTree pages = document.getPages();
      if (pages.getCount() == 0) {
        return new ConversionResult(DocumentConversionError.OUTPUT_INVALID.code());
      }
      BufferedImage image = pdfRenderer.renderImageWithDPI(0, 72, ImageType.RGB);
      return writeThumbnail(image, outfile);
    } catch (IOException e1) {
      LOG.warn("PDF thumbnail input failed validation or rendering", e1);
      return new ConversionResult(DocumentConversionError.OUTPUT_INVALID.code());
    }
  }

  private ConversionResult writeThumbnail(BufferedImage image, File outfile) {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream(1000)) {
      ImageUtils.createThumbnail(
          image,
          ImageUtils.DEFAULT_THUMBNAIL_DIMNSN,
          ImageUtils.DEFAULT_THUMBNAIL_DIMNSN,
          baos,
          "png");

      FileUtils.writeByteArrayToFile(outfile, baos.toByteArray());
      return new ConversionResult(outfile, "image/png");
    } catch (IOException e) {
      LOG.error("Could not write PDF thumbnail output", e);
      return new ConversionResult(DocumentConversionError.FAILED.code());
    }
  }

  @Override
  public ConversionResult convert(Convertible toConvert, String outputExtension) {
    try {
      File tmpDir = IoUtils.createOrGetSecureTempDirectory().toFile();
      File outfile = File.createTempFile("pdfThumbnail", ".png", tmpDir);
      return convert(toConvert, outputExtension, outfile);
    } catch (IOException e) {
      LOG.error("Could not create PDF thumbnail output", e);
      return new ConversionResult(DocumentConversionError.OUTPUT_CREATE_FAILED.code());
    }
  }

  @Override
  public boolean supportsConversion(Convertible toConvert, String to) {
    return "pdf".equals(FilenameUtils.getExtension(toConvert.getName())) && "png".equals(to);
  }
}
