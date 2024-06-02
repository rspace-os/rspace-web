package com.researchspace.service.archive;

import static com.researchspace.core.util.FieldParserConstants.ANNOTATION_IMG_CLASSNAME;
import static com.researchspace.core.util.FieldParserConstants.ATTACHMENT_CLASSNAME;
import static com.researchspace.core.util.FieldParserConstants.CHEM_IMG_CLASSNAME;
import static com.researchspace.core.util.FieldParserConstants.COMMENT_CLASS_NAME;
import static com.researchspace.core.util.FieldParserConstants.DATA_CHEM_FILE_ID;
import static com.researchspace.core.util.FieldParserConstants.IMAGE_DROPPED_CLASS_NAME;
import static com.researchspace.core.util.FieldParserConstants.MATH_CLASSNAME;
import static com.researchspace.core.util.MediaUtils.extractFileType;
import static java.lang.String.format;

import com.researchspace.archive.ArchivalDocument;
import com.researchspace.archive.ArchivalDocumentParserRef;
import com.researchspace.archive.ArchivalField;
import com.researchspace.archive.ArchivalForm;
import com.researchspace.archive.ArchivalGalleryMetaDataParserRef;
import com.researchspace.archive.ArchivalGalleryMetadata;
import com.researchspace.archive.ArchivalImportConfig;
import com.researchspace.archive.ArchivalLinkRecord;
import com.researchspace.archive.ArchiveComment;
import com.researchspace.archive.ArchiveCommentItem;
import com.researchspace.archive.ArchiveUtils;
import com.researchspace.archive.IArchiveModel;
import com.researchspace.core.util.FieldParserConstants;
import com.researchspace.core.util.imageutils.ImageProcessingFailureException;
import com.researchspace.core.util.progress.ProgressMonitor;
import com.researchspace.dao.FolderDao;
import com.researchspace.dao.InternalLinkDao;
import com.researchspace.linkedelements.FieldContents;
import com.researchspace.linkedelements.FieldElementLinkPair;
import com.researchspace.linkedelements.FieldElementLinkPairs;
import com.researchspace.linkedelements.FieldParser;
import com.researchspace.linkedelements.RichTextUpdater;
import com.researchspace.model.ChemElementsFormat;
import com.researchspace.model.EcatComment;
import com.researchspace.model.EcatCommentItem;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EcatImageAnnotation;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.FieldAttachment;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.RSMath;
import com.researchspace.model.User;
import com.researchspace.model.Version;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.ImportOverride;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.RecordInformation;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.EcatCommentManager;
import com.researchspace.service.FieldManager;
import com.researchspace.service.FormManager;
import com.researchspace.service.MediaManager;
import com.researchspace.service.RSChemElementManager;
import com.researchspace.service.RecordContext;
import com.researchspace.service.RecordManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/*
 * Helper class for importer implementations
 */
@Slf4j
abstract class AbstractImporterStrategyImpl {

  @Autowired RecordManager recordManager;
  @Autowired FolderDao folderDao;
  @Autowired FormManager formManager;
  @Autowired FieldManager fieldManager;
  @Autowired MediaManager mediaManager;
  @Autowired RSChemElementManager rsChemElementManager;
  @Autowired InternalLinkDao internalLinkDao;

  @Autowired RichTextUpdater rtu;
  @Autowired EcatCommentManager commentMgr;
  @Autowired FolderTreeImporter folderTreeImporter;
  @Autowired FormImporter formImporter;
  @Autowired FieldParser fieldParser;
  @Autowired IPropertyHolder properties;

  @Value("${import.allowCreationDateAfterModificationDate}")
  private boolean allowCreationDateAfterModificationDate;

  void insertRecordsToDatabase(
      User importingUser,
      IArchiveModel archive,
      ArchivalImportConfig iconfig,
      ArchivalLinkRecord linkRecord,
      ImportArchiveReport report,
      RecordContext context,
      ProgressMonitor monitor)
      throws IOException {
    Map<Long, Long> formMap = new HashMap<>();
    Map<Long, Folder> oldIdToNewFolder =
        folderTreeImporter.createFolderTree(
            archive.getFolderTree(), context, iconfig, archive, report);
    for (Entry<Long, Folder> oldToNewId : oldIdToNewFolder.entrySet()) {
      linkRecord.addOldIdToNewIdMapping(oldToNewId.getKey(), oldToNewId.getValue().getId());
    }
    List<ArchivalGalleryMetaDataParserRef> galleryMetaData = archive.getMediaDocs();
    int numElements = archive.getCurrentVersions().size() + 1 + galleryMetaData.size(); // avoid / 0
    Map<String, EcatMediaFile> oldIdToNewGalleryItem =
        importGalleryItems(
            galleryMetaData, oldIdToNewFolder, monitor, numElements, report, importingUser);
    updateImportedGalleryItemSelfReferences(galleryMetaData, oldIdToNewGalleryItem, importingUser);

    List<ArchivalDocumentParserRef> refs = archive.getCurrentVersions();
    // preserve order of creation, see RSPAC-1313
    refs.sort(ArchivalDocumentParserRef.SortArchivalDocumentParserRefByCreationDateAsc);
    for (ArchivalDocumentParserRef ref : refs) {
      log.info(
          "Importing document {} with name '{}'",
          ref.getArchivalDocument().getDocId(),
          ref.getArchivalDocument().getName());
      monitor.setDescription(String.format("Importing document %s", ref.getName()));
      try {
        insertToDatabase(
            ref,
            importingUser,
            formMap,
            linkRecord,
            oldIdToNewFolder,
            report,
            context,
            oldIdToNewGalleryItem,
            iconfig);
        // handle general exceptions here as we're parsing an archive
        // file that may be modified or inconsistent
      } catch (Exception e) {
        String errMsg =
            format("Archive item %s could not be imported: %s", ref.getName(), e.getMessage());
        report.getErrorList().addErrorMsg(errMsg);
        log.error(errMsg, e);
      } finally {
        monitor.worked((monitor.getTotalWorkUnits() / numElements));
      }
    }
  }

