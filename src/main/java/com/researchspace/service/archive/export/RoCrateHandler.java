package com.researchspace.service.archive.export;

import static com.researchspace.service.archive.ExportImport.EXPORT_MANIFEST;
import static com.researchspace.service.archive.ExportImport.FOLDER_TREE;
import static com.researchspace.service.archive.ExportImport.MESSAGES;
import static com.researchspace.service.archive.ExportImport.MESSAGES_SCHEMA;
import static com.researchspace.service.archive.ExportImport.NFS_EXPORT_XML;
import static com.researchspace.service.archive.ExportImport.RESOURCES;
import static com.researchspace.service.archive.ExportImport.USERS;
import static com.researchspace.service.archive.ExportImport.USERS_SCHEMA;
import static com.researchspace.service.archive.ExportImport.ZIP_FORM_SCHEMA;
import static com.researchspace.service.archive.ExportImport.ZIP_LINK_SOLVER;
import static com.researchspace.service.archive.ExportImport.ZIP_SCHEMA;
import static com.researchspace.service.archive.export.NfsExportContext.FILESTORE_FILES_ARCHIVE_DIR;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import com.researchspace.archive.ArchivalField;
import com.researchspace.archive.ArchivalGalleryMetadata;
import com.researchspace.archive.ArchivalNfsFile;
import com.researchspace.archive.ArchiveFolder;
import com.researchspace.archive.ArchiveManifest;
import com.researchspace.archive.model.IArchiveExportConfig;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.archive.ExportImport;
import edu.kit.datamanager.ro_crate.RoCrate;
import edu.kit.datamanager.ro_crate.entities.contextual.ContextualEntity;
import edu.kit.datamanager.ro_crate.entities.contextual.OrganizationEntity;
import edu.kit.datamanager.ro_crate.entities.contextual.PersonEntity;
import edu.kit.datamanager.ro_crate.entities.data.DataEntity;
import edu.kit.datamanager.ro_crate.entities.data.DataSetEntity;
import edu.kit.datamanager.ro_crate.entities.data.FileEntity;
import edu.kit.datamanager.ro_crate.entities.data.RootDataEntity;
import edu.kit.datamanager.ro_crate.externalproviders.personprovider.OrcidProvider;
import edu.kit.datamanager.ro_crate.writer.FolderWriter;
import edu.kit.datamanager.ro_crate.writer.RoCrateWriter;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.crypto.hash.Sha256Hash;

public class RoCrateHandler {

  private static final String DESCRIPTION = "description";
  private static final String SCHEMAS_DIR = "./schemas/";
  private static final String APPLICATION_XML = "application/xml";
  private static final String IDENTIFIER = "identifier";
  private static final String SHA_256 = "sha256";
  private static final String FILE_LINK_TEXT =
      "A file linked by an exported document or in a user Gallery";
  private final RoCrate roCrate;
  private final DataSetEntity.DataSetBuilder dsb;

  public RoCrateHandler(RoCrate roCrate, DataSetEntity.DataSetBuilder dsb) {
    this.roCrate = roCrate;
    this.dsb = dsb;
  }

  public static RoCrate startProcessRoCrate(
      List<RoCrateLogicalFolder> topLevelFolders,
      List<ArchiveFolder> folderTree,
      IArchiveExportConfig aconfig) {
    RoCrate.RoCrateBuilder roCrateBuilder = new RoCrate.RoCrateBuilder("name", DESCRIPTION);
    RoCrate roCrate = roCrateBuilder.build();
    for (ArchiveFolder af : folderTree) {
      boolean isGroupExport = aconfig.isGroupScope();
      DataSetEntity.DataSetBuilder topLevelFolder = new DataSetEntity.DataSetBuilder();
      if (isGroupExport) {
        topLevelFolder.setId("./" + af.getOwner().getUniqueName() + "/" + af.getName());
      } else {
        topLevelFolder.setId("./" + af.getName());
      }
      topLevelFolder.addProperty(
          DESCRIPTION,
          "Top level "
              + af.getType()
              + " containing child datasets. Represents a "
              + af.getType()
              + " in RSpace but is not present as"
              + " a physical file on export");
      topLevelFolder.addProperty("additionalType", af.getType());
      DataSetEntity dsTop = topLevelFolder.build();
      RoCrateLogicalFolder roCrateLogicalFolder = new RoCrateLogicalFolder(dsTop);
      roCrateLogicalFolder.setRspaceGlobalID(af.getGlobalIdentifier());
      roCrateLogicalFolder.setRspaceID(af.getId());
      roCrateLogicalFolder.setRspaceParentID(af.getParentId());
      topLevelFolders.add(roCrateLogicalFolder);
      roCrate.addDataEntity(dsTop, true);
    }
    // set ids on nested toplevel folders
    for (RoCrateLogicalFolder roCrateLogicalFolder : topLevelFolders) {
      for (RoCrateLogicalFolder roCrateLogicalFolderCompared : topLevelFolders) {
        roCrateLogicalFolder
            .getRspaceParentID()
            .ifPresent(
                rspaceParentID -> {
                  if (rspaceParentID.equals(roCrateLogicalFolderCompared.getRspaceID())) {
                    roCrateLogicalFolderCompared.addToHasPart(roCrateLogicalFolder.getId());
                    roCrateLogicalFolder.isPartOf(roCrateLogicalFolderCompared.getId());
                  }
                });
      }
    }
    return roCrate;
  }

