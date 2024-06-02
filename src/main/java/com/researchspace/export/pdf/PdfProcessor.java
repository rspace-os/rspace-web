package com.researchspace.export.pdf;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.PageSize;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfCopy;
import com.lowagie.text.pdf.PdfReader;
import com.researchspace.files.service.FileStore;
import com.researchspace.model.FileProperty;
import com.researchspace.model.core.IRSpaceDoc;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.repository.spi.ExternalId;
import com.researchspace.repository.spi.IdentifierScheme;
import com.researchspace.service.UserExternalIdResolver;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.xhtmlrenderer.layout.SharedContext;
import org.xhtmlrenderer.pdf.ITextFontResolver;
import org.xhtmlrenderer.pdf.ITextRenderer;

/** Convert the HTML representation of a {@link StructuredDocument} to PDF for export. */
@Service
public class PdfProcessor extends AbstractExportProcessor implements ExportProcessor {

  private final UserExternalIdResolver externalIdResolver;
  private final HTMLUnicodeFontProcesser htmlUnicodeFontProcesser;
  private final HtmlImageResolver imageResolver;
  private final PdfHtmlGenerator pdfHtmlGenerator;

  @Autowired
  public PdfProcessor(
      UserExternalIdResolver idResolver,
      HTMLUnicodeFontProcesser fontProcessor,
      HtmlImageResolver imageResolver,
      PdfHtmlGenerator pdfHtmlGenerator) {
    this.externalIdResolver = idResolver;
    this.htmlUnicodeFontProcesser = fontProcessor;
    this.imageResolver = imageResolver;
    this.pdfHtmlGenerator = pdfHtmlGenerator;
  }

  /*
   * (non-Javadoc)
   *
   * @see
   * com.researchspace.export.pdf.ExportProcessor#makeExport(java.io.File,
   * java.util.List, com.researchspace.export.pdf.ExportAppendix,
   * com.researchspace.model.record.IRecord,
   * com.researchspace.export.pdf.ExportToFormatConfig, java.lang.String)
   */
  @Override
  public void makeExport(
      File tempExportFile,
      ExportProcesserInput documentData,
      IRSpaceDoc strucDoc,
      ExportToFileConfig config)
      throws IOException {

    int startPage;
    try {
      startPage = doExportPdf(tempExportFile, documentData, strucDoc, config);
    } catch (DocumentException e) {
      throw new IOException("Could not generate PDFWriter", e);
    }

    if (!config.isPageName()) {
      config.setStartPage(startPage);
    } else {
      config.setStartPage(0);
    }
  }

  private int doExportPdf(
      File tempExportFile,
      ExportProcesserInput documentData,
      IRSpaceDoc strucDoc,
      ExportToFileConfig config)
      throws DocumentException, IOException {

    log.info("Before: {}", documentData.getDocumentAsHtml());
    documentData = preProcessHTML(documentData);

    String html = pdfHtmlGenerator.prepareHtml(documentData, strucDoc, config);

    ITextRenderer renderer = new ITextRenderer();
    imageResolver.setReplacedElementFactory(
        renderer.getSharedContext().getReplacedElementFactory());
    imageResolver.setDotsPerPixel(renderer.getSharedContext().getDotsPerPixel());
    imageResolver.setExportConfig(config);
    renderer.getSharedContext().setReplacedElementFactory(imageResolver);
    registerAdditionalFonts(renderer.getFontResolver());
    SharedContext sharedContext = renderer.getSharedContext();
    sharedContext.setPrint(true);
    sharedContext.setInteractive(false);
    renderer.setDocumentFromString(html);
    renderer.layout();
    try (FileOutputStream out = new FileOutputStream(tempExportFile)) {
      renderer.createPDF(out);
    }
    return renderer.getWriter().getPageNumber();
  }

  /*
  Additional fonts are registered to print non-ascii characters.
   */
  private void registerAdditionalFonts(ITextFontResolver fontResolver) {
    String fontFileLocation = "fonts";
    try {
      fontResolver.addFont(
          fontFileLocation + "/NotoSans-Regular.ttf", "noto sans", "Identity-H", true, null);
    } catch (DocumentException | IOException e) {
      log.warn("Unable to register font.", e);
    }

    try {
      fontResolver.addFont(
          fontFileLocation + "/NotoSansMath-Regular.ttf",
          "noto sans math",
          "Identity-H",
          true,
          null);
    } catch (DocumentException | IOException e) {
      log.warn("Unable to register font.", e);
    }

    try {
      fontResolver.addFont(fontFileLocation + "/unifont.ttf", "unifont", "Identity-H", true, null);
    } catch (DocumentException | IOException e) {
      log.warn("Unable to register font.", e);
    }
  }

