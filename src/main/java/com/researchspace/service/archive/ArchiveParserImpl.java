package com.researchspace.service.archive;

import static com.researchspace.archive.ArchiveFileNameData.DOC_PREFIX;
import static com.researchspace.archive.ArchiveFileNameData.MEDIA_PREFIX;
import static com.researchspace.core.util.XMLReadWriteUtils.fromXML;
import static com.researchspace.service.archive.ExportImport.SCHEMAS_FOLDER;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.researchspace.archive.AbstractArchivalParserRef;
import com.researchspace.archive.ArchivalDocument;
import com.researchspace.archive.ArchivalDocumentParserRef;
import com.researchspace.archive.ArchivalFileNotExistException;
import com.researchspace.archive.ArchivalForm;
import com.researchspace.archive.ArchivalGalleryMetaDataParserRef;
import com.researchspace.archive.ArchivalGalleryMetadata;
import com.researchspace.archive.ArchivalImportConfig;
import com.researchspace.archive.ArchiveManifest;
import com.researchspace.archive.ArchiveModel;
import com.researchspace.archive.ArchiveUtils;
import com.researchspace.archive.ExportRecordList;
import com.researchspace.archive.IArchiveModel;
import com.researchspace.archive.model.ArchiveUsers;
import com.researchspace.core.util.XMLReadWriteUtils;
import com.researchspace.core.util.ZipUtils;
import com.researchspace.core.util.version.SemanticVersion;
import com.researchspace.core.util.version.Versionable;
import com.researchspace.dao.ArchiveDao;
import com.researchspace.model.ArchivalCheckSum;
import com.researchspace.model.Version;
import com.researchspace.service.RSMetaDataManager;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import javax.xml.bind.JAXBException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.FalseFileFilter;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.xml.sax.SAXException;

/** Parses archive files. */
@Slf4j
public class ArchiveParserImpl implements IArchiveParser {

  private static final String userSchemaId = "users";
  private static final String docSchemaId = "doc";
  private static final String formSchemaId = "form";
  private static final String folderSchemaId = "recordList";

  private @Autowired ArchiveDao archiveDao;
  private @Autowired RSMetaDataManager metadataMgr;

  @Qualifier("propertyHolder")
  @Autowired
  private Versionable rspaceVersion;

  @SneakyThrows
  public IArchiveModel loadArchive(
      File zipFile, ImportArchiveReport report, ArchivalImportConfig iconfig) {
    // Check whether it's a zip file that unzips fine
    boolean isZip = isZipFile(zipFile);
    File zipFolder = null;
    if (isZip) {
      try {
        zipFolder = extractZipFolder(zipFile, new File(iconfig.getUnzipPath()));
      } catch (IOException e) {
        log.warn("Cannot unzip provided archive file, skipping further checks");
      }
    }
    if (!isZip || zipFolder == null) {
      report.setValidationResult(ImportValidationRule.ZIP_FILE_CAN_UNZIP, false);
      return null;
    }
    report.setExpandedZipFolderPath(zipFolder.getAbsolutePath());
    report.setValidationResult(ImportValidationRule.ZIP_FILE_CAN_UNZIP, true);

    // Calculate checksum
    long checksum = 0L;
    String zipContentsChecksum = "";
    try {
      checksum = calculateChecksum(zipFile);
      zipContentsChecksum = calculateZipContentsChecksum(zipFolder);

      report.setValidationResult(ImportValidationRule.CHECKSUM_CALCULATED, true);
    } catch (IOException e) {
      report.setValidationResult(ImportValidationRule.CHECKSUM_CALCULATED, false);
    }
    File schemasFolder = new File(zipFolder, SCHEMAS_FOLDER);
    // created for .eln archives
    if (schemasFolder.exists()) {
      Collection<File> schemas = FileUtils.listFiles(schemasFolder, null, true);
      for (File target : schemas) {
        File moveDest = new File(zipFolder, target.getName());
        Files.move(target.toPath(), moveDest.toPath(), REPLACE_EXISTING);
      }
    }
    // Parse unzipped files
    IArchiveModel rc = parse(zipFolder, report);

    // Check manifest
    try {
      ArchiveManifest manifest = rc.getManifest();
      // check checksum
      if (manifest.isRSpaceSource()) {
        verifyArchiveChecksum(report, manifest, checksum, zipContentsChecksum);
        verifyVersionCompatibilities(report, manifest);
      }
    } catch (IOException e) {
      report.setValidationResult(ImportValidationRule.MANIFEST_FILE_PRESENT, false);
    }

    // may not exist if was selection export
    if (rc.getUserInfo() != null && rc.getUserInfo().exists()) {
      checkUserSchemaOK(report, rc);
    }

    checkDocumentSchemaVersionOK(report, rc);
    checkFormSchemaVersionOK(report, rc);
    checkFolderTreeSchemaOK(report, rc);
    report.setValidationComplete(true);
    return rc;
  }