  private Map<String, EcatMediaFile> importGalleryItems(
      List<ArchivalGalleryMetaDataParserRef> galleryMetaData,
      Map<Long, Folder> oldIdToNewFolder,
      ProgressMonitor monitor,
      int numElements,
      ImportArchiveReport report,
      User importingUser)
      throws IOException {

    Map<String, EcatMediaFile> oldId2MediaFile = new LinkedHashMap<>();
    log.info("Importing {} media records...", galleryMetaData.size());
    monitor.setDescription("Importing Gallery documents");
    for (ArchivalGalleryMetaDataParserRef galleryRef : galleryMetaData) {
      ArchivalGalleryMetadata galleryMeta = galleryRef.getGalleryXML();
      ImportOverride importOverride = getGalleryMetaImportOverride(galleryMeta);
      Long oldFolderId = galleryMeta.getParentGalleryFolderId();
      File galleryFileToImport = getFileToImport(galleryRef);

      log.info("Attempting to import file {}", galleryFileToImport.getAbsolutePath());
      try (FileInputStream fis = new FileInputStream(galleryFileToImport)) {
        String galleryMetaFileName = galleryMeta.getFileName();

        log.debug(
            "saving [{}] with name [{}] and type [{}] to folder [{}] ",
            galleryFileToImport.getName(),
            galleryMetaFileName,
            extractFileType(galleryMeta.getExtension()),
            oldIdToNewFolder.get(oldFolderId) != null
                ? oldIdToNewFolder.get(oldFolderId).getName()
                : "null, using top-level gallery folder");
        try {
          String displayName =
              StringUtils.isBlank(galleryMeta.getName())
                  ? galleryMetaFileName
                  : galleryMeta.getName();
          EcatMediaFile media =
              mediaManager.saveMediaFile(
                  fis,
                  null,
                  displayName,
                  galleryMetaFileName,
                  null,
                  oldIdToNewFolder.get(oldFolderId),
                  galleryMeta.getDescription(),
                  importingUser,
                  importOverride);
          putIntoIdToMediaFileMap(
              oldId2MediaFile, galleryMeta.getId(), galleryMeta.getModificationDate(), media);
        } catch (ImageProcessingFailureException e) {
          // RSPAC-1085
          log.warn(
              "Error processing image {}: {} - continuing with import",
              galleryMetaFileName,
              e.getMessage());
          report.getInfoList().addErrorMsg("Importing Gallery item " + e.getMessage());
        }
      }
      monitor.worked((monitor.getTotalWorkUnits() / numElements));
    }
    return oldId2MediaFile;
  }

  private ImportOverride getGalleryMetaImportOverride(ArchivalGalleryMetadata galleryMeta) {
    return galleryMeta.createImportOverride(allowCreationDateAfterModificationDate);
  }

  private ImportOverride getArchivalDocImportOverride(ArchivalDocument archivalDoc) {
    return archivalDoc.createImportOverride(allowCreationDateAfterModificationDate);
  }

  private void updateImportedGalleryItemSelfReferences(
      List<ArchivalGalleryMetaDataParserRef> galleryMetaData,
      Map<String, EcatMediaFile> oldIdToNewGalleryItem,
      User importingUser) {
    for (ArchivalGalleryMetaDataParserRef ref : galleryMetaData) {
      Long imageOriginalId = ref.getGalleryXML().getOriginalId();
      if (imageOriginalId != null) {
        EcatMediaFile importedMedia =
            getFromIdToMediaFileMap(oldIdToNewGalleryItem, ref.getGalleryXML().getId(), null);
        EcatMediaFile importedOriginalMedia =
            getFromIdToMediaFileMap(oldIdToNewGalleryItem, imageOriginalId, null);
        if (importedMedia != null
            && importedMedia.isImage()
            && importedOriginalMedia != null
            && importedOriginalMedia.isImage()) {
          EcatImage importedImage = (EcatImage) importedMedia;
          importedImage.setOriginalImage((EcatImage) importedOriginalMedia);
          recordManager.save(importedImage, importingUser);
        }
      }
    }
  }