  /**
   * There should be no top level file other than ro-crate-metadata.json for the ELn Consortium
   * spec.
   */
  @SneakyThrows
  private static void moveFileIntoSchemasFolderForElnArchives(
      ExportContext exportContext, File schemasFolder, String target) {
    File targetFile = new File(exportContext.getArchiveAssmblyFlder(), target);
    File movedZipSchema = new File(schemasFolder, target);
    if (targetFile.exists()) {
      Files.move(targetFile.toPath(), movedZipSchema.toPath(), REPLACE_EXISTING);
    }
  }

  @SneakyThrows
  private static void moveAllFilesIntoSchemasFolderForElnArchives(
      ExportContext exportContext,
      File schemasFolder,
      IArchiveExportConfig aconfig,
      List<ExportedRecord> archived) {
    moveFileIntoSchemasFolderForElnArchives(exportContext, schemasFolder, ZIP_SCHEMA);
    moveFileIntoSchemasFolderForElnArchives(exportContext, schemasFolder, ZIP_FORM_SCHEMA);
    moveFileIntoSchemasFolderForElnArchives(exportContext, schemasFolder, EXPORT_MANIFEST);
    if (thereWereNfsExports(archived)) {
      moveFileIntoSchemasFolderForElnArchives(exportContext, schemasFolder, NFS_EXPORT_XML);
    }
    moveFileIntoSchemasFolderForElnArchives(exportContext, schemasFolder, ZIP_LINK_SOLVER);
    moveFileIntoSchemasFolderForElnArchives(exportContext, schemasFolder, FOLDER_TREE);
    if (aconfig.isUserScope() || aconfig.isGroupScope()) {
      moveFileIntoSchemasFolderForElnArchives(exportContext, schemasFolder, USERS);
      moveFileIntoSchemasFolderForElnArchives(exportContext, schemasFolder, USERS_SCHEMA);
      moveFileIntoSchemasFolderForElnArchives(exportContext, schemasFolder, MESSAGES);
      moveFileIntoSchemasFolderForElnArchives(exportContext, schemasFolder, MESSAGES_SCHEMA);
      moveFileIntoSchemasFolderForElnArchives(exportContext, schemasFolder, MESSAGES);
    }
  }