  private void verifyVersionCompatibilities(ImportArchiveReport report, ArchiveManifest manifest) {
    // only consider major and minor versions; qualifiers shouldn't affect archive compatibilities
    SemanticVersion manifestMajMin =
        new SemanticVersion(
            manifest.getRSpaceAppVersion().getMajor(),
            manifest.getRSpaceAppVersion().getMinor(),
            null,
            null);
    SemanticVersion appMajMin =
        new SemanticVersion(
            getRSpaceVersion().getMajor(), getRSpaceVersion().getMinor(), null, null);
    if (manifestMajMin.isNewerThan(appMajMin)) {
      report.setValidationResult(ImportValidationRule.ARCHIVE_NOT_TOO_NEW, false);
    }
  }

  protected SemanticVersion getRSpaceVersion() {
    return rspaceVersion.getVersion();
  }

  private void checkUserSchemaOK(ImportArchiveReport report, IArchiveModel rc) {
    try {
      ArchiveUsers fromXml =
          XMLReadWriteUtils.fromXML(rc.getUserInfo(), ArchiveUsers.class, null, null);
      if (!metadataMgr.isArchiveImportable(userSchemaId, new Version(fromXml.getSchemaVersion()))) {
        report.setValidationResult(ImportValidationRule.USER_SCHEMA_VERSION_RANGE_OK, false);
      } else {
        report.setValidationResult(ImportValidationRule.USER_SCHEMA_VERSION_RANGE_OK, true);
      }
      report.setValidationResult(ImportValidationRule.USER_FILE_READABLE, true);
    } catch (JAXBException e) {
      report.setValidationResult(ImportValidationRule.USER_FILE_READABLE, false);
    } catch (FileNotFoundException | SAXException e) {
      throw new ImportFailureException(e);
    }
  }

  private void checkFolderTreeSchemaOK(ImportArchiveReport report, IArchiveModel rc) {
    if (rc.getFolderTree() == null) {
      return;
    }
    try {
      ExportRecordList fromXml =
          XMLReadWriteUtils.fromXML(rc.getFolderTree(), ExportRecordList.class, null, null);
      if (!metadataMgr.isArchiveImportable(
          folderSchemaId, new Version(fromXml.getSchemaVersion()))) {
        report.setValidationResult(ImportValidationRule.FOLDER_SCHEMA_VERSION_RANGE_OK, false);
      } else {
        report.setValidationResult(ImportValidationRule.FOLDER_SCHEMA_VERSION_RANGE_OK, true);
      }
      report.setValidationResult(ImportValidationRule.FOLDER_FILE_READABLE, true);
    } catch (JAXBException e) {
      report.setValidationResult(ImportValidationRule.FOLDER_FILE_READABLE, false);
    } catch (FileNotFoundException | SAXException e) {
      throw new ImportFailureException(e);
    }
  }

  private void checkDocumentSchemaVersionOK(
      ImportArchiveReport report, IArchiveModel archiveModel) {
    boolean ok = true;
    for (ArchivalDocumentParserRef ref : archiveModel.getCurrentVersions()) {
      if (ref.getArchivalDocument() == null) {
        report.getErrorList().addErrorMsg("Missing XML  file for document: " + ref.getName());
        report.setValidationResult(ImportValidationRule.GENERAL_ARCHIVE_STRUCTURE, false);
        ok = false;
        break;
      }
      if (!metadataMgr.isArchiveImportable(
          docSchemaId, new Version(ref.getArchivalDocument().getSchemaVersion()))) {
        report.setValidationResult(ImportValidationRule.DOC_SCHEMA_VERSION_RANGE_OK, false);
        ok = false;
        break;
      }
    }
    if (ok) {
      report.setValidationResult(ImportValidationRule.DOC_SCHEMA_VERSION_RANGE_OK, true);
    }
  }

