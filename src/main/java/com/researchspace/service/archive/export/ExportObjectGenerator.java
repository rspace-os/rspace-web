package com.researchspace.service.archive.export;

import static com.researchspace.core.util.imageutils.ImageUtils.convertTiffToPng;
import static org.apache.commons.io.FilenameUtils.getBaseName;

import com.researchspace.archive.ArchivalDocument;
import com.researchspace.archive.ArchivalField;
import com.researchspace.archive.ArchivalGalleryMetadata;
import com.researchspace.archive.ArchiveFolder;
import com.researchspace.archive.ImmutableExportRecordList;
import com.researchspace.archive.model.ArchiveModelFactory;
import com.researchspace.archive.model.IArchiveExportConfig;
import com.researchspace.core.util.imageutils.ImageUtils;
import com.researchspace.files.service.FileStore;
import com.researchspace.linkedelements.FieldContents;
import com.researchspace.linkedelements.FieldElementLinkPair;
import com.researchspace.linkedelements.FieldElementLinkPairs;
import com.researchspace.linkedelements.FieldParser;
import com.researchspace.linkedelements.RichTextUpdater;
import com.researchspace.model.EcatAudio;
import com.researchspace.model.EcatChemistryFile;
import com.researchspace.model.EcatComment;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EcatImageAnnotation;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.EcatVideo;
import com.researchspace.model.IFieldLinkableElement;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.RSMath;
import com.researchspace.model.audit.AuditedRecord;
import com.researchspace.model.netfiles.NfsElement;
import com.researchspace.model.record.RecordInformation;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.archive.ExportImport;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.crypto.hash.Sha256Hash;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Generates archive contents for a record to export <br>
 * Configured in {@link com.axiope.service.cfg.BaseConfig} to be a prototype - i.e., it's OK to have
 * state as one instance is created per service thread.
 */
public class ExportObjectGenerator {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private @Autowired FieldParser fieldParser;
  private @Autowired ExportObjectWriter htmlWriter;
  private @Autowired RichTextUpdater richTextUpdater;
  private @Autowired ResourceToArchiveCopier resourceCopier;
  private @Autowired FieldExporterSupport support;
  private @Autowired @Qualifier("compositeFileStore") FileStore fileStore;

  private ArchiveModelFactory archiveModelFactory = new ArchiveModelFactory();
  private Set<AuditedRecord> seenDocuments = new HashSet<AuditedRecord>();

  // the folder in which archive artifacts will be assembled
  private File exportFolder;

  protected void setExportFolder(File exportFolder) {
    this.exportFolder = exportFolder;
  }

  private IArchiveExportConfig exportConfig;

  protected void setExportConfig(IArchiveExportConfig exportConfig) {
    this.exportConfig = exportConfig;
  }

  /*
   * Exports a Gallery file to its own media_ folder in the export, independent of
   * a document.
   */
  public String makeGalleryExport(
      EcatMediaFile mediaFile,
      Number revision,
      File recordFolder,
      List<ExportedRecord> archived,
      RoCrateHandler roCrateHandler)
      throws URISyntaxException, IOException {

    AuditedRecord ar = new AuditedRecord(mediaFile, revision == null ? -1 : revision);
    if (isSeen(ar)) {
      return null;
    }
    String documentFileName = recordFolder.getName();
    String exportFileName = exportConfig.generateDocumentExportFileName(documentFileName);
    // the xml/html file that will contain a link to the Gallery item
    File outputDocFile = new File(recordFolder, exportFileName);
    ArchivalGalleryMetadata mediaXML = archiveModelFactory.createGalleryMetadata(mediaFile);

    String exportedFileName =
        copyFromFilestoreToMediaExportFolder(mediaFile, recordFolder, mediaXML);
    ExportedRecord exportedRecord = new ExportedRecord(mediaFile, mediaXML);
    writeToFiles(recordFolder, archived, outputDocFile, exportedRecord);
    if (roCrateHandler != null) {
      Sha256Hash hash = new Sha256Hash(outputDocFile);
      roCrateHandler.buildXmlFileEntity(
          recordFolder, exportFileName, null, false, "", hash.toHex());
      Sha256Hash mediaHash = new Sha256Hash(new File(recordFolder, mediaFile.getName()));
      roCrateHandler.buildFileEntityForEcatMedia(recordFolder, mediaFile, mediaHash.toHex());
    }
    return exportedFileName;
  }

  private String copyFromFilestoreToMediaExportFolder(
      EcatMediaFile mediaFile, File recordFolder, ArchivalGalleryMetadata mediaXML)
      throws URISyntaxException, MalformedURLException, IOException, FileNotFoundException {
    File originalExportFile = new File(recordFolder, mediaFile.getName());
    File fx = fileStore.findFile(mediaFile.getFileProperty());
    String exportedFileName = "";
    if (ImageUtils.isTiff(mediaFile.getExtension())) {
      exportedFileName =
          convertTiffToPng(fx, recordFolder, getBaseName(mediaFile.getName())).getName();
      mediaXML.setFileName(exportedFileName);
    }
    copyFile(fx, originalExportFile);
    return exportedFileName;
  }

