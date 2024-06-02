package com.researchspace.service.archive.export;

import com.researchspace.archive.ArchivalNfsFile;
import com.researchspace.archive.model.IArchiveExportConfig;
import com.researchspace.model.netfiles.NfsElement;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.netfiles.NfsClient;
import com.researchspace.netfiles.NfsException;
import com.researchspace.netfiles.NfsFileDetails;
import com.researchspace.netfiles.NfsFolderDetails;
import com.researchspace.netfiles.NfsResourceDetails;
import com.researchspace.netfiles.NfsTarget;
import com.researchspace.service.DiskSpaceChecker;
import com.researchspace.service.NfsFileHandler;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

/** Stores nfs export configuration and status of exported nfs files. <br> */
@Data
@Slf4j
public class NfsExportContext {

  public static final String FILESTORE_FILES_ARCHIVE_DIR = "filestoreFiles";

  private Map<Long, NfsClient> nfsClients;
  private File archiveNfsDir;
  private File archiveAssemblyDir;

  private Map<Long, NfsFileStore> fileStoresByIdMap = new HashMap<>();
  private Map<String, NfsResourceDetails> resolvedResources = new HashMap<>();

  private Map<String, String> errors = new HashMap<>();
  private Map<String, String> folderSummaryMsgs = new HashMap<>();

  private IArchiveExportConfig exportConfig;

  public NfsExportContext(IArchiveExportConfig aconfig) {
    exportConfig = aconfig;
    nfsClients = aconfig.getAvailableNfsClients();
  }

  /**
   * Returns details of file behind provided NfsElement, with 'localFile' field referencing copy
   * downloaded from nfs filestore to RSpace server.
   *
   * @return file details or null if file pointed by nfsElement couldn't be downloaded
   */
  public NfsResourceDetails getDownloadedNfsResourceDetails(
      NfsElement nfsElem, FieldExporterSupport support) {

    NfsFileStore nfsFileStore = getNfsFileStore(nfsElem.getFileStoreId(), support);
    String fullPath = nfsFileStore.getAbsolutePath(nfsElem.getPath());
    NfsFileSystem fileSystem = nfsFileStore.getFileSystem();
    NfsTarget target = new NfsTarget(fullPath, nfsElem.getNfsId());

    return downloadNfsResourceDetails(fileSystem, target, nfsElem.isFolderLink(), null, support);
  }

  private NfsResourceDetails downloadNfsResourceDetails(
      NfsFileSystem fileSystem,
      NfsTarget nfsTarget,
      boolean isFolderLink,
      File targetDir,
      FieldExporterSupport support) {

    Long fileSystemId = fileSystem.getId();
    String fileMapKey = fileSystemId + "_" + nfsTarget.getPath();
    NfsClient nfsClient = nfsClients.get(fileSystemId);
    if (nfsClient == null || !nfsClient.isUserLoggedIn()) {
      errors.put(fileMapKey, "user not logged into '" + fileSystem.getName() + "' File System");
      log.info(
          "skipping export of nfs element "
              + nfsTarget.getPath()
              + " as user not logged into "
              + fileSystemId);
      return null;
    }

    if (!resolvedResources.containsKey(fileMapKey)) {
      NfsFileHandler nfsFileHandler = support.getNfsFileHandler();
      DiskSpaceChecker diskSpaceChecker = support.getDiskSpaceChecker();
      NfsResourceDetails downloadedNfsDetails = null;
      try {
        if (isFolderLink) {
          downloadedNfsDetails =
              downloadNfsFolderToRSpace(nfsTarget, nfsClient, fileSystem, support);
        } else {
          NfsFileDetails nfsFileDetails = nfsClient.queryForNfsFile(nfsTarget);
          String skippedMsg = null;
          if (nfsFileDetails != null) {
            diskSpaceChecker.assertEnoughDiskSpaceToCopyFileSizeIntoArchiveDir(
                nfsFileDetails.getSize(), archiveAssemblyDir);
            skippedMsg = checkNfsExportFiltersMsgForFile(nfsFileDetails, exportConfig);
          }
          if (skippedMsg != null) {
            log.info("skipping nfs file " + nfsTarget + ": " + skippedMsg);
            errors.put(fileMapKey, "file skipped (" + skippedMsg + ")");
          } else {
            downloadedNfsDetails =
                nfsFileHandler.downloadNfsFileToRSpace(nfsTarget, nfsClient, targetDir);
          }
        }
        if (downloadedNfsDetails != null) {
          downloadedNfsDetails.setFileSystemId(fileSystemId);
        }
      } catch (IOException e) {
        log.info("download error when trying to export nfs element: " + nfsTarget, e);
        errors.put(fileMapKey, "download error: " + e.getMessage());
      }
      resolvedResources.put(fileMapKey, downloadedNfsDetails);
    }
    return resolvedResources.get(fileMapKey);
  }

  /**
   * @return message explaining why file is filtered out during export, or null if file should be
   *     included
   */
  public static String checkNfsExportFiltersMsgForFile(
      NfsFileDetails nfsFileDetails, IArchiveExportConfig exportConfig) {
    if (exportConfig == null) {
      return null;
    }

    long fileSizeLimit = exportConfig.getMaxNfsFileSize();
    Set<String> excludedFileExtensions = exportConfig.getExcludedNfsFileExtensions();

    if (nfsFileDetails != null) {
      if (fileSizeLimit > 0 && nfsFileDetails.getSize() > fileSizeLimit) {
        return "file larger than provided size limit";
      }
      String fileExtensionLowerCase =
          FilenameUtils.getExtension(nfsFileDetails.getName()).toLowerCase();
      if (CollectionUtils.isNotEmpty(excludedFileExtensions)
          && excludedFileExtensions.contains(fileExtensionLowerCase)) {
        return "file extension '" + fileExtensionLowerCase + "' excluded";
      }
    }
    return null;
  }

