package com.researchspace.service.impl;

import com.researchspace.core.util.IoUtils;
import com.researchspace.core.util.imageutils.ImageUtils;
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
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

/** Specifically converts PDF files to thumbnail images */
public class PDFToImageConverter implements DocumentConversionService {

  @Override
  public ConversionResult convert(Convertible toConvert, String outputExtension, File outfile) {
    File pdfFile;
    try {
      pdfFile = new File(new URI(toConvert.getFileUri()));
    } catch (URISyntaxException e2) {
      return new ConversionResult("Couldn't read file from URI: " + e2.getMessage());
    }

    PDDocument document2;
    try {
      document2 = PDDocument.load(pdfFile);
    } catch (IOException e1) {
      return new ConversionResult("Couldn't load pdf file: " + e1.getMessage());
    }
    PDFRenderer pdfRenderer = new PDFRenderer(document2);

    PDPageTree pages2 = document2.getPages();
    if (pages2.getCount() == 0) {
      return null;
    }
    BufferedImage image;
    try {
      image = pdfRenderer.renderImageWithDPI(0, 72, ImageType.RGB);
      document2.close();
    } catch (IOException e1) {
      return new ConversionResult("Couldn't convert pdf to image: " + e1.getMessage());
    }
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
      return new ConversionResult("Couldn't write thumbnail file: " + e.getMessage());
    }
  }

  @Override
  public ConversionResult convert(Convertible toConvert, String outputExtension) {
    try {
      File tmpDir = IoUtils.createOrGetSecureTempDirectory().toFile();
      File outfile = File.createTempFile("pdfThumbnail", ".png", tmpDir);
      return convert(toConvert, outputExtension, outfile);
    } catch (IOException e) {
      return new ConversionResult("Couldn't generate outfile " + e.getMessage());
    }
  }

  @Override
  public boolean supportsConversion(Convertible toConvert, String to) {
    return "pdf".equals(FilenameUtils.getExtension(toConvert.getName())) && "png".equals(to);
  }
}