  private void checkFormSchemaVersionOK(ImportArchiveReport report, IArchiveModel rc) {
    boolean ok = true;
    for (ArchivalDocumentParserRef ref : rc.getCurrentVersions()) {
      if (ref.getArchivalForm() == null) {
        report.getErrorList().addErrorMsg("Missing XML file for form: " + ref.getName());
        report.setValidationResult(ImportValidationRule.GENERAL_ARCHIVE_STRUCTURE, false);
        ok = false;
        break;
      }
      if (!metadataMgr.isArchiveImportable(
          formSchemaId, new Version(ref.getArchivalForm().getSchemaVersion()))) {
        report.setValidationResult(ImportValidationRule.FORM_SCHEMA_VERSION_RANGE_OK, false);
        ok = false;
        break;
      }
    }
    if (ok) {
      report.setValidationResult(ImportValidationRule.DOC_SCHEMA_VERSION_RANGE_OK, true);
    }
  }

  protected boolean isZipFile(File zipFile) {
    return zipFile.exists() && FilenameUtils.getExtension(zipFile.getAbsolutePath()).equals("zip")
        || FilenameUtils.getExtension(zipFile.getAbsolutePath()).equals("eln");
  }

  protected File extractZipFolder(File zipFile, File outFolder) throws IOException {
    log.info("Extracting {} into {}", zipFile.getAbsolutePath(), outFolder.getAbsolutePath());
    return new File(ZipUtils.extractZip(zipFile.getAbsolutePath(), outFolder.getAbsolutePath()));
  }

  private void verifyArchiveChecksum(
      ImportArchiveReport report,
      ArchiveManifest manifest,
      long checksum,
      String zipContentsChecksum) {
    String id = manifest.getArchiveId();
    Optional<ArchivalCheckSum> dbCsum = getCheckSum(id);
    // possibly we're importing from another RSpace, in which case we don't know about this checksum
    if (dbCsum.isPresent()) {
      if (checksum == dbCsum.get().getCheckSum()) {
        // Zip file checksum matches
        report.setValidationResult(ImportValidationRule.CHECKSUM_MATCHES, true);
      } else {
        // If archive contents checksum isn't available, we cannot check if the contents are changed
        // or not, so
        // CHECKSUM_MATCHES remains UNTESTED and the import goes through as expected.
        if (dbCsum.get().getZipContentCheckSum() != null
            && !dbCsum.get().getZipContentCheckSum().isEmpty()) {
          // Archive contents checksum is available on the server, so it must match the one
          // calculated now
          report.setValidationResult(
              ImportValidationRule.CHECKSUM_MATCHES,
              dbCsum.get().getZipContentCheckSum().equals(zipContentsChecksum));
        }
      }
    } else {
      log.info("No checksum retrieved - possibly importing from another RSpace?");
    }
  }