  /**
   * @param aconfig
   * @param strucDoc the document to export
   * @param revision An optional revision number, can be <code>null</code>.
   * @param recordFolder
   * @param linkLevel the current depth of links to follow to include in the archive. 0 means don't
   *     follow links
   * @param archived
   * @param itemsToExport
   * @return the name of the xml file holding the exported docs, or <code>null</code> if this doc
   *     was already exported, or could not be exported.
   */
  public String makeRecordExport(
      IArchiveExportConfig aconfig,
      StructuredDocument strucDoc,
      Number revision,
      File recordFolder,
      int linkLevel,
      List<ExportedRecord> archived,
      List<ArchiveFolder> folderTree,
      NfsExportContext nfsContext,
      ImmutableExportRecordList itemsToExport,
      RoCrateHandler roCrateHandler) {
    // we need to make sure that we can export multiple revisions of an entity
    AuditedRecord ar = new AuditedRecord(strucDoc, revision == null ? -1 : revision);
    // we only want to export records once
    if (isSeen(ar)) {
      return null;
    }

    String documentFileName = recordFolder.getName();
    String exportFileName = exportConfig.generateDocumentExportFileName(documentFileName);
    File outputDocFile = new File(recordFolder, exportFileName);

    ArchivalDocument archiveDoc = archiveModelFactory.createArchivalDocument(strucDoc);
    List<ArchivalField> flds = archiveDoc.getListFields();
    for (ArchivalField archiveField : flds) {
      createAssociateFiles(
          aconfig, archiveField, recordFolder, revision, nfsContext, itemsToExport);
    }
    ExportedRecord exportedRecord = new ExportedRecord(strucDoc, archiveDoc);
    exportedRecord.calculateParentFolder(folderTree);
    writeToFiles(recordFolder, archived, outputDocFile, exportedRecord);
    for (ArchivalField archiveField : flds) {
      if (roCrateHandler != null) {
        roCrateHandler.buildFileEntityForArchiveGalleryFieldFiles(recordFolder, archiveField);
        roCrateHandler.buildFileEntityForNfsFieldFiles(archiveField);
      }
    }
    if (roCrateHandler != null) {
      Sha256Hash hash = new Sha256Hash(outputDocFile);
      Sha256Hash hashForForm =
          new Sha256Hash(new File(recordFolder, documentFileName + "_form.xml"));
      roCrateHandler.buildXmlFileEntity(recordFolder, exportFileName, strucDoc, true, hash.toHex());
      roCrateHandler.buildXmlFileEntity(
          recordFolder, documentFileName + "_form.xml", strucDoc, false, hashForForm.toHex());
    }
    return exportFileName;
  }

  private void writeToFiles(
      File recordFolder,
      List<ExportedRecord> archived,
      File outputDocFile,
      ExportedRecord exportedRecord) {
    exportedRecord.setOutFile(outputDocFile);
    exportedRecord.setRecordFolder(recordFolder);
    getWriter().writeExportObject(outputDocFile, exportedRecord);
    archived.add(exportedRecord);
  }

  private ExportObjectWriter getWriter() {
    if (exportConfig.isArchive()) {
      return new XMLWriter();
    }
    return htmlWriter;
  }

  // -------- support methods ------------------------------

  /**
   * @param aconfig
   * @param archiveField
   * @param recordFolder
   * @param revision
   * @param exportList
   */
  private void createAssociateFiles(
      IArchiveExportConfig aconfig,
      ArchivalField archiveField,
      File recordFolder,
      Number revision,
      NfsExportContext nfsContext,
      ImmutableExportRecordList exportList) {

    FieldContents fieldContents =
        fieldParser.findFieldElementsInContent(archiveField.getFieldData());
    FieldExportContext context =
        new FieldExportContext(
            aconfig, archiveField, recordFolder, exportFolder, revision, nfsContext, exportList);
    try {
      addElementsToExport(
          context, fieldContents, RsChemElementFieldExporter.class, RSChemElement.class);
      addElementsToExport(context, fieldContents, MathFieldExporter.class, RSMath.class);
      addElementsToExport(context, fieldContents, CommentFieldExporter.class, EcatComment.class);
      addElementsToExport(context, fieldContents, ImageFieldExporter.class, EcatImage.class);
      addElementsToExport(context, fieldContents, NfsElementFieldExporter.class, NfsElement.class);
    } catch (InstantiationException | IllegalAccessException e) {
      log.warn("exception parsing content of archive field " + archiveField.getFieldId(), e);
    }
    // these don't fit into the generic mechanism easily
    addImageAnnotationsToExport(context, fieldContents);
    addSketchesToExport(context, fieldContents);
    addAttachmentFilesToExport(context, fieldContents);
    updateInteralLinksInExport(context, fieldContents, exportList);
    addResourcesFiles(archiveField);
    updateIframeLinks(archiveField);
  }