  @SneakyThrows
  public static void finishProcessRoCrate(
      ArchiveManifest manifest,
      IArchiveExportConfig aconfig,
      ExportContext exportContext,
      RoCrate roCrate,
      List<ExportedRecord> archived) {
    if (roCrate != null) {
      File schemasFolder = makeSchemasFolder(aconfig, exportContext, roCrate, archived);
      String orcidID = manifest.getKeyToValue().get("OrcidID");
      PersonEntity orcidDetails = null;
      if (orcidID != null) {
        orcidDetails =
            OrcidProvider.getPerson("https://orcid.org/" + manifest.getKeyToValue().get("OrcidID"));
      }
      PersonEntity.PersonEntityBuilder exporterBuilder =
          new PersonEntity.PersonEntityBuilder()
              .setEmail(aconfig.getExporter().getEmail())
              .setGivenName(aconfig.getExporter().getFirstName())
              .setFamilyName(aconfig.getExporter().getLastName())
              .addProperty("text", "The RSpace user who exported this data");
      if (orcidID != null) {
        exporterBuilder.setId(orcidDetails.getId());
        exporterBuilder.addProperty(
            "alternateName",
            orcidDetails.getProperty("givenName").asText()
                + " "
                + orcidDetails.getProperty("familyName").asText());
      } else {
        exporterBuilder.setId(manifest.getKeyToValue().get("Exported by"));
      }
      PersonEntity exported = exporterBuilder.build();
      roCrate.addContextualEntity(exported);
      OrganizationEntity rSpace =
          new OrganizationEntity.OrganizationEntityBuilder()
              .setId("#RSpace")
              .setEmail("info@researchspace.com")
              .addProperty("name", "RSpace")
              .addProperty("url", "https://www.researchspace.com/")
              .build();
      roCrate.addContextualEntity(rSpace);
      FileEntity.FileEntityBuilder docSchema = new FileEntity.FileEntityBuilder();
      docSchema.setId(SCHEMAS_DIR + ZIP_SCHEMA);
      docSchema.setEncodingFormat(APPLICATION_XML);
      docSchema.addProperty("text", "schema for RSpace documents");
      addSha256Hash(docSchema, new File(schemasFolder, ZIP_SCHEMA));
      roCrate.addDataEntity(docSchema.build(), true);
      FileEntity.FileEntityBuilder formSchema = new FileEntity.FileEntityBuilder();
      formSchema.setId(SCHEMAS_DIR + ZIP_FORM_SCHEMA);
      formSchema.addProperty("text", "schema for RSpace forms");
      formSchema.setEncodingFormat(APPLICATION_XML);
      addSha256Hash(formSchema, new File(schemasFolder, ZIP_FORM_SCHEMA));
      roCrate.addDataEntity(formSchema.build(), true);
      DataSetEntity.DataSetBuilder resources = new DataSetEntity.DataSetBuilder();
      resources.setId("./" + RESOURCES);
      resources.addProperty("text", "Common resources shared among exported data");
      roCrate.addDataEntity(resources.build(), true);
      if (aconfig.isUserScope() || aconfig.isGroupScope()) {
        FileEntity.FileEntityBuilder userSchema = new FileEntity.FileEntityBuilder();
        userSchema.setId(SCHEMAS_DIR + USERS_SCHEMA);
        userSchema.addProperty("text", "schema for RSpace Users and Groups");
        userSchema.setEncodingFormat(APPLICATION_XML);
        roCrate.addDataEntity(userSchema.build(), true);
        addSha256Hash(userSchema, new File(schemasFolder, USERS_SCHEMA));
        FileEntity.FileEntityBuilder userXml = new FileEntity.FileEntityBuilder();
        userXml.setId(SCHEMAS_DIR + USERS);
        userXml.addProperty("text", "Data on exported users and Groups");
        addSha256Hash(userXml, new File(schemasFolder, USERS));
        userXml.setEncodingFormat(APPLICATION_XML);
        roCrate.addDataEntity(userXml.build(), true);
        FileEntity.FileEntityBuilder messagesSchema = new FileEntity.FileEntityBuilder();
        messagesSchema.setId(SCHEMAS_DIR + MESSAGES_SCHEMA);
        messagesSchema.addProperty("text", "schema for RSpace Messages");
        addSha256Hash(messagesSchema, new File(schemasFolder, MESSAGES_SCHEMA));
        messagesSchema.setEncodingFormat(APPLICATION_XML);
        roCrate.addDataEntity(messagesSchema.build(), true);
        FileEntity.FileEntityBuilder messagesXML = new FileEntity.FileEntityBuilder();
        messagesXML.setId(SCHEMAS_DIR + MESSAGES);
        addSha256Hash(messagesXML, new File(schemasFolder, MESSAGES));
        messagesXML.addProperty("text", "Data on the messages of exported users");
        userXml.setEncodingFormat(APPLICATION_XML);
        roCrate.addDataEntity(messagesXML.build(), true);
      }
      if (thereWereNfsExports(archived)) {
        FileEntity.FileEntityBuilder messagesSchema = new FileEntity.FileEntityBuilder();
        messagesSchema.setId(SCHEMAS_DIR + NFS_EXPORT_XML);
        messagesSchema.addProperty("text", "Data on remote Nfs files linked in the export");
        messagesSchema.setEncodingFormat(APPLICATION_XML);
        addSha256Hash(messagesSchema, new File(schemasFolder, NFS_EXPORT_XML));
        roCrate.addDataEntity(messagesSchema.build(), true);
      }
      if (thereWereNfsExportedExports(archived)) {
        DataSetEntity.DataSetBuilder exportedNFS = new DataSetEntity.DataSetBuilder();
        exportedNFS.setId("./" + FILESTORE_FILES_ARCHIVE_DIR);
        exportedNFS.addProperty("text", "Contains remote nfs files linked by exported documents");
        roCrate.addDataEntity(exportedNFS.build(), true);
      }
      // TODO - add a license when we go opensource??
      RootDataEntity rde = roCrate.getRootDataEntity();
      // Recommended to be a DOI - change this to be a DOI if we ever have one for RSpace instances
      rde.addProperty(IDENTIFIER, aconfig.getArchivalMeta().getSource());
      rde.addProperty("datePublished", manifest.getKeyToValue().get("exportDate"));
      DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss");
      LocalTime localTime = LocalTime.now();
      String exportTime = dtf.format(localTime);
      rde.addProperty(
          "name",
          manifest.getKeyToValue().get("Exported by")
              + "_"
              + manifest.getKeyToValue().get("exportDate")
              + "_"
              + exportTime);
      rde.addProperty(DESCRIPTION, aconfig.getDescription());
      RoCrateWriter folderRoCrateWriter = new RoCrateWriter(new FolderWriter());
      folderRoCrateWriter.save(roCrate, exportContext.getArchiveAssmblyFlder().getPath());
      replaceSingleKeyWordsInRoCrate(exportContext);
    }
  }