  // this is to handle case where gallery-selection or whole-user export
  // contains tif and png files. Updates galleryRef with tif filename if need be.
  private File getFileToImport(ArchivalGalleryMetaDataParserRef galleryRef) {
    FileImportConflictResolver fileImportResolver = new FileImportConflictResolver(galleryRef);
    return fileImportResolver.getFileToImport();
  }

  private void putIntoIdToMediaFileMap(
      Map<String, EcatMediaFile> map, Long id, Date modificationDate, EcatMediaFile media) {
    /* media files can be versioned, so id is not enough for an identifier */
    if (modificationDate != null) {
      map.put(id + "-" + modificationDate.getTime(), media);
    }
    /*
     * adding without a filename, so if something (e.g. annotation) references just
     * the id, it can still find it
     */
    map.put(id + "-null", media);
  }

  private EcatMediaFile getFromIdToMediaFileMap(
      Map<String, EcatMediaFile> map, Long id, Date modificationDate) {
    if (modificationDate != null) {
      return map.get(id + "-" + modificationDate.getTime());
    }
    return map.get(id + "-null");
  }

  private void insertToDatabase(
      ArchivalDocumentParserRef ref,
      User importingUser,
      Map<Long, Long> formMap,
      ArchivalLinkRecord linkRecord,
      Map<Long, Folder> oldIdToNewFolder,
      ImportArchiveReport report,
      RecordContext context,
      Map<String, EcatMediaFile> oldIdToNewGalleryItem,
      ArchivalImportConfig iconfig)
      throws IOException, URISyntaxException {

    ArchivalForm archiveForm = ref.getArchivalForm();
    Long olderId = archiveForm.getFormId();
    RSForm formToImport = null;
    long formId = 0L;
    if (formMap.containsKey(olderId)) {
      formId = formMap.get(olderId);
    } else {
      formToImport = makeRSForm(ref, importingUser);
      formManager.save(formToImport, importingUser);
      formMap.put(olderId, formToImport.getId());
      formId = formToImport.getId();
    }

    ArchivalDocument archivalDoc = ref.getArchivalDocument();

    Folder importRoot =
        iconfig
            .getTargetFolderId()
            .map(id -> folderDao.get(id))
            .orElse(folderDao.getRootRecordForUser(importingUser));
    Folder newParent = oldIdToNewFolder.get(archivalDoc.getFolderId());
    if (newParent == null) {
      newParent = importRoot;
    }

    StructuredDocument newDoc =
        recordManager.createNewStructuredDocument(
            newParent.getId(),
            formId,
            importingUser,
            context,
            getArchivalDocImportOverride(archivalDoc));

    if (newDoc != null) {
      linkRecord.addOldIdToNewIdMapping(archivalDoc.getDocId(), newDoc.getId());
      convertStructureDocument(
          archivalDoc, newDoc, importingUser, ref.getPath(), linkRecord, oldIdToNewGalleryItem);
      if (isTemplate(archivalDoc)) {
        newDoc.addType(RecordType.TEMPLATE);
      }
      recordManager.save(newDoc, importingUser);
      String dnm = ref.getDocumentFileName();
      if (dnm != null && dnm.trim().length() > 1) {
        linkRecord.addMap(dnm, Long.toString(newDoc.getId()));
      }
      setUpFieldAttachmentLinks(newDoc, importingUser, report);
      report.addImportedRecord(newDoc);
    } else {
      String msg =
          String.format(
              "Could not create document from archive document: %s", archivalDoc.getName());
      report.getErrorList().addErrorMsg(msg);
      log.warn(msg);
    }
  }

  private void setUpFieldAttachmentLinks(
      StructuredDocument newDoc, User importingUser, ImportArchiveReport report) {
    newDoc.getFields().stream()
        .filter(Field::isTextField)
        .forEach(
            field -> {
              FieldContents attachments =
                  fieldParser.findFieldElementsInContent(field.getFieldData());
              for (EcatMediaFile emf : attachments.getAllMediaFiles().getElements()) {
                fieldManager
                    .addMediaFileLink(emf.getId(), importingUser, field.getId(), false)
                    .map(FieldAttachment::getMediaFile)
                    .ifPresent(report::addImportedMedia);
              }
            });
  }

  private boolean isTemplate(ArchivalDocument archivalDoc) {
    return archivalDoc.getType().contains(RecordType.TEMPLATE.name());
  }

  private RSForm makeRSForm(ArchivalDocumentParserRef parserRef, User user) {
    return formImporter.makeRSForm(parserRef, user);
  }

