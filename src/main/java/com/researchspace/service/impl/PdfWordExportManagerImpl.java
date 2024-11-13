package com.researchspace.service.impl;

import static com.researchspace.archive.ExportScope.SELECTION;
import static com.researchspace.model.record.Folder.EXPORTS_FOLDER_NAME;
import static com.researchspace.model.record.Folder.TEMPLATE_MEDIA_FOLDER_NAME;
import static org.apache.commons.lang.RandomStringUtils.randomAlphabetic;
import static org.apache.commons.lang.StringUtils.isBlank;

import com.researchspace.archive.ExportScope;
import com.researchspace.core.util.EscapeReplacement;
import com.researchspace.core.util.SecureStringUtils;
import com.researchspace.dao.RecordDao;
import com.researchspace.documentconversion.spi.ConversionResult;
import com.researchspace.export.pdf.ExportOperationDetails;
import com.researchspace.export.pdf.ExportProcesserInput;
import com.researchspace.export.pdf.ExportProcessor;
import com.researchspace.export.pdf.ExportToFileConfig;
import com.researchspace.export.pdf.HTMLStringGenerator;
import com.researchspace.files.service.FileStore;
import com.researchspace.files.service.InternalFileStore;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.FileProperty;
import com.researchspace.model.SearchDepth;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.CommunicationManager;
import com.researchspace.service.FolderManager;
import com.researchspace.service.IMediaFactory;
import com.researchspace.service.RecordManager;
import com.researchspace.service.archive.PdfWordExportManager;
import com.researchspace.service.archive.export.ExportFailureException;
import com.researchspace.service.archive.export.RecordIdExtractor;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PdfWordExportManagerImpl extends AbstractExporter implements PdfWordExportManager {

  private static final String PDF_ROOT = "pdfs/";

  private static AtomicInteger counterx = new AtomicInteger(0);

  private @Autowired @Qualifier("compositeFileStore") FileStore fileStore;
  private @Autowired ApplicationEventPublisher publisher;
  private @Autowired IMediaFactory ecatDocumentFactory;
  private @Autowired RecordManager recordManager;
  private @Autowired RecordDao recordDao;
  private @Autowired CommunicationManager commMgr;
  private @Autowired FolderManager fMger;
  private @Autowired MessageSource messageSource;

  private @Autowired IPermissionUtils permissions;
  private @Autowired List<ExportProcessor> exportProcessors;
  private @Autowired IPropertyHolder propertyHolder;

  @Autowired private HTMLStringGenerator htmlStringGenerator;

  @Override
  public EcatDocumentFile doExport(
      User ownerOfWork,
      Long[] exportIds,
      String[] exportNames,
      String[] exportTypes,
      ExportToFileConfig config,
      User exporter)
      throws IOException {
    if (isBlank(config.getExportName())) {
      config.setExportName(config.getExportFormat().name().toLowerCase() + "-export");
    }
    ExportOperationDetails details = createExportOperationDetails(config);
    // default type as StructuredDocument
    verifyInput(exportIds, exportTypes);
    List<File> tmpExportedFiles = new ArrayList<>();

    EcatDocumentFile exportResult = null;
    processDocsForDocExport(
        ownerOfWork, exportIds, exportTypes, config, exporter, details, tmpExportedFiles);
    FileProperty fp = generateFilePropertyForExportedFile(config, exporter, details);

    if (mergeFilesIntoFinalOutputFileAndSaveInFileStore(
        tmpExportedFiles, details.getConcatenatedExportFile(), fp, config)) {
      exportResult = addPdfToGallery(config, exporter, details, fp);
    } else {
      log.error("Couldn't generate EcatDocumentFile");
      removeTempFiles(details);
    }
    return exportResult;
  }

  public File doExportForSigning(
      User ownerOfWork,
      Long[] exportIds,
      String[] exportNames,
      String[] exportTypes,
      ExportToFileConfig config,
      User exporter)
      throws IOException {
    if (isBlank(config.getExportName())) {
      config.setExportName(config.getExportFormat().name().toLowerCase() + "-export");
    }
    ExportOperationDetails details = createExportOperationDetails(config);
    // default type as StructuredDocument
    verifyInput(exportIds, exportTypes);
    List<File> tmpExportedFiles = new ArrayList<>();
    processDocsForDocExport(
        ownerOfWork, exportIds, exportTypes, config, exporter, details, tmpExportedFiles);
    ExportProcessor processor = getExportProcessor(config);
    try {
      processor.concatenateExportedFilesIntoOne(
          details.getConcatenatedExportFile(), tmpExportedFiles, config);
    } catch (IOException io) {
      log.error("Couldn't generate PDF");
      removeTempFiles(details);
    }
    return details.getConcatenatedExportFile();
  }

  private EcatDocumentFile addPdfToGallery(
      ExportToFileConfig config, User exporter, ExportOperationDetails details, FileProperty fp) {

    EcatDocumentFile resultFile = null;
    Folder parent = recordManager.getGallerySubFolderForUser(Folder.EXPORTS_FOLDER_NAME, exporter);
    if (parent == null) {
      throw new IllegalStateException("Could not obtain the PDF export folder.");
    }
    try {
      resultFile =
          ecatDocumentFactory.generateEcatDocument(
              exporter,
              fp,
              fp.getFileCategory(),
              Folder.EXPORTS_FOLDER_NAME,
              config.getExportName() + "." + config.getExportFormat().getSuffix(),
              null);
      ConversionResult thumbnail = exportUtils.createThumbnailForExport(fp.getFileName(), exporter);
      if (thumbnail.isSuccessful()) {
        FileProperty exportThumbnail =
            fileStore.createAndSaveFileProperty(
                InternalFileStore.DOC_THUMBNAIL_CATEGORY,
                exporter,
                thumbnail.getConverted().getName(),
                new FileInputStream(thumbnail.getConverted()));
        resultFile.setDocThumbnailFP(exportThumbnail);
      }

      fMger.addChild(parent.getId(), resultFile, exporter);

      postExportSuccess(config, exporter, resultFile);
    } catch (Exception ex) {
      log.warn(ex.toString());
      postExportFailure(config, exporter, ex.getMessage());
    }
    removeTempFiles(details);
    return resultFile;
  }

  private boolean mergeFilesIntoFinalOutputFileAndSaveInFileStore(
      List<File> tmpExportedFiles,
      File finalExportFile,
      FileProperty fp,
      ExportToFileConfig config) {
    boolean fg = true;
    try {
      ExportProcessor processor = getExportProcessor(config);
      processor.concatenateExportedFilesIntoOne(
          finalExportFile, tmpExportedFiles, fileStore, fp, config);
      if (fp.getId() == null) {
        fg = false; // unsuccessful generation of fileProperty
      }
    } catch (Exception ex) {
      fg = false;
      log.error("Could not generate PDF content: " + ex.getMessage());
    }
    return fg;
  }

  private void postExportSuccess(
      ExportToFileConfig config, User u, EcatDocumentFile ecatDocumentFile) {
    publisher.publishEvent(new GenericEvent(u, ecatDocumentFile, AuditAction.CREATE));
    publisher.publishEvent(new GenericEvent(u, config, AuditAction.EXPORT));

    String escapedName = StringEscapeUtils.escapeHtml(config.getExportName());
    String galleryLink =
        String.format(
            "%s/gallery/item/%d", propertyHolder.getServerUrl(), ecatDocumentFile.getId());
    String msg =
        messageSource.getMessage(
            "workspace.export.msgSuccess", new String[] {escapedName, galleryLink}, null);
    commMgr.systemNotify(NotificationType.PROCESS_COMPLETED, msg, u.getUsername(), true);
  }

  private FileProperty generateFilePropertyForExportedFile(
      ExportToFileConfig config, User exporter, ExportOperationDetails details) {
    FileProperty fp = new FileProperty();
    fp.setFileCategory(config.getExportFormat().getSuffix());
    fp.setFileUser(exporter.getUsername());
    fp.setFileOwner(exporter.getUsername());
    fp.setFileGroup("research");
    fp.setFileVersion("v1");
    fp.setFileName(details.getConcatenatedExportFile().getName());
    return fp;
  }

  private void processDocsForDocExport(
      User ownerOfWork,
      Long[] exportIds,
      String[] exportTypes,
      ExportToFileConfig config,
      User exporter,
      ExportOperationDetails details,
      List<File> tmpExportedFiles) {
    for (int i = 0; i < exportIds.length; i++) {
      try {
        String tp1 = exportTypes[i];
        if (RecordType.isNotebookOrFolder(tp1)) {
          processFolderForExportToPdfWord(
              exportIds[i], tmpExportedFiles, ownerOfWork, config, details);
        } else if (RecordType.isDocumentOrTemplate(tp1)) {
          processRecordForExport(exportIds[i], tmpExportedFiles, exporter, config, details);
        } else {
          log.info("Error in doExport()");
          // what if it's none of these things? Should fail here
          // rather than at some unknown point in later code
        }
      } catch (ExportFailureException ex) {
        throw ex;
      } catch (Exception ex) {
        log.warn("Error on {} for id={}", ex.getMessage(), exportIds[i]);
      }
    }
  }

  private void removeTempFiles(ExportOperationDetails details) {
    File tempFolder = details.getTempExportFolder();
    if (tempFolder.exists()) {
      boolean delete = tempFolder.delete();
      if (!delete) {
        log.warn("Could not delete temporary Exports folder at " + details.getTempExportFolder());
      }
    }
  }

  ExportOperationDetails createExportOperationDetails(ExportToFileConfig config)
      throws IOException {
    File tempExportFolderRoot =
        exportUtils.createFolder(FileUtils.getTempDirectoryPath() + File.separator + PDF_ROOT);
    String uniqueFolderName = randomAlphabetic(10);
    File tempExportFolder = new File(tempExportFolderRoot, uniqueFolderName);
    exportUtils.createFolder(tempExportFolder.getAbsolutePath());

    String tstr = SecureStringUtils.getURLSafeSecureRandomString(8);
    String exportFileName =
        EscapeReplacement.replaceChars(config.getExportName())
            + "_"
            + tstr
            + "."
            + config.getExportFormat().getSuffix();
    File finalExportFile = new File(tempExportFolder, exportFileName);
    ExportOperationDetails details = new ExportOperationDetails(tempExportFolder, finalExportFile);
    return details;
  }

  private void postExportFailure(ExportToFileConfig config, User u, String detailMsg) {
    String escapedName = StringEscapeUtils.escapeHtml(config.getExportName());
    String msg =
        messageSource.getMessage(
            "workspace.export.msgFailure", new String[] {escapedName, detailMsg}, null);
    commMgr.systemNotify(NotificationType.PROCESS_COMPLETED, msg, u.getUsername(), true);
  }

  @Override
  public List<Long> getExportIDsForTagRetrievalFromFilesAndFolders(
      List<Long> recordIds, ExportToFileConfig exportConfig, User user) {
    List<Long> exportIDs = new ArrayList<>();
    for (Long recordID : recordIds) {
      if (recordDao.isRecord(recordID)) {
        exportIDs.add(recordID);
      } else {
        try {
          Folder fd1 = fMger.getFolder(recordID, user, SearchDepth.INFINITE);
          if (!permissions.isPermitted(fd1, PermissionType.READ, user)) {
            throw new AuthorizationException(
                "Unauthorized attempt by :"
                    + user.getUsername()
                    + " to export folder with id "
                    + fd1.getId());
          }
          exportIDs.add(recordID);
          getIdExtractorForFolder(exportConfig, user, fd1, true)
              .ifPresent(
                  idExtractor -> {
                    fd1.process(idExtractor);
                    // recordToExport here holds both record and folder ids (as idExtractor was
                    // created with 'includeFolderIds' option)
                    exportIDs.addAll(
                        idExtractor.getExportRecordList().getRecordsToExport().stream()
                            .map(oid -> oid.getDbId())
                            .collect(Collectors.toList()));
                  });
        } catch (AuthorizationException ex) {
          log.warn(ex.toString());
          throw new ExportFailureException(
              "Unauthorized attempt by :" + user.getUsername() + " to export  folder " + recordID);
        } catch (Exception ex) {
          log.warn(ex.toString());
          throw new ExportFailureException(ex.getMessage());
        }
      }
    }
    return exportIDs;
  }

  private void processFolderForExportToPdfWord(
      Long folderId,
      List<File> tempIndividualExportFiles,
      User user,
      ExportToFileConfig exportConfig,
      ExportOperationDetails exportOpDetails) {
    try {
      Folder fd1 = fMger.getFolder(folderId, user, SearchDepth.INFINITE);
      if (!permissions.isPermitted(fd1, PermissionType.READ, user)) {
        throw new AuthorizationException(
            "Unauthorized attempt by :"
                + user.getUsername()
                + " to export folder with id "
                + fd1.getId());
      }

      getIdExtractorForFolder(exportConfig, user, fd1, false)
          .ifPresent(
              idExtractor -> {
                fd1.process(idExtractor);
                List<GlobalIdentifier> rcdIds =
                    idExtractor.getExportRecordList().getRecordsToExport();
                for (GlobalIdentifier recordOid : rcdIds) {
                  processRecordForExport(
                      recordOid.getDbId(),
                      tempIndividualExportFiles,
                      user,
                      exportConfig,
                      exportOpDetails);
                }
              });
    } catch (AuthorizationException ex) {
      log.warn(ex.toString());
      throw new ExportFailureException(
          "Unauthorized attempt by :"
              + user.getUsername()
              + " to export folder with id "
              + folderId);
    } catch (Exception ex) {
      log.warn(ex.toString());
      throw new ExportFailureException(ex.getMessage());
    }
  }

  private void processRecordForExport(
      Long sid,
      List<File> tempExportFiles,
      User user,
      ExportToFileConfig exportConfig,
      ExportOperationDetails exportOpDetails) {

    try {
      exportConfig.setExporter(user);
      BaseRecord strucDocMaybe = recordManager.getRecordWithFields(sid, user);
      // in some cases this might be an EcatImage....
      if (strucDocMaybe == null || !strucDocMaybe.isStructuredDocument()) {
        log.warn(
            "{} of type {} is not an id for a notebook entry or document.. skipping",
            sid,
            (strucDocMaybe != null) ? strucDocMaybe.getType() : "null");
        return;
      }
      StructuredDocument strucDoc = strucDocMaybe.asStrucDoc();
      // fail if exportint
      if (!permissions.isPermitted(strucDoc, PermissionType.READ, user)) {
        throw new AuthorizationException(
            "Unauthorized attempt by :"
                + user.getUsername()
                + " to export record with id "
                + strucDoc.getOid());
      }
      log.info("exporting {}", strucDoc.getName());

      File tempExportFile =
          new File(
              exportOpDetails.getTempExportFolder(), createExportFileName(strucDoc, exportConfig));

      ExportProcesserInput exportInputHtml =
          htmlStringGenerator.extractHtmlStr(strucDoc, exportConfig);
      ExportProcessor processor = getExportProcessor(exportConfig);
      processor.makeExport(tempExportFile, exportInputHtml, strucDoc, exportConfig);
      tempExportFiles.add(tempExportFile);

    } catch (AuthorizationException ex) {
      log.warn(ex.toString());
      throw new ExportFailureException(
          "Unauthorized attempt by :" + user.getUsername() + " to export record with id " + sid);
    } catch (Exception ex) {
      log.warn(ex.toString());
      throw new ExportFailureException(
          "Exception when processing record with id [" + sid + "]: " + ex.getMessage());
    }
  }

  private String createExportFileName(StructuredDocument strucDoc, ExportToFileConfig config) {
    String strucDocName = strucDoc.getName();
    String strucDocNameEscaped = EscapeReplacement.replaceChars(strucDocName);
    int dty = counterx.getAndIncrement();
    return strucDocNameEscaped + Integer.toString(dty) + "." + config.getExportFormat().getSuffix();
  }

  /*
   * Package scoped for testing
   */
  Optional<PdfExportTreeTraversor> getIdExtractorForFolder(
      ExportToFileConfig exportConfig, User userToExport, Folder folder, boolean includeFolderIds) {

    boolean ownerOnly = true;
    // RSPAC-1411
    if (isExportExampleFromUserOrGroup(exportConfig, folder)
        || isTemplateUserOrGroup(exportConfig, folder)) {
      return Optional.empty();
    }
    if (folder != null && folder.isNotebook()) {
      ownerOnly = false; // when exporting the notebook include entries shared into it
    }

    // not owner for group export
    if (ExportScope.GROUP.equals(exportConfig.getExportScope())) {
      // assumes owner of root folder is that user, and that folder is root folder.
      return Optional.of(
          new PdfExportTreeTraversor(
              exportConfig,
              new RecordIdExtractor(false, includeFolderIds, ownerOnly, folder.getOwner())));
    }

    // In case, user has selected this folder and has reading permission,
    // but isn't owner, allow to export it.
    if (exportConfig.getExportScope().equals(ExportScope.SELECTION)
        && permissions.isPermitted(folder, PermissionType.READ, userToExport)
        && !folder.getOwner().equals(userToExport)) {
      ownerOnly = false;
    }
    return Optional.of(
        new PdfExportTreeTraversor(
            exportConfig, new RecordIdExtractor(false, includeFolderIds, ownerOnly, userToExport)));
  }

  private boolean isTemplateUserOrGroup(ExportToFileConfig exportConfig, Folder folder) {
    return folder.isSystemFolder()
        && !SELECTION.equals(exportConfig.getExportScope())
        && TEMPLATE_MEDIA_FOLDER_NAME.equals(folder.getName());
  }

  // RSPAC-1411
  private boolean isExportExampleFromUserOrGroup(ExportToFileConfig exportConfig, Folder folder) {
    return folder.isSystemFolder()
        && !SELECTION.equals(exportConfig.getExportScope())
        && EXPORTS_FOLDER_NAME.equals(folder.getName());
  }

  private ExportProcessor getExportProcessor(ExportToFileConfig expCfg) {
    for (ExportProcessor mgr : exportProcessors) {
      if (mgr.supportsFormat(expCfg.getExportFormat())) {
        return mgr;
      }
    }
    log.error("No known export processor for {}", expCfg.getExportFormat());
    return null;
  }
}