  private static File makeSchemasFolder(
      IArchiveExportConfig aconfig,
      ExportContext exportContext,
      RoCrate roCrate,
      List<ExportedRecord> archived)
      throws IOException {
    File schemasFolder =
        new File(exportContext.getArchiveAssmblyFlder(), ExportImport.SCHEMAS_FOLDER);
    FileUtils.forceMkdir(
        new File(exportContext.getArchiveAssmblyFlder(), ExportImport.SCHEMAS_FOLDER));
    moveAllFilesIntoSchemasFolderForElnArchives(exportContext, schemasFolder, aconfig, archived);
    roCrate.setJsonDescriptor(
        new ContextualEntity.ContextualEntityBuilder()
            .setId("ro-crate-metadata.json")
            .addType("CreativeWork")
            .addIdProperty("about", "./")
            .addIdProperty("conformsTo", "https://w3id.org/ro/crate/1.1")
            .addIdProperty("sdPublisher", "#RSpace")
            .build());
    return schemasFolder;
  }

  /**
   * RoCrate Java library converts lists of string properties into simple strings if there is only
   * one in the list EG - keywords: ["red"] - gets written to ro-crate-metadata.json file as
   * keywords: "red". As this is no longer a list of strings, it breaks the ELNConsortium's
   * interpretation of the spec for keywords. We therefore inspect ro-crate-metadata.json and if any
   * lines of text are found with a keywords property not enclosed in an array we re-write
   * ro-crate-metadata.json with the correct value.
   *
   * @param exportContext
   * @throws IOException
   */
  private static void replaceSingleKeyWordsInRoCrate(ExportContext exportContext)
      throws IOException {
    File writtenJson = new File(exportContext.getArchiveAssmblyFlder(), "ro-crate-metadata.json");
    FileInputStream inputStream = new FileInputStream(writtenJson);
    StringBuilder resultStringBuilder = new StringBuilder();
    boolean replaceRoCrate = false;
    try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
      String line;
      while ((line = br.readLine()) != null) {
        if (line.indexOf("keywords") != -1 && line.indexOf("[") == -1) {
          replaceRoCrate = true;
          String singleKeyword = line.replace("\"keywords\" :", "");
          singleKeyword = singleKeyword.substring(0, singleKeyword.length() - 1);
          resultStringBuilder
              .append("\"keywords\" : [ " + singleKeyword.trim() + " ],")
              .append("\n");
        } else {
          resultStringBuilder.append(line).append("\n");
        }
      }
      if (replaceRoCrate) {
        File replaced =
            new File(exportContext.getArchiveAssmblyFlder(), "ro-crate-metadata.json_rep");
        try (FileWriter myWriter = new FileWriter(replaced)) {
          myWriter.write(resultStringBuilder.toString());
          Path targetPath = writtenJson.toPath();
          Files.delete(writtenJson.toPath());
          Files.move(replaced.toPath(), targetPath);
        }
      }
      inputStream.close();
    }
  }

  private static boolean thereWereNfsExports(List<ExportedRecord> archived) {
    for (ExportedRecord exportedRecord : archived) {
      if (exportedRecord.getArchivedRecord() != null) {
        for (ArchivalField af : exportedRecord.getArchivedRecord().getListFields()) {
          if (!af.getNfsElements().isEmpty()) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static boolean thereWereNfsExportedExports(List<ExportedRecord> archived) {
    for (ExportedRecord exportedRecord : archived) {
      if (exportedRecord.getArchivedRecord() != null) {
        for (ArchivalField af : exportedRecord.getArchivedRecord().getListFields()) {
          if (!af.getNfsElements().isEmpty()) {
            for (ArchivalNfsFile anfs : af.getNfsElements()) {
              if (anfs.isAddedToArchive()) {
                return true;
              }
            }
          }
        }
      }
    }
    return false;
  }

  private void addDatesToFileEntityBuilder(
      FileEntity.FileEntityBuilder fe, Date created, Date modified) {
    DateFormat df = new SimpleDateFormat("dd:MM:yy:HH:mm:ss");
    if (created != null) {
      fe.addProperty("dateCreated", df.format(created));
    }
    if (modified != null) {
      fe.addProperty("dateModified", df.format(modified));
    }
  }

  public void buildXmlFileEntity(
      File recordFolder,
      String fileName,
      StructuredDocument structuredDocument,
      boolean isStructuredDoc,
      String sha256Hash) {
    buildXmlFileEntity(
        recordFolder, fileName, structuredDocument, isStructuredDoc, null, sha256Hash);
  }

  /**
   * @param recordFolder export folder
   * @param fileName
   * @param structuredDocument
   * @param isStructuredDoc false if this is the xml for a Strcutured document's form
   */
  public void buildXmlFileEntity(
      File recordFolder,
      String fileName,
      StructuredDocument structuredDocument,
      boolean isStructuredDoc,
      String description,
      String sha256) {
    FileEntity.FileEntityBuilder fefeb = new FileEntity.FileEntityBuilder();
    fefeb.setId("./" + recordFolder.getName() + "/" + fileName);
    fefeb.setEncodingFormat(APPLICATION_XML);
    fefeb.addProperty(SHA_256, sha256);
    if (structuredDocument != null) {
      if (isStructuredDoc) {
        fefeb.addProperty("text", "An RSpace Structured Document");
        addDatesToFileEntityBuilder(
            fefeb,
            structuredDocument.getCreationDate(),
            structuredDocument.getModificationDateAsDate());
      } else {
        fefeb.addProperty("additionalType", structuredDocument.getForm().getName());
        fefeb.addProperty("text", "A Form used to create an RSpace Structured Document");
        addDatesToFileEntityBuilder(
            fefeb,
            structuredDocument.getForm().getCreationDateAsDate(),
            structuredDocument.getForm().getModificationDateAsDate());
      }
    } else if (!StringUtils.isEmpty(description)) {
      fefeb.addProperty("text", description);
    }
    DataEntity de = fefeb.build();
    roCrate.addDataEntity(de, false);
    dsb.addToHasPart(de);
  }

  public void buildFileEntityForEcatMedia(
      File recordFolder, EcatMediaFile mediaFile, String sha256Hash) {
    FileEntity.FileEntityBuilder mediaFeb = new FileEntity.FileEntityBuilder();
    mediaFeb.setId("./" + recordFolder.getName() + "/" + mediaFile.getName());
    mediaFeb.setEncodingFormat(mediaFile.getContentType());
    mediaFeb.addProperty("text", "A Media file from the Gallery");
    mediaFeb.addProperty(SHA_256, sha256Hash);
    addDatesToFileEntityBuilder(
        mediaFeb, mediaFile.getCreationDate(), mediaFile.getModificationDateAsDate());
    DataEntity mediaDE = mediaFeb.build();
    roCrate.addDataEntity(mediaDE, false);
    dsb.addToHasPart(mediaDE);
  }

  public void buildFileEntityForArchiveGalleryFieldFiles(
      File recordFolder, ArchivalField archivalField) {
    for (ArchivalGalleryMetadata agm : archivalField.getAllGalleryMetaData()) {
      FileEntity.FileEntityBuilder mediaFeb = new FileEntity.FileEntityBuilder();
      File target = null;
      if (StringUtils.isEmpty(agm.getLinkFile())) {
        target = new File(recordFolder, agm.getFileName());
        addSha256Hash(mediaFeb, target);
        mediaFeb.setId("./" + recordFolder.getName() + "/" + agm.getFileName());
      } else {
        target = new File(recordFolder, agm.getLinkFile());
        if (target.exists()) {
          // its a link to a Gallery file in a file or folder export - the Gallery file is being
          // exported
          // inside the folder of its linking doc - we can generate a hash
          addSha256Hash(mediaFeb, target);
          mediaFeb.setId("./" + recordFolder.getName() + "/" + agm.getLinkFile());
          mediaFeb.setEncodingFormat(agm.getContentType());
          mediaFeb.addProperty("text", FILE_LINK_TEXT);
          addDatesToFileEntityBuilder(mediaFeb, agm.getCreationDate(), agm.getModificationDate());
          DataEntity mediaDE = mediaFeb.build();
          roCrate.addDataEntity(mediaDE, false);
          dsb.addToHasPart(mediaDE);
        } else {
          if (agm.getParentGalleryFolderId() == null) {
            // Its a ***link*** to another RSpace structured doc, not a link to a Gallery file.
            // Hash will be generated later, when the doc itself is exported
            File parentFolder = new File(recordFolder.getParentFile(), agm.getLinkFile());
            DataSetEntity.DataSetBuilder link = new DataSetEntity.DataSetBuilder();
            // we generate the real ID of the linked data set
            // here to use as an identfier property of the 'link' so that the real doc
            // may be found in the ro-crate
            link.addProperty(IDENTIFIER, "./" + parentFolder.getName());
            // we generate a link here
            // with ID of linkingdoc/linkeddoc name (which does not exist as physical file but
            // denotes that the link is part of the linking document)
            link.setId("./" + recordFolder.getName() + "/" + agm.getLinkFile());
            link.addProperty("text", FILE_LINK_TEXT);
            DataEntity mediaDE = link.build();
            roCrate.addDataEntity(mediaDE, false);
            dsb.addToHasPart(mediaDE);
          } else {
            // Its a ***link*** to Gallery file in a full user/group export.
            // The Gallery file is being exported in a Gallery folder and the link from its linking
            // doc is virtual
            // Hash will be generated later, when the gallery file itself is exported
            String pathToItemInGallery = agm.getLinkFile().substring(3);
            DataSetEntity.DataSetBuilder link = new DataSetEntity.DataSetBuilder();
            // we generate the real ID of the linked data set
            // here to use as an identfier property of the 'link' so that the real doc
            // may be found in the ro-crate
            link.addProperty(IDENTIFIER, "./" + pathToItemInGallery);
            // we generate a link here
            // with ID of linkingdoc/linkeddoc name (which does not exist as physical file but
            // denotes that the link is part of the linking document)
            link.setId("./" + recordFolder.getName() + "/" + pathToItemInGallery);
            link.addProperty("text", FILE_LINK_TEXT);
            DataEntity mediaDE = link.build();
            roCrate.addDataEntity(mediaDE, false);
            dsb.addToHasPart(mediaDE);
          }
        }
      }
    }
  }

  private static void addSha256Hash(FileEntity.FileEntityBuilder builder, File target) {
    Sha256Hash sha256 = new Sha256Hash(target);
    builder.addProperty(SHA_256, sha256.toHex());
  }

  public void buildFileEntityForNfsFieldFiles(ArchivalField archivalField) {
    for (ArchivalNfsFile anfs : archivalField.getNfsElements()) {
      FileEntity.FileEntityBuilder mediaFeb = new FileEntity.FileEntityBuilder();
      if (anfs.isAddedToArchive()) {
        File target = new File(FILESTORE_FILES_ARCHIVE_DIR, anfs.getArchivePath());
        mediaFeb.setId("./" + FILESTORE_FILES_ARCHIVE_DIR + "/" + anfs.getArchivePath());
        mediaFeb.addProperty(
            IDENTIFIER, anfs.getFileSystemUrl() + anfs.getFileStorePath() + anfs.getRelativePath());
        addSha256Hash(mediaFeb, target);
        mediaFeb.addProperty(
            "text", "An exported remote file linked to by an exported RSpace Structured Document");
      } else {
        mediaFeb.setId(anfs.getFileSystemUrl() + anfs.getFileStorePath() + anfs.getRelativePath());
        mediaFeb.addProperty(
            "text",
            "A non exported remote file linked to by an exported RSpace Structured Document");
      }
      DataEntity mediaDE = mediaFeb.build();
      roCrate.addDataEntity(mediaDE, false);
      dsb.addToHasPart(mediaDE);
    }
  }
}