  // the sdc created and set Form
  private void convertStructureDocument(
      ArchivalDocument archivalDoc,
      StructuredDocument strucDoc,
      User user,
      File pth,
      ArchivalLinkRecord linkRecord,
      Map<String, EcatMediaFile> oldIdToNewGalleryItem)
      throws IOException, URISyntaxException {

    strucDoc.setName(archivalDoc.getName());
    strucDoc.addType(RecordType.NORMAL);
    strucDoc.setCreatedBy(archivalDoc.getCreatedBy());
    strucDoc.setDocTag(archivalDoc.getDocumentTag());
    if (archivalDoc.getTagMetaData() != null) {
      strucDoc.setTagMetaData(archivalDoc.getTagMetaData());
    } else {
      strucDoc.setTagMetaData(archivalDoc.getDocumentTag());
    }
    strucDoc.setUserVersion(new Version(archivalDoc.getVersion()));
    List<ArchivalField> archivalFlds = archivalDoc.getListFields();
    List<Field> stdFields = strucDoc.getFields();
    if (stdFields.size() != archivalFlds.size()) {
      log.warn(
          "Expected equal field list sizes, "
              + "but archival field had {} fields and doc has {} fields, see RSPAC-1793?",
          archivalFlds.size(),
          stdFields.size());
    }
    for (int i = 0; i < archivalFlds.size() && i < stdFields.size(); i++) {

      Field fld = stdFields.get(i);
      fld.setStructuredDocument(strucDoc);
      ArchivalField afd = archivalFlds.get(i);
      log.info(
          "Field name - {}, type - {}, data - {}, fieldData - {}, archiveFld - {}",
          fld.getName(),
          fld.getType(),
          fld.getData().length(),
          fld.getFieldData().length(),
          afd.getFieldData().length());
      convertStructuredDocumentField(
          fld,
          afd,
          archivalDoc.getLastModifiedDate(),
          user,
          pth,
          linkRecord,
          oldIdToNewGalleryItem);
    }
    // RSPAC-2761 - field data modifications push modification date of a doc to current date, so
    // resetting here
    strucDoc.setModificationDate(archivalDoc.getLastModifiedDate());
  }

  private void convertStructuredDocumentField(
      Field fld,
      ArchivalField archivalField,
      Date archivalDocModifiedDate,
      User user,
      File pth,
      ArchivalLinkRecord linkRecord,
      Map<String, EcatMediaFile> oldIdToNewGalleryItem)
      throws IOException, URISyntaxException {
    try {
      fld.setName(archivalField.getFieldName());
      fld.setModificationDate(
          archivalDocModifiedDate.getTime()); // RSPAC-2761, that's better than new Date()
      fld.setFieldData(archivalField.getFieldData());
      clearRevisionFromGalleryLinks(fld);
      setFieldAssociates(fld, archivalField, user, pth, linkRecord, oldIdToNewGalleryItem);
      fieldManager.save(fld, user);
    } catch (IllegalArgumentException ex) {
      log.warn(ex.getMessage(), ex);
    }
  }

  private void clearRevisionFromGalleryLinks(Field field) {
    // only parse fields that contain revisioned links
    if (field.isTextField() && field.getData().contains(RichTextUpdater.REVISION_DATA_ATTR)) {
      rtu.updateLinksWithRevisions(field, null);
    }
  }

  private void setFieldAssociates(
      Field fld,
      ArchivalField archiveFld,
      User user,
      File recordFolder,
      ArchivalLinkRecord linkRecord,
      Map<String, EcatMediaFile> oldIdToNewGalleryItem)
      throws IOException, URISyntaxException {

    fld = importComments(fld, archiveFld);

    // preserve insertion order
    fld = importImages(fld, archiveFld, user, recordFolder, oldIdToNewGalleryItem);
    importAudio(fld, archiveFld, user, recordFolder, oldIdToNewGalleryItem);
    importVideo(fld, archiveFld, user, recordFolder, oldIdToNewGalleryItem);
    fld = importAttachments(fld, archiveFld, user, recordFolder, oldIdToNewGalleryItem);
    fld = importChemistryFiles(fld, archiveFld, user, recordFolder, oldIdToNewGalleryItem);
    fld = importChemElements(fld, archiveFld, user, recordFolder);
    fld = importMath(fld, archiveFld, recordFolder);
    fld = importImageAnnotation(fld, archiveFld, recordFolder, oldIdToNewGalleryItem);
    importLinkedRecords(fld, archiveFld, recordFolder, linkRecord);
    fld = importSketches(fld, archiveFld, recordFolder);
    rtu.updateAttachmentIcons(fld);
    rtu.updateNfsLinksOnImport(fld);
  }

