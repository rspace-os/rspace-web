package com.researchspace.service.archive.export;

import static com.researchspace.service.archive.ExportImport.RESOURCES;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.io.FileUtils.forceMkdir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.researchspace.archive.ArchivalNfsFile;
import com.researchspace.archive.ArchiveFileNameData;
import com.researchspace.archive.ArchiveFolder;
import com.researchspace.archive.ArchiveManifest;
import com.researchspace.archive.ArchiveResult;
import com.researchspace.archive.ArchiveUtils;
import com.researchspace.archive.ImmutableExportRecordList;
import com.researchspace.archive.model.IArchiveExportConfig;
import com.researchspace.core.util.ZipUtils;
import com.researchspace.dao.ArchiveDao;
import com.researchspace.dao.RecordDao;
import com.researchspace.model.ArchivalCheckSum;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.audit.AuditedRecord;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.RecordToFolder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.DiskSpaceLimitException;
import com.researchspace.service.archive.ArchiveExportServiceManager;
import com.researchspace.service.archive.ExportImport;
import edu.kit.datamanager.ro_crate.RoCrate;
import edu.kit.datamanager.ro_crate.entities.data.DataSetEntity;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractArchiveExporter implements ArchiveExportServiceManager {

  protected final Logger log = LoggerFactory.getLogger(getClass());

  protected @Autowired RecordDao recordDao;
  protected @Autowired ArchiveDao archiveDao;
  protected @Autowired FormIconWriter formIconWriter;

  private @Autowired ArchiveNamingStrategy archiveNamingStrategy;
  private @Autowired BeanFactory beanFactory;
  private @Autowired ArchiveExportPlanner archivePlanner;

  @Override
  public ArchiveResult exportArchive(
      ArchiveManifest manifest, ImmutableExportRecordList exportList, IArchiveExportConfig aconfig)
      throws Exception {

    setItemsFromConfig(aconfig, manifest);
    ExportObjectGenerator exportObjectGenerator = beanFactory.getBean(ExportObjectGenerator.class);
    ExportContext exportContext = preArchive(manifest, aconfig, exportObjectGenerator, exportList);

    List<ExportedRecord> archived = new ArrayList<>();
    List<ArchiveFolder> folderTree = exportList.getFolderTree();
    RoCrate roCrate = null;
    // We do not export RSPace folders or Notebooks as a physical folders (there would be issues due
    // to file path length if we did)
    // - instead represent them in foldertree.xml and in ro-crate-metadata.json if its an 'ELN'
    // export
    List<RoCrateLogicalFolder> logicalFolders = null;
    if (aconfig.isELNArchive()) {
      logicalFolders = new ArrayList<>();
      roCrate = RoCrateHandler.startProcessRoCrate(logicalFolders, folderTree, aconfig);
    }
    for (GlobalIdentifier rid : exportList.getRecordsToExport()) {
      exportRecord(
          aconfig,
          exportObjectGenerator,
          exportContext,
          archived,
          rid,
          folderTree,
          exportList,
          roCrate,
          logicalFolders);
      aconfig.getProgressMonitor().worked(1);
      log.debug(
          "Export is {}% complete",
          String.format("%.2f", aconfig.getProgressMonitor().getPercentComplete()));
    }
    preZip(manifest, exportList, aconfig, exportContext, archived);
    RoCrateHandler.finishProcessRoCrate(manifest, aconfig, exportContext, roCrate, archived);
    ArchivalCheckSum csum = zipAndSaveChecksumInDB(aconfig, exportObjectGenerator, exportContext);
    exportContext.setCsum(csum);
    ArchiveResult archiveResult =
        postArchive(aconfig, exportObjectGenerator, archived, folderTree, exportContext);
    return archiveResult;
  }

  private void exportRecord(
      IArchiveExportConfig aconfig,
      ExportObjectGenerator exportObjecGen,
      ExportContext context,
      List<ExportedRecord> archived,
      GlobalIdentifier rid,
      List<ArchiveFolder> folderTree,
      ImmutableExportRecordList exportList) {
    exportRecord(
        aconfig, exportObjecGen, context, archived, rid, folderTree, exportList, null, null);
  }

  private boolean recordParentIsThisFolder(BaseRecord parent, RoCrateLogicalFolder logicalFolder) {
    return logicalFolder.getRspaceGlobalID().equals(parent.getGlobalIdentifier());
  }

  private void exportRecord(
      IArchiveExportConfig aconfig,
      ExportObjectGenerator exportObjecGen,
      ExportContext context,
      List<ExportedRecord> archived,
      GlobalIdentifier rid,
      List<ArchiveFolder> folderTree,
      ImmutableExportRecordList exportList,
      RoCrate roCrate,
      List<RoCrateLogicalFolder> logicalTopLevelFolders) {
    try {
      Record record = recordDao.get(rid.getDbId());
      List<AuditedRecord> versionsToExport =
          archivePlanner.getVersionsToExportForRecord(aconfig, rid, record);

      for (AuditedRecord ar : versionsToExport) {
        BaseRecord recordToExport = ar.getRecord();
        Number revision = ar.getRevision(); // will be null for non-all-revision export
        String uniqueExportName = new ArchiveFileNameData(record, revision).toFileName();
        File recordFolder = new File(context.getArchiveAssmblyFlder(), uniqueExportName);
        forceMkdir(recordFolder);
        DataSetEntity.DataSetBuilder dsb = null;
        if (roCrate != null) {
          dsb = new DataSetEntity.DataSetBuilder();
          dsb.setId("./" + recordFolder.getName());
          DataSetEntity out = dsb.build();
          roCrate.addDataEntity(out, true);
          for (RoCrateLogicalFolder logicalFolder : logicalTopLevelFolders) {
            if (recordToExport.hasParents()) {
              for (RecordToFolder parent : recordToExport.getParents()) {
                if (recordParentIsThisFolder(parent.getFolder(), logicalFolder)) {
                  logicalFolder.addToHasPart(out.getId());
                  out.addProperty("isPartOf", logicalFolder.getId());
                }
              }
            }
          }
          if (recordToExport.isStructuredDocument()) {
            StructuredDocument sd = recordToExport.asStrucDoc();
            if (!StringUtils.isEmpty(sd.getDocTag())) {
              ObjectMapper mapper = new ObjectMapper();

              List<String> tags = Arrays.asList(sd.getDocTag().split(","));
              ArrayNode node = mapper.valueToTree(tags);
              dsb.addProperty("keywords", node);
            }
          }
        }
        RoCrateHandler roCrateHandler = null;
        if (roCrate != null) {
          roCrateHandler = new RoCrateHandler(roCrate, dsb);
        }
        if (recordToExport.isStructuredDocument()) {
          exportObjecGen.makeRecordExport(
              aconfig,
              recordToExport.asStrucDoc(),
              revision,
              recordFolder,
              0,
              archived,
              folderTree,
              context.getNfsContext(),
              exportList,
              roCrateHandler);
          writeFormIconEntityFile(record.asStrucDoc(), recordFolder);
        } else if (recordToExport.isMediaRecord()) {
          exportObjecGen.makeGalleryExport(
              (EcatMediaFile) recordToExport, revision, recordFolder, archived, roCrateHandler);
          exportLinkedMediaFiles(
              recordToExport,
              aconfig,
              exportObjecGen,
              context,
              archived,
              rid,
              folderTree,
              exportList);
        }
      }

    } catch (Exception ex) {
      if (ex instanceof DiskSpaceLimitException) {
        throw (DiskSpaceLimitException) ex;
      }
      log.warn("Error exporting record {} : {}", rid, ex.getMessage());
      log.warn("Exception:", ex);
    }
  }

  private void exportLinkedMediaFiles(
      BaseRecord recordToExport,
      IArchiveExportConfig aconfig,
      ExportObjectGenerator exportObjecGen,
      ExportContext context,
      List<ExportedRecord> archived,
      GlobalIdentifier rid,
      List<ArchiveFolder> folderTree,
      ImmutableExportRecordList exportList) {

    if (recordToExport instanceof EcatImage) {
      EcatImage imageToExport = (EcatImage) recordToExport;
      if (imageToExport.getOriginalImage() != null) {
        exportRecord(
            aconfig,
            exportObjecGen,
            context,
            archived,
            imageToExport.getOriginalImageOid(),
            folderTree,
            exportList);
      }
    }
  }

  private void writeFormIconEntityFile(StructuredDocument strucDoc, File recordFolder)
      throws IOException {
    formIconWriter.writeFormIconEntityFile(strucDoc.getForm(), recordFolder);
  }

  protected ArchiveResult postArchive(
      IArchiveExportConfig aconfig,
      ExportObjectGenerator xmlDoc,
      List<ExportedRecord> archived,
      List<ArchiveFolder> folderTree,
      ExportContext context)
      throws IOException {
    if (context.getArchiveAssmblyFlder().exists()) {
      FileUtils.forceDelete(context.getArchiveAssmblyFlder());
    }
    ArchiveResult archiveResult = generateResults(aconfig, xmlDoc, archived, folderTree, context);
    return archiveResult;
  }

  private ArchiveResult generateResults(
      IArchiveExportConfig aconfig,
      ExportObjectGenerator xmlDoc,
      List<ExportedRecord> exported,
      List<ArchiveFolder> folderTree,
      ExportContext context)
      throws IOException {
    ArchiveResult archiveResult = new ArchiveResult();
    archiveResult.setExportFile(
        new File(aconfig.getTopLevelExportFolder(), context.getZipFileName()));
    archiveResult.setChecksum(context.getCsum());
    archiveResult.setArchiveConfig(aconfig);
    archiveResult.setArchivedRecords(
        exported.stream().map(r -> r.getExportedRecord()).collect(toList()));
    archiveResult.setArchivedFolders(folderTree);
    archiveResult.setArchivedNfsFiles(getAllNfsLinksInExport(exported));

    return archiveResult;
  }

  private void setItemsFromConfig(IArchiveExportConfig aconfig, ArchiveManifest manifest) {
    if (!StringUtils.isEmpty(aconfig.getDescription())) {
      manifest.addItem(ArchiveManifest.DESCRIPTION, aconfig.getDescription());
    }
    manifest.addItem(ArchiveManifest.SCOPE, aconfig.getExportScope().name());
  }

  protected File writeManifestFile(ArchiveManifest manifest, File archiveRoot) {
    File manifestFile = new File(archiveRoot, ExportImport.EXPORT_MANIFEST);
    try {
      FileUtils.write(manifestFile, manifest.stringify(), "UTF-8");
    } catch (IOException ex) {
      log.warn(ex.getMessage());
    }
    return manifestFile;
  }

  /**
   * Performs export-format-specific operations on the export folder prior to zipping; e.g. writing
   * manifests, indexes, meta-data etc.
   *
   * @param manifest
   * @param exportList
   * @param aconfig
   * @param context
   * @param archived
   * @throws Exception
   */
  protected abstract void preZip(
      ArchiveManifest manifest,
      ImmutableExportRecordList exportList,
      IArchiveExportConfig aconfig,
      ExportContext context,
      List<ExportedRecord> archived)
      throws Exception;

  protected ExportContext preArchive(
      ArchiveManifest manifest,
      IArchiveExportConfig aconfig,
      ExportObjectGenerator xmlDoc,
      ImmutableExportRecordList exportList)
      throws IOException {
    return defaultPreArchive(manifest, aconfig, xmlDoc, exportList);
  }

  private void setUpArchiveAssemblyFolder(IArchiveExportConfig aconfig, ExportContext context)
      throws IOException {
    File archiveAssmblyFlder = createArchiveFolder(aconfig, context);
    context.setArchiveAssmblyFlder(archiveAssmblyFlder);
    if (!archiveAssmblyFlder.exists()) {
      forceMkdir(archiveAssmblyFlder);
    }
    File staticResourcesFolder = new File(archiveAssmblyFlder, RESOURCES);
    if (!staticResourcesFolder.exists()) {
      forceMkdir(staticResourcesFolder);
    }
  }

  private File createArchiveFolder(IArchiveExportConfig aconfig, ExportContext context) {
    String zipFolderName = archiveNamingStrategy.generateArchiveName(aconfig, context);
    File archiveAssmblyFlder = new File(aconfig.getTopLevelExportFolder(), zipFolderName);
    context.setZipName(zipFolderName);
    context.setZipFileName(
        aconfig.isELNArchive() ? zipFolderName + ".eln" : zipFolderName + ".zip");
    return archiveAssmblyFlder;
  }

  private ExportContext defaultPreArchive(
      ArchiveManifest manifest,
      IArchiveExportConfig aconfig,
      ExportObjectGenerator exportObjectGenerator,
      ImmutableExportRecordList exportList)
      throws IOException {
    ExportContext context = new ExportContext();
    context.setExportRecordList(exportList);
    setUpArchiveAssemblyFolder(aconfig, context);
    File archiveAssmblyFlder = context.getArchiveAssmblyFlder();
    exportObjectGenerator.setExportFolder(archiveAssmblyFlder);
    exportObjectGenerator.setExportConfig(aconfig);
    if (aconfig.isIncludeNfsLinks()) {
      NfsExportContext nfsContext = new NfsExportContext(aconfig);
      nfsContext.configureArchiveNfsDirForAssemblyFolder(archiveAssmblyFlder);
      context.setNfsContext(nfsContext);
    }
    final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    manifest.addItem("exportDate", sdf.format(new Date()));
    return context;
  }

  private ArchivalCheckSum zipAndSaveChecksumInDB(
      IArchiveExportConfig aconfig,
      ExportObjectGenerator exportObjectGnerator,
      ExportContext exportContext)
      throws IOException {
    String contentsChecksum =
        ArchiveUtils.calculateFolderContentsChecksum(exportContext.getArchiveAssmblyFlder());
    long sum = writeZip(exportContext, aconfig);
    ArchivalCheckSum csum = new ArchivalCheckSum();
    csum.setArchivalDate(new Date().getTime());
    csum.setUid(exportContext.getArchiveId());
    csum.setZipName(exportContext.getZipFileName());
    csum.setZipSize(exportContext.getZipSize());
    csum.setCheckSum(sum);
    csum.setExporter(aconfig.getExporter());
    csum.setZipContentCheckSum(contentsChecksum);
    save(csum);
    return csum;
  }

  /**
   * See RSPAC-649. For DataShare export, this requires a flat structure of the zip archive, i.e.,
   * we don't zip the archive folder, but the folder's contents. This currently (0.28) means that
   * METS archives cannot be re-imported easily.
   *
   * @param exportContext
   * @return the Zip's checksum
   * @throws IOException
   */
  private long writeZip(ExportContext exportContext, IArchiveExportConfig exportConfig)
      throws IOException {
    File zipFile = new File(exportConfig.getTopLevelExportFolder(), exportContext.getZipFileName());
    ZipUtils.createZip(zipFile, exportContext.getArchiveAssmblyFlder());
    exportContext.setZipSize(zipFile.length());
    return ArchiveUtils.calculateChecksum(zipFile);
  }

  public List<ArchivalCheckSum> getCurrentArchiveMetadatas() {
    return archiveDao.getUnexpiredArchives();
  }

  public ArchivalCheckSum save(ArchivalCheckSum csum) {
    return archiveDao.save(csum);
  }

  protected Set<ArchivalNfsFile> getAllNfsLinksInExport(List<ExportedRecord> archived) {
    Set<ArchivalNfsFile> allNfs = new LinkedHashSet<>();
    for (ExportedRecord exported : archived) {
      if (exported.getArchivedRecord() != null) {
        List<ArchivalNfsFile> nfs = exported.getArchivedRecord().getAllDistinctNfsElements();
        allNfs.addAll(nfs);
      }
    }
    return allNfs;
  }
}