  /**
   * @param rootFolder The root folder of an expanded archive folder
   * @throws ImportFailureException which wraps underlying exceptions
   */
  public IArchiveModel parse(File rootFolder, ImportArchiveReport report) {
    // checks content
    ArchiveModel archiveModel = initWorkDir(rootFolder, report);

    // Map from document id to all parser refs with that document id (contains all revisions)
    Multimap<Long, ArchivalDocumentParserRef> documentIdToParserRefs = HashMultimap.create();

    // Iterates over all the folders in the archive.
    // Problem - this parses the whole archive into memory, this will break for large archives.
    for (File recordFolder : getRecordFolders(rootFolder)) {
      String folderName = recordFolder.getName();
      Collection<File> recordFiles = getRecordFiles(recordFolder);

      if (recordFiles.isEmpty()) {
        log.warn("Folder " + folderName + " did not contain any record data, skipping");
        continue;
      }

      AbstractArchivalParserRef parserRef =
          generateParserRef(report, archiveModel, folderName, recordFiles);
      if (parserRef == null) {
        log.warn(String.format("generateParserRef returned null for folder [%s]", recordFolder));
        continue;
      }
      parserRef.setName(folderName);
      parserRef.setPath(recordFolder);

      // If this is a document, add to documentIdToParserRefs
      if (parserRef.isDocument()) {
        ArchivalDocumentParserRef archivalDocumentParserRef = (ArchivalDocumentParserRef) parserRef;

        if (archivalDocumentParserRef.getArchivalDocument() != null) {
          documentIdToParserRefs.put(
              archivalDocumentParserRef.getArchivalDocument().getDocId(),
              archivalDocumentParserRef);
        } else {
          String errorMessage =
              String.format("%s folder does not contain a valid document.", folderName);
          report.getErrorList().addErrorMsg(errorMessage);
          throw new ImportFailureException(new NullPointerException(errorMessage));
        }
      }
    }

    report.setValidationResult(ImportValidationRule.XMLSCHEMA, true);

    // Add the latest revision to archiveModel's currentRevisions for every document id
    for (Collection<ArchivalDocumentParserRef> documentParserRefs :
        documentIdToParserRefs.asMap().values()) {
      ArchivalDocumentParserRef latestParserRef = null;
      for (ArchivalDocumentParserRef documentParserRef : documentParserRefs) {
        if (latestParserRef == null
            || documentParserRef.getRevision() > latestParserRef.getRevision()) {
          latestParserRef = documentParserRef;
        }
      }
      if (latestParserRef != null) {
        if (documentParserRefs.size() > 1) {
          report
              .getInfoList()
              .addErrorMsg(
                  String.format(
                      "Only the latest version of %s was imported.",
                      latestParserRef.getArchivalDocument().getName()));
        }
        archiveModel.addToCurrentRevisions(latestParserRef);
      }
    }

    return archiveModel;
  }

  private AbstractArchivalParserRef generateParserRef(
      ImportArchiveReport report,
      ArchiveModel archiveModel,
      String folderName,
      Collection<File> filesInRecordFolder) {
    if (isDocumentFolder(folderName)) {
      ArchivalDocumentParserRef parserRef = new ArchivalDocumentParserRef();
      try {
        for (File fileInRecordFolder : filesInRecordFolder) {
          if (!fileInRecordFolder.exists()) {
            continue;
          }
          if (fileInRecordFolder.getName().equals(folderName + ".xml")) {
            ArchivalDocument ard =
                unmarshalDocumentXmlFile(
                    fileInRecordFolder,
                    archiveModel.getXmlSchema(),
                    new XMLImportSchemaValidator(report));
            parserRef.setArchivalDocument(ard);
            parserRef.setDocumentFileName(fileInRecordFolder.getName());
          } else if (fileInRecordFolder.getName().equals(folderName + "_form.xml")) {
            ArchivalForm af = unmarshalFormFile(fileInRecordFolder, archiveModel.getFormScheme());
            parserRef.setArchivalForm(af);
          } else
            // it's a resource file - image , attachment etc
            parserRef.addFile(fileInRecordFolder);
        }
      } catch (Exception ex) {
        log.error(
            String.format("Error when parsing content of XML archive folder [%s]", folderName));
        report.setValidationResult(ImportValidationRule.XMLSCHEMA, false);
        throw new ImportFailureException(ex);
      }
      archiveModel.addToAll(parserRef);
      return parserRef;
    } else if (isMediaFolder(folderName)) {
      ArchivalGalleryMetaDataParserRef parserRef = new ArchivalGalleryMetaDataParserRef();
      try {
        for (File fileInRecordFolder : filesInRecordFolder) {
          if (fileInRecordFolder.getName().equals(folderName + ".xml")) {
            ArchivalGalleryMetadata galleryXML =
                unmarshalMediaXmlFile(
                    fileInRecordFolder,
                    archiveModel.getXmlSchema(),
                    new XMLImportSchemaValidator(report));
            parserRef.setGalleryXML(galleryXML);
            parserRef.setDocumentFileName(fileInRecordFolder.getName());

          } else {
            parserRef.addFile(fileInRecordFolder);
          }
        }
      } catch (Exception ex) {
        log.error("Error parsing XML archives");
        report.setValidationResult(ImportValidationRule.XMLSCHEMA, false);
        throw new ImportFailureException(ex);
      }
      archiveModel.addMediaDoc(parserRef);
      return parserRef;
    }
    return null;
  }