  private Field importComments(Field fld, ArchivalField archiveFld) {
    List<ArchiveComment> comments = archiveFld.getComments();
    // data-mce-src="/images/commentIcon.gif"
    if (comments != null && comments.size() > 0) {
      int cnt = 0;
      for (ArchiveComment ecatComment : comments) {
        EcatComment comm = new EcatComment();
        comm.setAuthor(ecatComment.getAuthor());
        comm.setCreateDate(ecatComment.getCreateDate());
        comm.setUpdateDate(ecatComment.getUpdateDate());
        comm.setLastUpdater(ecatComment.getLastUpdater());
        comm.setParentId(fld.getId());
        comm.setRecord(fld.getStructuredDocument());
        comm = commentMgr.addComment(comm);
        for (ArchiveCommentItem item : ecatComment.getItems()) {
          EcatCommentItem itemEntity = new EcatCommentItem();
          itemEntity.setEcatComment(comm);
          itemEntity.setCreateDate(item.getCreateDate());
          itemEntity.setComId(comm.getId());
          itemEntity.setLastUpdater(item.getLastUpdater());
          itemEntity.setUpdateDate(item.getUpdateDate());
          itemEntity.setItemContent(item.getItemContent());
          commentMgr.addCommentItem(comm, itemEntity);
        }
        fld = rtu.changeDataForImportedField(fld, comm.getComId(), null, COMMENT_CLASS_NAME, cnt);
        cnt++;
      }
    }
    return fld;
  }

  private Field importImages(
      Field fld,
      ArchivalField archiveFld,
      User user,
      File recordFolder,
      Map<String, EcatMediaFile> oldIdToNewGalleryItem)
      throws FileNotFoundException, IOException {
    List<ArchivalGalleryMetadata> imgMeta = archiveFld.getImgMeta();

    if (imgMeta != null && imgMeta.size() > 0) {
      int imageIndex = 0;
      for (ArchivalGalleryMetadata agm : imgMeta) {
        EcatImage image =
            (EcatImage)
                getFromIdToMediaFileMap(
                    oldIdToNewGalleryItem, agm.getId(), agm.getModificationDate());
        // with gallery file versioning it could be that two images with same id link
        // different files
        if (image == null) {
          ImportOverride importOverride = getGalleryMetaImportOverride(agm);
          File fx = new File(recordFolder, agm.getLinkFile());
          if (fx.exists()) {
            try (FileInputStream fis = new FileInputStream(fx)) {
              String newName =
                  StringUtils.isBlank(agm.getName()) ? agm.getFileName() : agm.getName();
              image = mediaManager.saveNewImage(newName, fis, user, null, importOverride);
              putIntoIdToMediaFileMap(
                  oldIdToNewGalleryItem, agm.getId(), agm.getModificationDate(), image);
            }
          }
        }
        if (image != null) {
          var attrs = rtu.makeEcatImageAttributes(image, Long.toString(fld.getId()));
          fld =
              rtu.changeDataForImportedField(
                  fld, image.getId(), attrs, IMAGE_DROPPED_CLASS_NAME, imageIndex);
        } else {
          log.warn(
              "Image with original ID {} could not be found in the archive, skipping", agm.getId());
        }
        imageIndex++;
      }
    }
    return fld;
  }

  private void importAudio(
      Field fld,
      ArchivalField archiveFld,
      User user,
      File recordFolder,
      Map<String, EcatMediaFile> oldIdToNewGalleryItem)
      throws FileNotFoundException, IOException {
    List<ArchivalGalleryMetadata> audioMeta = archiveFld.getAudioMeta();
    int count = 0;
    if (audioMeta != null && audioMeta.size() > 0) {
      for (ArchivalGalleryMetadata agm : audioMeta) {
        EcatMediaFile audio =
            getFromIdToMediaFileMap(oldIdToNewGalleryItem, agm.getId(), agm.getModificationDate());
        // this will be the case when we're importing a selection.
        if (audio == null) {
          ImportOverride importOverride = getGalleryMetaImportOverride(agm);
          File fx = new File(recordFolder, agm.getLinkFile());
          if (fx.exists()) {
            try (FileInputStream fis = new FileInputStream(fx)) {
              String newName =
                  StringUtils.isBlank(agm.getName()) ? agm.getFileName() : agm.getName();
              audio = mediaManager.saveNewAudio(newName, fis, user, null, importOverride);
              putIntoIdToMediaFileMap(
                  oldIdToNewGalleryItem, agm.getId(), agm.getModificationDate(), audio);
            }
          }
        }
        if (audio != null) {
          rtu.changeMediaData(fld, audio, count, FieldParserConstants.AUDIO_CLASSNAME);
          count++;
        }
      }
    }
  }

  private void importVideo(
      Field fld,
      ArchivalField archiveFld,
      User user,
      File recordFolder,
      Map<String, EcatMediaFile> oldIdToNewGalleryItem)
      throws IOException {
    List<ArchivalGalleryMetadata> videoMeta = archiveFld.getVideoMeta();
    int count = 0;
    if (videoMeta != null && !videoMeta.isEmpty()) {
      for (ArchivalGalleryMetadata agm : videoMeta) {
        EcatMediaFile video =
            getFromIdToMediaFileMap(oldIdToNewGalleryItem, agm.getId(), agm.getModificationDate());
        // this will be the case when we're importing a selection.
        if (video == null) {
          ImportOverride importOverride = getGalleryMetaImportOverride(agm);
          File fx = new File(recordFolder, agm.getLinkFile());
          if (fx.exists()) {
            try (FileInputStream fis = new FileInputStream(fx)) {
              String newName =
                  StringUtils.isBlank(agm.getName()) ? agm.getFileName() : agm.getName();
              video = mediaManager.saveNewVideo(newName, fis, user, null, importOverride);
              putIntoIdToMediaFileMap(
                  oldIdToNewGalleryItem, agm.getId(), agm.getModificationDate(), video);
            }
          }
        }
        if (video != null) {
          rtu.changeMediaData(fld, video, count, FieldParserConstants.VIDEO_CLASSNAME);
        }
      }
      count++;
    }
  }