  private void updateIframeLinks(ArchivalField archiveField) {
    String fieldData = archiveField.getFieldData();
    Document d = Jsoup.parse(fieldData);
    if (d.select("div.embedIframeDiv.mceNonEditable").size() > 0) {
      d.select("div.embedIframeDiv.mceNonEditable")
          .forEach(
              element -> {
                Element iframe = element.select("iframe").first();
                assert iframe != null;
                UriComponentsBuilder uriComponentsBuilder =
                    UriComponentsBuilder.fromUriString(iframe.attr("src"));
                uriComponentsBuilder.replaceQueryParam("access");
                String url = uriComponentsBuilder.toUriString();
                iframe.attr("src", url);
              });
      archiveField.setFieldData(d.outerHtml());
    }
  }

  // this used same exporter class for different media subclasses, as doesn't fit into the generic
  // mechanism
  @SuppressWarnings("unchecked")
  private void addAttachmentFilesToExport(FieldExportContext context, FieldContents fieldContents) {
    FieldElementLinkPairs<EcatMediaFile> medias =
        fieldContents.getMediaElements(
            EcatAudio.class, EcatDocumentFile.class, EcatVideo.class, EcatChemistryFile.class);
    for (FieldElementLinkPair<EcatMediaFile> mediaFilePair : medias.getPairs()) {
      new AttachmentFieldExporter(support).export(context, mediaFilePair);
    }
  }

  // add static resources e.g. document type icons
  private void addResourcesFiles(ArchivalField archiveField) {
    String fielddata = archiveField.getFieldData();
    Elements elements = fieldParser.getNonRSpaceImages(fielddata);
    for (Element el : elements) {
      String url = el.attr("src");
      if (StringUtils.isEmpty(url)) {
        url = el.attr("data-src");
      }
      fielddata = copyFromClassPathResourceToArchiveResources(fielddata, url);
    }
    // copy comment icon across to resources, as link is already updated in field data
    copyFromClassPathResourceToArchiveResources(fielddata, "/images/commentIcon.gif");
    archiveField.setFieldData(fielddata);
  }

  // generic method
  private <F extends AbstractFieldExporter<T>, T extends IFieldLinkableElement>
      void addElementsToExport(
          FieldExportContext context,
          FieldContents fieldContents,
          Class<F> exporterClass,
          Class<T> type)
          throws InstantiationException, IllegalAccessException {
    FieldElementLinkPairs<T> elements = fieldContents.getElements(type);
    for (FieldElementLinkPair<T> element : elements.getPairs()) {
      FieldExporterFactory.createFactory(exporterClass, type)
          .create(support)
          .export(context, element);
    }
  }

  // sketches and annotations use the same class so can't be generified
  private void addSketchesToExport(FieldExportContext context, FieldContents fieldContents) {
    FieldElementLinkPairs<EcatImageAnnotation> sks = fieldContents.getSketches();
    for (FieldElementLinkPair<EcatImageAnnotation> sketch : sks.getPairs()) {
      new SketchFieldExporter(support).export(context, sketch);
    }
  }

  // this used different method signature to allow recursion of linked documents
  private void updateInteralLinksInExport(
      FieldExportContext context,
      FieldContents fieldContents,
      ImmutableExportRecordList exportList) {
    FieldElementLinkPairs<RecordInformation> links =
        fieldContents.getLinkedRecordsWithRelativeUrl();
    for (FieldElementLinkPair<RecordInformation> linkedRecord : links.getPairs()) {
      new LinkedRecordFieldExporter(support, exportList).export(context, linkedRecord);
    }
  }

  private void addImageAnnotationsToExport(
      FieldExportContext context, FieldContents fieldContents) {
    FieldElementLinkPairs<EcatImageAnnotation> imgas = fieldContents.getImageAnnotations();
    for (FieldElementLinkPair<EcatImageAnnotation> imgax : imgas.getPairs()) {
      new ImageAnnotationFieldExporter(support).export(context, imgax);
    }
  }

  // takes a static resource from the classpath, updates the links in the archive documents to
  // point at the new resource.
  private String copyFromClassPathResourceToArchiveResources(String fieldData, String url) {

    // we ignore images that are not local to RSpace on classpath, e.g. img links pasted in
    // from a webpage, see rspac-2481
    if (!url.startsWith("/")) {
      return fieldData;
    }
    // resource might not exist
    Optional<String> resourceFileNameOpt =
        resourceCopier.copyFromClassPathResourceToArchiveResources(url, exportFolder);
    if (resourceFileNameOpt.isPresent()) {
      fieldData =
          richTextUpdater.replaceImageSrc(
              url, "../" + ExportImport.RESOURCES + "/" + resourceFileNameOpt.get(), fieldData);
    }
    return fieldData;
  }

  private void copyFile(File fx, File outFile) throws IOException {
    try (FileInputStream fis = new FileInputStream(fx);
        FileOutputStream fos = new FileOutputStream(outFile)) {
      IOUtils.copy(fis, fos);
    }
  }

  private boolean isSeen(AuditedRecord record) {
    if (seenDocuments.contains(record)) {
      log.info("Already archived  record [{}], skipping", record.getRecord().getId());
      return true;
    }
    seenDocuments.add(record);
    return false;
  }
}