  private ExportProcesserInput preProcessHTML(ExportProcesserInput documentData) {
    String html = documentData.getDocumentAsHtml();
    html = htmlUnicodeFontProcesser.apply(html);
    log.debug("Processed: {}", html);
    return new ExportProcesserInput(
        html,
        documentData.getComments(),
        documentData.getRevisionInfo(),
        documentData.getNfsLinks());
  }

  /*
   * (non-Javadoc)
   *
   * @see com.researchspace.export.pdf.ExportProcessor#
   * concatenateExportedFilesIntoOne(java.io.File, java.util.List,
   * com.researchspace.service.FileStore, com.researchspace.model.FileProperty)
   */
  @Override
  public FileProperty concatenateExportedFilesIntoOne(
      File finalExportFile,
      List<File> tmpExportedFiles,
      FileStore fileStore,
      FileProperty fileProperty,
      ExportToFileConfig config)
      throws IOException {
    doConcatenation(finalExportFile, tmpExportedFiles, config);
    return saveToFileStore(finalExportFile, tmpExportedFiles, fileStore, fileProperty);
  }

  public boolean supportsFormat(ExportFormat exportFormat) {
    return ExportFormat.PDF.equals(exportFormat);
  }

  @Override
  public void concatenateExportedFilesIntoOne(
      File finalExportFile, List<File> tmpExportedFiles, ExportToFileConfig config)
      throws IOException {
    doConcatenation(finalExportFile, tmpExportedFiles, config);
  }

  private void doConcatenation(
      File finalExportFile, List<File> tmpExportedFiles, ExportToFileConfig config)
      throws IOException {
    List<HashMap<String, Object>> outline = new ArrayList<>();
    HashMap<String, Object> map = new HashMap<>();
    outline.add(map);
    map.put("Title", "Table of Contents");
    List<HashMap<String, Object>> kids = new ArrayList<>();
    map.put("Kids", kids);
    Rectangle pgSize = PageSize.A4;
    Document pdfDocument = new Document(pgSize);
    int pageOffset = 0; // x1=x0+1
    int numPages;
    List<Integer> pos = new ArrayList<>();
    PdfCopy pdfCopy;
    try {
      pdfCopy = new PdfCopy(pdfDocument, new FileOutputStream(finalExportFile));
      pdfDocument.open();

      for (int i = 0; i < tmpExportedFiles.size(); i++) {
        PdfReader pdfReader = new PdfReader(tmpExportedFiles.get(i).getAbsolutePath());
        numPages = pdfReader.getNumberOfPages();
        for (int pageNum = 0; pageNum < numPages; ) {
          pdfCopy.addPage(pdfCopy.getImportedPage(pdfReader, ++pageNum));
        }
        if (i < tmpExportedFiles.size()) {
          pos.add(pageOffset + 1);
          pageOffset += numPages;
        }
      }
    } catch (DocumentException exception) {
      throw new IOException("Problem creating PDF document file", exception);
    }
    for (int j = 0; j < pos.size(); j++) {
      int pgx = pos.get(j);
      HashMap<String, Object> kid = new HashMap<>();
      kids.add(kid);
      kid.put("Title", tmpExportedFiles.get(j).getAbsolutePath());
      kid.put("Action", "GoTo");
      kid.put("Page", String.format("%d Fit", pgx));
    }

    pdfCopy.setOutlines(outline);
    Optional<ExternalId> extId =
        externalIdResolver.getExternalIdForUser(config.getExporter(), IdentifierScheme.ORCID);
    String exporterName = config.getExporter().getDisplayName();
    if (extId.isPresent()) {
      exporterName = String.format("%s - Orcid ID %s", exporterName, extId.get().getIdentifier());
    }
    pdfDocument.addAuthor(exporterName);
    pdfDocument.addCreationDate();

    pdfDocument.addTitle(config.getExportName());
    pdfDocument.close();
  }
}