  private Field importAttachments(
      Field field,
      ArchivalField archiveFld,
      User user,
      File recordFolder,
      Map<String, EcatMediaFile> oldIdToNewGalleryItem)
      throws IOException {
    List<ArchivalGalleryMetadata> attachMeta = archiveFld.getAttachMeta();
    if (attachMeta != null && !attachMeta.isEmpty()) {
      int cnt = 0;
      for (ArchivalGalleryMetadata agm : attachMeta) {
        ImportOverride importOverride = getGalleryMetaImportOverride(agm);
        EcatMediaFile ecatDocument =
            getFromIdToMediaFileMap(oldIdToNewGalleryItem, agm.getId(), agm.getModificationDate());
        // this will be the case when we're importing a selection.
        if (ecatDocument == null) {
          File fx = new File(recordFolder, agm.getLinkFile());
          if (fx.exists()) {
            try (FileInputStream fis = new FileInputStream(fx)) {
              String newName =
                  StringUtils.isBlank(agm.getName()) ? agm.getFileName() : agm.getName();
              ecatDocument = mediaManager.saveNewDocument(newName, fis, user, null, importOverride);
              putIntoIdToMediaFileMap(
                  oldIdToNewGalleryItem, agm.getId(), agm.getModificationDate(), ecatDocument);
            }
          }
        }
        if (ecatDocument != null) {
          Map<String, String> attrs = new HashMap<>();
          attrs.put("href", ecatDocument.getName());
          field =
              rtu.changeDataForImportedField(
                  field, ecatDocument.getId(), attrs, ATTACHMENT_CLASSNAME, cnt);
          cnt++;
        } else {
          log.warn(
              "Document with original ID {} could not be found in the archive, skipping",
              agm.getId());
        }
      }
    }
    return field;
  }

  private Field importSketches(Field fld, ArchivalField archiveFld, File recordFolder)
      throws FileNotFoundException, IOException {
    List<ArchivalGalleryMetadata> sktchMeta = archiveFld.getSktchMeta();
    if (sktchMeta != null && sktchMeta.size() > 0) {
      int cnt = 0;
      for (ArchivalGalleryMetadata agm : sktchMeta) {
        File fx = new File(recordFolder, agm.getLinkFile());
        if (fx.exists()) {
          try (FileInputStream fis = new FileInputStream(fx)) {
            EcatImageAnnotation sktch =
                mediaManager.importSketch(
                    fis, agm.getAnnotation(), fld.getId(), fld.getStructuredDocument());

            Map<String, String> attrs = rtu.makeSketchAttributes(sktch);
            fld =
                rtu.changeDataForImportedField(
                    fld, sktch.getId(), attrs, FieldParserConstants.SKETCH_IMG_CLASSNAME, cnt);
            cnt++;
          }
        }
      }
    }
    return fld;
  }

  private void importLinkedRecords(
      Field fld, ArchivalField archiveFld, File recordFolder, ArchivalLinkRecord linkRecord) {
    List<ArchivalGalleryMetadata> linkMeta = archiveFld.getLinkMeta();
    if (linkMeta != null && linkMeta.size() > 0) {
      for (ArchivalGalleryMetadata agm : linkMeta) {
        if ("Link/FOLDER".equals(agm.getContentType())
            || "Link/NOTEBOOK".equals(agm.getContentType())) {
          linkRecord.addSourceFieldId(Long.toString(fld.getId()));
          continue;
        }

        String pathStr = recordFolder.getAbsolutePath();
        int pos = FilenameUtils.indexOfLastSeparator(pathStr);
        pathStr = pathStr.substring(0, pos + 1);

        String pathx = pathStr + agm.getLinkFile() + File.separator + agm.getLinkFile() + ".xml";
        File fx = new File(pathx);
        if (fx.exists()) {
          linkRecord.addSourceFieldId(Long.toString(fld.getId()));
        } else {
          log.warn(
              "linked document: {} could  not be found for linked record {}",
              fx.getAbsolutePath(),
              agm.getId());
        }
      }
    }
    checkFieldForInternalLinksWithAbsoluteUrl(fld);
  }