  public void configureArchiveNfsDirForAssemblyFolder(File archiveAssemblyFolder) {
    archiveAssemblyDir = archiveAssemblyFolder;
    archiveNfsDir = FileUtils.getFile(archiveAssemblyFolder, FILESTORE_FILES_ARCHIVE_DIR);
  }

  public String getDownloadErrorMsgForNfsFile(ArchivalNfsFile archiveNfs) {
    NfsFileStore nfsFileStore = getNfsFileStore(archiveNfs.getFileStoreId(), null);
    String fileMapKey =
        archiveNfs.getFileSystemId()
            + "_"
            + nfsFileStore.getAbsolutePath(archiveNfs.getRelativePath());
    return errors.get(fileMapKey);
  }

  public String getDownloadSummaryMsgForNfsFolder(ArchivalNfsFile archiveNfs) {
    NfsFileStore nfsFileStore = getNfsFileStore(archiveNfs.getFileStoreId(), null);
    String folderKey =
        archiveNfs.getFileSystemId()
            + "_"
            + nfsFileStore.getAbsolutePath(archiveNfs.getRelativePath());

    String internalSummaryMsg = folderSummaryMsgs.get(folderKey);
    if (internalSummaryMsg == null) {
      return "empty folder";
    }

    List<String> included = new ArrayList<>();
    List<String> skipped = new ArrayList<>();
    for (String childMsg : internalSummaryMsg.split(";;")) {
      if (childMsg.endsWith("::included")) {
        included.add(childMsg.substring(0, childMsg.length() - "::included".length()));
      } else {
        String[] childNameAndError = childMsg.split("::");
        skipped.add(String.format("%s (%s)", childNameAndError[0], childNameAndError[1]));
      }
    }
    String downloadSummaryMsg = "";
    if (included.size() > 0) {
      downloadSummaryMsg += "Included: " + String.join(", ", included) + "; ";
    }
    if (skipped.size() > 0) {
      downloadSummaryMsg += "Skipped: " + String.join(", ", skipped) + ";";
    }
    return downloadSummaryMsg;
  }

  private NfsFileStore getNfsFileStore(Long fileStoreId, FieldExporterSupport support) {
    if (!fileStoresByIdMap.containsKey(fileStoreId) && support != null) {
      fileStoresByIdMap.put(fileStoreId, support.getNfsManager().getNfsFileStore(fileStoreId));
    }
    NfsFileStore nfsFileStore = fileStoresByIdMap.get(fileStoreId);
    return nfsFileStore;
  }

  /**
   * Retrieves details of folder & downloads its files. Skips the subfolders.
   *
   * @return details of the downloaded folder (with localFile pointing to File on RSpace server),
   * @throws IOException if folder can't be retrieved
   */
  private NfsFolderDetails downloadNfsFolderToRSpace(
      NfsTarget nfsTarget,
      NfsClient nfsClient,
      NfsFileSystem fileSystem,
      FieldExporterSupport support)
      throws IOException {

    NfsFileHandler nfsFileHandler = support.getNfsFileHandler();
    NfsFolderDetails nfsFolderDetails =
        nfsFileHandler.retireveNfsFolderDetails(nfsTarget, nfsClient);
    if (nfsFolderDetails == null) {
      throw new NfsException("couldn't retrieve details of nfs folder: " + nfsTarget, null);
    }
    applyFileSystemIdToNfsResource(nfsFolderDetails, fileSystem.getId());

    File tempDirectory = nfsFileHandler.createTempDirForNfsDownload();
    File downloadedFolder = new File(tempDirectory, nfsFolderDetails.getName());
    FileUtils.forceMkdir(downloadedFolder);
    nfsFolderDetails.setLocalFile(downloadedFolder);

    String folderKey = fileSystem.getId() + "_" + nfsTarget.getPath();
    for (NfsResourceDetails child : nfsFolderDetails.getContent()) {
      NfsResourceDetails childDetails = null;
      if (child.isFile()) {
        childDetails =
            downloadNfsResourceDetails(
                fileSystem,
                new NfsTarget(child.getFileSystemFullPath(), child.getNfsId()),
                false,
                downloadedFolder,
                support);
      }

      // add a line to folder summary msg
      String childKey = fileSystem.getId() + "_" + child.getFileSystemFullPath();
      String childMsg = null;
      if (childDetails != null) {
        childMsg = "included";
      } else {
        childMsg = child.isFolder() ? "skipped as subfolder" : errors.get(childKey);
      }
      String childSummaryMsg = String.format("%s::%s;;", child.getName(), childMsg);
      String newFolderSummary = folderSummaryMsgs.getOrDefault(folderKey, "") + childSummaryMsg;
      folderSummaryMsgs.put(folderKey, newFolderSummary);
    }
    return nfsFolderDetails;
  }

  /** Updates resource with fileSystemId, if it's a folder than updates content resources too. */
  public static void applyFileSystemIdToNfsResource(
      NfsResourceDetails nfsResourceDetails, Long fileSystemId) {
    nfsResourceDetails.setFileSystemId(fileSystemId);
    if (nfsResourceDetails.isFolder()) {
      NfsFolderDetails nfsFolderDetails = (NfsFolderDetails) nfsResourceDetails;
      for (NfsResourceDetails child : nfsFolderDetails.getContent()) {
        if (child.isFolder()) {
          applyFileSystemIdToNfsResource(child, fileSystemId);
        } else {
          child.setFileSystemId(fileSystemId);
        }
      }
    }
  }
}