  private boolean isDocumentFolder(String folderName) {
    return folderName.startsWith(DOC_PREFIX);
  }

  private boolean isMediaFolder(String folderName) {
    return folderName.startsWith(MEDIA_PREFIX);
  }

  protected ArchiveModel initWorkDir(File rootFolder, ImportArchiveReport report)
      throws ArchivalFileNotExistException {
    ArchiveModel archive = new ArchiveModel();

    File formScheme = new File(rootFolder, ExportImport.ZIP_FORM_SCHEMA);
    File manifest = new File(rootFolder, ExportImport.EXPORT_MANIFEST);
    File xmlScheme = new File(rootFolder, ExportImport.ZIP_SCHEMA);
    File linkResolver = new File(rootFolder, ExportImport.ZIP_LINK_SOLVER);
    File folderTree = new File(rootFolder, ExportImport.FOLDER_TREE);
    File userInfo = new File(rootFolder, ExportImport.USERS);

    if (!formScheme.exists()) {
      throw new ArchivalFileNotExistException(formScheme.getName());
    }
    if (manifest.exists()) {
      report.setValidationResult(ImportValidationRule.MANIFEST_FILE_PRESENT, true);
    }
    if (folderTree.exists()) {
      report.setValidationResult(ImportValidationRule.FOLDER_TREE_PRESENT, true);
    }
    if (!xmlScheme.exists()) {
      throw new ArchivalFileNotExistException(xmlScheme.getName());
    }
    archive.setFormSchema(formScheme);
    archive.setXmlSchema(xmlScheme);
    archive.setManifestFile(manifest);
    archive.setLinkResolver(linkResolver);
    archive.setFolderTree(folderTree);
    archive.setUserInfo(userInfo);

    return archive;
  }

  private Collection<File> getRecordFolders(File rootFolder) {
    Collection<File> files =
        FileUtils.listFilesAndDirs(
            rootFolder,
            FalseFileFilter.FALSE,
            FileFilterUtils.or(new WildcardFileFilter("doc*"), new WildcardFileFilter("media*")));
    files.remove(rootFolder);
    return files;
  }

  private Collection<File> getRecordFiles(File recordFolder) {
    File[] recordFiles = recordFolder.listFiles();
    if (recordFiles == null) {
      log.warn(
          String.format(
              "listFiles returned null, either no files in a record folder [%s] or I/O error",
              recordFolder.getName()));
      return new ArrayList<>();
    }
    return Arrays.asList(recordFiles);
  }

  private ArchivalDocument unmarshalDocumentXmlFile(
      File xmlFile, File xsdFile, XMLImportSchemaValidator xmlSchemaValidator)
      throws JAXBException {
    try {
      return fromXML(xmlFile, ArchivalDocument.class, xsdFile, xmlSchemaValidator);
    } catch (FileNotFoundException | SAXException e) {
      throw new ImportFailureException(e);
    }
  }

  private ArchivalGalleryMetadata unmarshalMediaXmlFile(
      File xmlFile, File xsdFile, XMLImportSchemaValidator xmlSchemaValidator)
      throws JAXBException {
    try {
      return fromXML(xmlFile, ArchivalGalleryMetadata.class, xsdFile, xmlSchemaValidator);
    } catch (FileNotFoundException | SAXException e) {
      throw new ImportFailureException(e);
    }
  }

  private ArchivalForm unmarshalFormFile(File xmlFile, File xsdFile) throws Exception {
    return fromXML(xmlFile, ArchivalForm.class, xsdFile, null);
  }

  protected Optional<ArchivalCheckSum> getCheckSum(String uid) {
    return archiveDao.getSafeNull(uid);
  }

  // For testing
  protected long calculateChecksum(File zipFile) throws IOException {
    return ArchiveUtils.calculateChecksum(zipFile);
  }

  // For testing
  protected String calculateZipContentsChecksum(File zipFolder) throws IOException {
    return ArchiveUtils.calculateFolderContentsChecksum(zipFolder);
  }
}