  private void checkFieldForInternalLinksWithAbsoluteUrl(Field fld) {
    // RSPAC-1357: check field for internal links with absolute URL pointing to
    // current instance & import these too
    if (fld.isTextField()) {
      FieldContents fieldContents = new FieldContents();
      fieldParser.findFieldElementsInContentForCssClass(
          fieldContents, fld.getData(), FieldParserConstants.LINKEDRECORD_CLASS_NAME);
      FieldElementLinkPairs<RecordInformation> links =
          fieldContents.getLinkedRecordsWithNonRelativeUrl();

      try {
        if (links.size() > 0) {
          String serverUrl = properties.getServerUrl();
          for (FieldElementLinkPair<RecordInformation> linkedRecord : links.getPairs()) {
            String urlFromFieldContent = linkedRecord.getLink();
            RecordInformation recordInfo = linkedRecord.getElement();
            String urlForCurrentInstance =
                ArchiveUtils.getAbsoluteGlobalLink(recordInfo.getOid().getIdString(), serverUrl);
            if (urlForCurrentInstance.equals(urlFromFieldContent)) {
              internalLinkDao.saveInternalLink(
                  fld.getStructuredDocument().getId(), recordInfo.getId());
            }
          }
          updateInternalLinksWithAbsoluteUrlOnImport(fld, serverUrl);
        }
      } catch (Exception e) {
        log.warn("Exception on updating absolute urls in field " + fld.getId(), e);
      }
    }
  }

  private void updateInternalLinksWithAbsoluteUrlOnImport(Field fld, String serverUrl) {
    Document dc = Jsoup.parse(fld.getFieldData());
    Elements elms = dc.select(String.format("a.%s", FieldParserConstants.LINKEDRECORD_CLASS_NAME));
    String thisInstanceAbsoluteUrlPrefix =
        serverUrl + (serverUrl.endsWith("/") ? "globalId/" : "/globalId/");
    for (Element internalLink : elms) {
      String elemHref = internalLink.attr("href");
      if (elemHref != null) {
        // link points to the current instance
        if (elemHref.startsWith(thisInstanceAbsoluteUrlPrefix)) {
          // make the link relative
          internalLink.attr("href", elemHref.substring(serverUrl.length()));
        }
      }
    }
    fld.setFieldData(dc.body().html());
  }

  private Field importImageAnnotation(
      Field fld,
      ArchivalField archiveFld,
      File recordFolder,
      Map<String, EcatMediaFile> oldIdToNewGalleryItem)
      throws FileNotFoundException, IOException {
    List<ArchivalGalleryMetadata> annotMeta = archiveFld.getAnnotMeta();
    if (annotMeta != null && annotMeta.size() > 0) {
      int cnt = 0;
      for (ArchivalGalleryMetadata agm : annotMeta) {
        Long originalImageID = agm.getOriginalId();
        if (originalImageID == null) {
          log.warn(
              "The original image id for this annotation {} is not specified, annotation not"
                  + " imported",
              agm.getFileName());
          cnt++;
          continue;
        }
        File fx = new File(recordFolder, agm.getLinkFile());
        if (fx.exists()) {
          try (FileInputStream fis = new FileInputStream(fx)) {
            EcatImage newImage =
                (EcatImage) getFromIdToMediaFileMap(oldIdToNewGalleryItem, originalImageID, null);
            if (newImage == null) {
              log.warn(
                  "New image id not found for original image id {}, cannot import this annotation"
                      + " {}",
                  originalImageID,
                  agm.getFileName());
            } else {
              EcatImageAnnotation annot =
                  mediaManager.importImageAnnotation(
                      fis,
                      agm.getAnnotation(),
                      fld.getId(),
                      fld.getStructuredDocument(),
                      newImage.getId());
              Map<String, String> attrs =
                  rtu.makeImageAnnotationAttributes(annot, Long.toString(fld.getId()));
              fld =
                  rtu.changeDataForImportedField(
                      fld, annot.getId(), attrs, ANNOTATION_IMG_CLASSNAME, cnt);
            }
            cnt++;
          }
        }
      }
    }
    return fld;
  }

  private static final String MRV_FORMAT_MARKER =
      "xsi:schemaLocation=\"http://www.chemaxon.com"
          + " http://www.chemaxon.com/marvin/schema/mrvSchema";

