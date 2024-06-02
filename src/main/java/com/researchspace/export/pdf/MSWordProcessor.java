package com.researchspace.export.pdf;

import static org.apache.commons.io.FilenameUtils.getBaseName;

import com.researchspace.documentconversion.spi.ConversionResult;
import com.researchspace.documentconversion.spi.Convertible;
import com.researchspace.documentconversion.spi.ConvertibleFile;
import com.researchspace.documentconversion.spi.DocumentConversionService;
import com.researchspace.files.service.FileStore;
import com.researchspace.model.FileProperty;
import com.researchspace.model.core.IRSpaceDoc;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class MSWordProcessor extends AbstractExportProcessor implements ExportProcessor {

  Logger log = LoggerFactory.getLogger(MSWordProcessor.class);

  @Autowired
  @Qualifier("compositeDocumentConverter")
  private DocumentConversionService docConverter;

  private @Autowired ImageRetrieverHelper imageHelper;

  public void setDocConverter(DocumentConversionService docConverter) {
    this.docConverter = docConverter;
  }

  public void setImageHelper(ImageRetrieverHelper imageHelper) {
    this.imageHelper = imageHelper;
  }

  @Override
  public FileProperty concatenateExportedFilesIntoOne(
      File finalExportFile,
      List<File> tmpExportedFiles,
      FileStore fileStore,
      FileProperty fp,
      ExportToFileConfig config)
      throws IOException {
    if (!tmpExportedFiles.isEmpty()) {
      FileUtils.copyFile(tmpExportedFiles.get(0), finalExportFile);
      return saveToFileStore(finalExportFile, tmpExportedFiles, fileStore, fp);
    } else {
      log.warn("No export files to concatenate!! - returning unsaved file property {}", fp);
      return fp;
    }
  }

  @Override
  public void concatenateExportedFilesIntoOne(
      File finalExportFile, List<File> tmpExportedFiles, ExportToFileConfig config)
      throws IOException {
    if (!tmpExportedFiles.isEmpty()) {
      FileUtils.copyFile(tmpExportedFiles.get(0), finalExportFile);
    } else {
      log.warn("No export files to concatenate!! ");
    }
  }

  @Override
  public boolean supportsFormat(ExportFormat exportFormat) {
    return ExportFormat.WORD.equals(exportFormat);
  }

  @Override
  public void makeExport(
      File tempExportFile,
      ExportProcesserInput exportInput,
      IRSpaceDoc strucDoc,
      ExportToFileConfig exportConfig)
      throws IOException {
    if (!supportsFormat(exportConfig.getExportFormat())) {
      throw new IllegalArgumentException(
          String.format(
              "This method supports %s export, not %s export",
              ExportFormat.WORD, exportConfig.getExportFormat()));
    }
    // this puts html and images in the same folder.
    File htmlInput = extractImagesAndReplaceSrcLinks(tempExportFile, exportInput, exportConfig);
    Convertible toconvert = new ConvertibleFile(htmlInput);
    ConversionResult result = docConverter.convert(toconvert, "doc", tempExportFile);
    if (!result.isSuccessful()) {
      log.error("Couldn't convert {} to doc format", toconvert);
    }
  }

  private File extractImagesAndReplaceSrcLinks(
      File tempExportFile, ExportProcesserInput exportInput, ExportToFileConfig exportConfig)
      throws IOException, FileNotFoundException {
    String html = exportInput.getDocumentAsHtml();
    Document jsoup = Jsoup.parse(html);
    Elements images = jsoup.getElementsByTag("img");
    extractImageFileAndUpdateLinkFromImages(tempExportFile, exportConfig, images);

    html = jsoup.html();
    File htmlInput =
        new File(tempExportFile.getParentFile(), getBaseName(tempExportFile.getName()) + ".html");
    FileUtils.write(htmlInput, html, "UTF-8");
    return htmlInput;
  }

  private void extractImageFileAndUpdateLinkFromImages(
      File tempExportFile, ExportToFileConfig exportConfig, Elements images)
      throws IOException, FileNotFoundException {
    for (int i = 0; i < images.size(); i++) {
      Element img = images.get(i);
      byte[] imgData = imageHelper.getImageBytesFromImgSrc(img.attr("src"), exportConfig);
      String imageName = RandomStringUtils.randomAlphabetic(10) + ".png";
      File outfile = new File(tempExportFile.getParentFile(), imageName);
      img.attr("src", imageName); // replace image name
      try (FileOutputStream fos = new FileOutputStream(outfile)) {
        IOUtils.write(imgData, fos);
      }
    }
  }
}
