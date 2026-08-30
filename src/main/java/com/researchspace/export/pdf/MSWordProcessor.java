package com.researchspace.export.pdf;

import static org.apache.commons.io.FilenameUtils.getBaseName;

import com.researchspace.documentconversion.ext.DocumentConversionError;
import com.researchspace.documentconversion.ext.DocumentConversionException;
import com.researchspace.documentconversion.spi.ConversionResult;
import com.researchspace.documentconversion.spi.Convertible;
import com.researchspace.documentconversion.spi.ConvertibleFile;
import com.researchspace.documentconversion.spi.DocumentConversionService;
import com.researchspace.export.stoichiometry.StoichiometryHtmlGenerator;
import com.researchspace.files.service.FileStore;
import com.researchspace.model.FileProperty;
import com.researchspace.model.core.IRSpaceDoc;
import com.researchspace.service.UserLocaleService;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.apache.tika.Tika;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.spring.VelocityEngineUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class MSWordProcessor extends AbstractExportProcessor implements ExportProcessor {

  private static final int MAX_INLINE_IMAGES = 100;
  private static final long MAX_INLINE_IMAGE_BYTES = 10_000_000;
  private static final long MAX_TOTAL_INLINE_IMAGE_BYTES = 50_000_000;
  private static final Set<String> INLINE_IMAGE_TYPES =
      Set.of("image/png", "image/jpeg", "image/gif");

  Logger log = LoggerFactory.getLogger(MSWordProcessor.class);

  @Autowired
  @Qualifier("compositeDocumentConverter")
  private DocumentConversionService docConverter;

  private @Autowired ImageRetrieverHelper imageHelper;
  @Autowired private StoichiometryHtmlGenerator stoichiometryHtmlGenerator;
  @Autowired private VelocityEngine velocityEngine;
  @Autowired private PdfHtmlGenerator pdfHtmlGenerator;
  @Autowired private UserLocaleService userLocaleService;

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
      ExportProcessorInput exportInput,
      IRSpaceDoc strucDoc,
      ExportToFileConfig exportConfig)
      throws IOException {
    if (!supportsFormat(exportConfig.getExportFormat())) {
      throw new IllegalArgumentException(
          String.format(
              "This method supports %s export, not %s export",
              ExportFormat.WORD, exportConfig.getExportFormat()));
    }
    File htmlInput = inlineImages(tempExportFile, exportInput, exportConfig, strucDoc);
    Convertible toconvert = new ConvertibleFile(htmlInput);
    ConversionResult result = docConverter.convert(toconvert, "docx", tempExportFile);
    if (!result.isSuccessful()) {
      log.error("Couldn't convert {} to DOCX format", toconvert);
      throw new DocumentConversionException(
          DocumentConversionError.fromCode(result.getErrorMsg())
              .orElse(DocumentConversionError.FAILED));
    }
  }

  private File inlineImages(
      File tempExportFile,
      ExportProcessorInput exportInput,
      ExportToFileConfig exportConfig,
      IRSpaceDoc strucDoc)
      throws IOException {
    String html = exportInput.getDocumentAsHtml();
    Document jsoup = Jsoup.parse(html);
    Elements images = jsoup.getElementsByTag("img");
    inlineImageSources(exportConfig, images);

    html = jsoup.html();
    if (html.contains("data-stoichiometry-table")) {
      html = stoichiometryHtmlGenerator.addStoichiometryLinks(html, exportConfig.getExporter());
    }
    if (exportInput.hasExternalWorkflowData()) {
      html = prepareExternalWorkflowTablesForWordExport(html);
    }
    String pageSize = exportConfig.getPageSize().equals("A4") ? "A4" : "LETTER";
    String footerFormattedDate =
        pdfHtmlGenerator.formatFooterDate(
            strucDoc, exportConfig, userLocaleService.getLocaleFor(exportConfig.getExporter()));
    html =
        addStyleElement(
            html,
            makeHtmlStyleElement(
                pageSize, !exportConfig.isIncludeFooterAtEndOnly(), footerFormattedDate));
    File htmlInput =
        new File(tempExportFile.getParentFile(), getBaseName(tempExportFile.getName()) + ".html");
    FileUtils.write(htmlInput, html, "UTF-8");
    return htmlInput;
  }

  private String prepareExternalWorkflowTablesForWordExport(String html) {
    Document document = stringToJsoupDoc(html);
    Elements externalWorkflowTables = document.select("table.external-workflow-table");
    for (Element table : externalWorkflowTables) {
      table.attr("border", "1");
      table.attr("cellpadding", "4");
      table.attr("cellspacing", "0");
    }
    return document.toString();
  }

  private String makeHtmlStyleElement(String pageSize, boolean footerEachPage, String footerDate) {
    Map<String, Object> context = new HashMap<>();
    context.put("pageSize", pageSize);
    return VelocityEngineUtils.mergeTemplateIntoString(
        velocityEngine, "doc/styles.vm", "UTF-8", context);
  }

  private String addStyleElement(String html, String styles) {
    Document document = stringToJsoupDoc(html);
    Element head = document.head();
    head.append(styles);
    return document.toString();
  }

  private Document stringToJsoupDoc(String html) {
    Document doc = Jsoup.parse(html);
    doc.outputSettings()
        .syntax(org.jsoup.nodes.Document.OutputSettings.Syntax.xml)
        .escapeMode(Entities.EscapeMode.xhtml)
        .charset("UTF-8");
    return doc;
  }

  private void inlineImageSources(ExportToFileConfig exportConfig, Elements images)
      throws IOException {
    if (images.size() > MAX_INLINE_IMAGES) {
      throw new IOException("Word export contains too many images");
    }
    long totalBytes = 0;
    Tika tika = new Tika();
    for (int i = 0; i < images.size(); i++) {
      Element img = images.get(i);
      String source = img.attr("src");
      validateInternalImageSource(source);
      byte[] imgData = imageHelper.getImageBytesFromImgSrc(source, exportConfig);
      if (imgData == null || imgData.length == 0 || imgData.length > MAX_INLINE_IMAGE_BYTES) {
        throw new IOException("Word export image exceeds the permitted size");
      }
      totalBytes += imgData.length;
      if (totalBytes > MAX_TOTAL_INLINE_IMAGE_BYTES) {
        throw new IOException("Word export images exceed the permitted total size");
      }
      String mediaType = tika.detect(imgData).toLowerCase(Locale.ROOT);
      if (!INLINE_IMAGE_TYPES.contains(mediaType)) {
        throw new IOException("Word export image has an unsupported media type");
      }
      img.attr(
          "src", "data:" + mediaType + ";base64," + Base64.getEncoder().encodeToString(imgData));
    }
  }

  private void validateInternalImageSource(String source) throws IOException {
    if (source == null
        || source.isBlank()
        || source.startsWith("//")
        || source.indexOf('\\') >= 0) {
      throw new IOException("Word export contains an invalid image source");
    }
    try {
      URI uri = URI.create(source);
      String path = uri.getPath();
      if (uri.isAbsolute()
          || uri.getAuthority() != null
          || path == null
          || java.nio.file.Path.of(path).normalize().startsWith("..")) {
        throw new IOException("Word export contains an external image source");
      }
    } catch (IllegalArgumentException e) {
      throw new IOException("Word export contains an invalid image source", e);
    }
  }
}