  private Field importChemElements(
      Field fld, ArchivalField archiveFld, User user, File recordFolder) throws IOException {
    List<ArchivalGalleryMetadata> chemMeta = archiveFld.getChemElementMeta();
    if (chemMeta != null && !chemMeta.isEmpty()) {
      int cnt = 0;
      for (ArchivalGalleryMetadata agm : chemMeta) {
        RSChemElement currentChem = null;
        File dataImageFile = new File(recordFolder, agm.getLinkFile());
        if (dataImageFile.exists()) {
          String chemAnnotation = agm.getAnnotation();
          ChemElementsFormat format = null;
          if (!StringUtils.isBlank(agm.getChemElementsFormat())) {
            try {
              format = ChemElementsFormat.valueOf(agm.getChemElementsFormat());
            } catch (IllegalArgumentException e) {
              log.warn(
                  "Could not recognise chem format type '{}', this may not be rendered properly "
                      + "in chemical editor",
                  agm.getChemElementsFormat());
            }
          }
          if (format == null) {
            /* all pre1.55 imports are in mol format, all pre1.61.1 don't have this property set.
             * let's try to guess by looking at annotation, if looks like marvin xml then it's an MRV  */
            format =
                chemAnnotation.contains(MRV_FORMAT_MARKER)
                    ? ChemElementsFormat.MRV
                    : ChemElementsFormat.MOL;
          }
          try (FileInputStream fis = new FileInputStream(dataImageFile)) {
            currentChem =
                mediaManager.importChemElement(
                    fis, chemAnnotation, format, fld.getId(), fld.getStructuredDocument());
            Map<String, String> mp =
                rtu.makeChemImageAttributes(Long.toString(currentChem.getId()));
            fld =
                rtu.changeDataForImportedField(
                    fld, currentChem.getId(), mp, CHEM_IMG_CLASSNAME, cnt);
            cnt++;
          }
        }
        if (StringUtils.isNotBlank(agm.getLinkToOriginalFile())) {
          File previewImageFile = new File(recordFolder, agm.getLinkToOriginalFile());
          if (previewImageFile.exists() && currentChem != null) {
            try (FileInputStream fis = new FileInputStream(previewImageFile)) {
              rsChemElementManager.saveChemImagePng(currentChem, fis, user);
            }
          }
        }
      }
    }
    return fld;
  }

  private Field importChemistryFiles(
      Field field,
      ArchivalField archiveFld,
      User user,
      File recordFolder,
      Map<String, EcatMediaFile> oldIdToNewGalleryItem)
      throws IOException {
    List<ArchivalGalleryMetadata> attachMeta = archiveFld.getChemFileMeta();
    if (attachMeta != null && !attachMeta.isEmpty()) {
      int cnt = 0;
      for (ArchivalGalleryMetadata agm : attachMeta) {
        ImportOverride importOverride = getGalleryMetaImportOverride(agm);
        EcatMediaFile ecatMediaFile =
            getFromIdToMediaFileMap(oldIdToNewGalleryItem, agm.getId(), agm.getModificationDate());
        // this will be the case when we're importing a selection.
        if (ecatMediaFile == null) {
          File fx = new File(recordFolder, agm.getLinkFile());
          if (fx.exists()) {
            try (FileInputStream fis = new FileInputStream(fx)) {
              String newName =
                  StringUtils.isBlank(agm.getName()) ? agm.getFileName() : agm.getName();
              ecatMediaFile =
                  mediaManager.saveNewChemFile(newName, fis, user, null, importOverride);
              putIntoIdToMediaFileMap(
                  oldIdToNewGalleryItem, agm.getId(), agm.getModificationDate(), ecatMediaFile);
            }
          }
        }
        if (ecatMediaFile != null) {
          Map<String, String> attrs = new HashMap<>();
          attrs.put(DATA_CHEM_FILE_ID, ecatMediaFile.getId().toString());
          field =
              rtu.changeDataForImportedField(
                  field, ecatMediaFile.getId(), attrs, CHEM_IMG_CLASSNAME, cnt);
          cnt++;
        } else {
          log.warn(
              "Document with original ID {} could not be found in the archive, skipping",
              agm.getId());
        }
      }
    }
    return field;
  }

  private Field importMath(Field fld, ArchivalField archiveFld, File recordFolder)
      throws FileNotFoundException, IOException {
    List<ArchivalGalleryMetadata> mathMeta = archiveFld.getMathMeta();
    if (mathMeta != null && mathMeta.size() > 0) {
      int cnt = 0;
      for (ArchivalGalleryMetadata agm : mathMeta) {
        File fx = new File(recordFolder, agm.getLinkFile());
        if (fx.exists()) {
          FileInputStream fis = new FileInputStream(fx);
          RSMath importedMath =
              mediaManager.importMathElement(
                  fis, agm.getAnnotation(), fld.getId(), fld.getStructuredDocument());
          Map<String, String> mp = rtu.makeMathAttributes(importedMath.getId());
          fld = rtu.changeDataForImportedField(fld, importedMath.getId(), mp, MATH_CLASSNAME, cnt);
          cnt++;
        }
      }
    }
    return fld;
  }

  public final void doImport(
      User importingUser,
      IArchiveModel archiveModel,
      ArchivalImportConfig iconfig,
      ArchivalLinkRecord linkRecord,
      ImportArchiveReport report,
      RecordContext context,
      ProgressMonitor monitor) {
    monitor.setDescription(getMonitorMessage());
    try {
      doDatabaseInsertion(
          importingUser, archiveModel, iconfig, linkRecord, report, context, monitor);
    } catch (Exception e) {
      throw new RuntimeException(
          e); // this method might be a lambda so checked exceptions are problematic
    }
    monitor.worked(100);
  }

  abstract String getMonitorMessage();

  abstract void doDatabaseInsertion(
      User importingUser,
      IArchiveModel archiveModel,
      ArchivalImportConfig iconfig,
      ArchivalLinkRecord linkRecord,
      ImportArchiveReport report,
      RecordContext context,
      ProgressMonitor monitor)
      throws Exception;
}
