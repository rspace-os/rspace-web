package com.researchspace.service.impl;

import com.researchspace.archive.model.IArchiveExportConfig;
import com.researchspace.dao.NfsDao;
import com.researchspace.dao.RecordDao;
import com.researchspace.linkedelements.FieldContents;
import com.researchspace.linkedelements.FieldElementLinkPair;
import com.researchspace.linkedelements.FieldElementLinkPairs;
import com.researchspace.linkedelements.FieldParser;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.netfiles.NfsElement;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.model.netfiles.NfsFileSystemInfo;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.netfiles.NfsClient;
import com.researchspace.netfiles.NfsExportPlan;
import com.researchspace.netfiles.NfsFileDetails;
import com.researchspace.netfiles.NfsFolderDetails;
import com.researchspace.netfiles.NfsResourceDetails;
import com.researchspace.netfiles.NfsTarget;
import com.researchspace.service.DiskSpaceChecker;
import com.researchspace.service.NfsExportManager;
import com.researchspace.service.NfsManager;
import com.researchspace.service.archive.export.NfsExportContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("nfsExportManager")
public class NfsExportManagerImpl implements NfsExportManager {

  @Autowired private FieldParser fieldParser;

  @Autowired private NfsDao nfsDao;

  @Autowired private RecordDao recordDao;

  @Autowired private DiskSpaceChecker diskSpaceChecker;

  @Autowired private NfsManager nfsManager;

  @Override
  public NfsExportPlan generateQuickExportPlan(List<GlobalIdentifier> recordsToExport) {

    NfsExportPlan plan = new NfsExportPlan();

    Set<NfsElement> foundElements = new HashSet<>();
    Set<Long> foundFileStoreIds = new HashSet<>();

    for (GlobalIdentifier recordOid : recordsToExport) {
      Record record = recordDao.get(recordOid.getDbId());
      if (record.isStructuredDocument()) {
        StructuredDocument doc = record.asStrucDoc();
        FieldContents fieldContents =
            fieldParser.findFieldElementsInContent(doc.getConcatenatedFieldContent());
        FieldElementLinkPairs<NfsElement> nfsLinks = fieldContents.getElements(NfsElement.class);
        for (FieldElementLinkPair<NfsElement> nfsElemPair : nfsLinks.getPairs()) {
          NfsElement nfsElem = nfsElemPair.getElement();
          foundElements.add(nfsElem);
          foundFileStoreIds.add(nfsElem.getFileStoreId());
        }
      }
    }

    for (Long fileStoreId : foundFileStoreIds) {
      NfsFileStore nfsFileStore = nfsDao.getNfsFileStore(fileStoreId);
      plan.addFoundFileStore(nfsFileStore);
      NfsFileSystem fileSystem = nfsFileStore.getFileSystem();
      plan.addFoundFileSystem(fileSystem.toFileSystemInfo());
    }

    for (NfsElement elem : foundElements) {
      NfsFileStore fileStoreForLink = plan.getFoundFileStoresByIdMap().get(elem.getFileStoreId());
      plan.addFoundNfsLink(
          fileStoreForLink.getFileSystem().getId(),
          fileStoreForLink.getAbsolutePath(elem.getPath()),
          elem);
    }

    return plan;
  }

  @Override
  public void checkLoggedAsStatusForFileSystemsInExportPlan(
      NfsExportPlan plan, Map<Long, NfsClient> nfsClients, User user) {
    // for each found filesystem check if users is logged in
    for (NfsFileSystemInfo fileSystemInfo : plan.getFoundFileSystems()) {
      String fsUsername =
          nfsManager.getLoggedAsUsernameIfUserLoggedIn(fileSystemInfo.getId(), nfsClients, user);
      if (fsUsername != null) {
        fileSystemInfo.setLoggedAs(fsUsername);
      }
    }
  }

  @Override
  public void scanFileSystemsForFoundNfsLinks(
      NfsExportPlan plan,
      Map<Long, NfsClient> nfsClients,
      IArchiveExportConfig archiveExportConfig) {

    plan.clearCheckedNfsLinks();
    checkFoundLinksOnConnectedFileSystems(plan, nfsClients);
    addExportFilterMsgForCheckedLinksInPlan(plan, archiveExportConfig);
    setArchiveSizeLimitProperties(plan);
  }

  public static final String SUBFOLDER_NOT_INCLUDED_MSG = "subfolder of linked folder";

  public static final String RESOURCE_NOT_ACCESSIBLE_MSG = "resource not accessible";

  public static final String NOT_LOGGED_INTO_FILE_SYSTEM_MSG =
      "not logged into connected File System";

  private void checkFoundLinksOnConnectedFileSystems(
      NfsExportPlan plan, Map<Long, NfsClient> nfsClients) {
    for (NfsElement nfsElem : plan.getFoundNfsLinks().values()) {
      NfsFileStore fileStore = plan.getFoundFileStoresByIdMap().get(nfsElem.getFileStoreId());
      Long fileSystemId = fileStore.getFileSystem().getId();
      String absolutePath = fileStore.getAbsolutePath(nfsElem.getPath());
      NfsTarget nfsTarget = new NfsTarget(absolutePath, nfsElem.getNfsId());

      if (!plan.isFileAlreadyChecked(fileSystemId, absolutePath)) {
        NfsResourceDetails foundDetails = null;
        NfsClient nfsClient = nfsClients.get(fileSystemId);
        if (nfsClient == null || !nfsClient.isUserLoggedIn()) {
          plan.addCheckedNfsLinkMsg(fileSystemId, absolutePath, NOT_LOGGED_INTO_FILE_SYSTEM_MSG);
        } else {
          if (nfsElem.isFolderLink()) {
            try {
              foundDetails = nfsClient.queryForNfsFolder(nfsTarget);
            } catch (IOException e) {
              // already logged
            }
          } else {
            foundDetails = nfsClient.queryForNfsFile(nfsTarget);
          }
          if (foundDetails == null) {
            plan.addCheckedNfsLinkMsg(fileSystemId, absolutePath, RESOURCE_NOT_ACCESSIBLE_MSG);
          } else {
            NfsExportContext.applyFileSystemIdToNfsResource(foundDetails, fileSystemId);
          }
        }
        plan.addCheckedNfsLink(fileSystemId, absolutePath, foundDetails);
      }
    }
  }

  private void addExportFilterMsgForCheckedLinksInPlan(
      NfsExportPlan plan, IArchiveExportConfig archiveExportConfig) {
    List<NfsFileDetails> nfsFilesToCheck = new ArrayList<>();
    for (Entry<String, NfsResourceDetails> checkedLink : plan.getCheckedNfsLinks().entrySet()) {
      NfsResourceDetails checkedResource = checkedLink.getValue();
      if (checkedResource != null) {
        if (checkedResource.isFile()) {
          nfsFilesToCheck.add((NfsFileDetails) checkedResource);
        } else {
          NfsFolderDetails checkedFolder = (NfsFolderDetails) checkedResource;
          for (NfsResourceDetails folderContentResource : checkedFolder.getContent()) {
            if (folderContentResource.isFile()) {
              nfsFilesToCheck.add((NfsFileDetails) folderContentResource);
            } else {
              plan.addCheckedNfsLinkMsg(
                  folderContentResource.getFileSystemId(),
                  folderContentResource.getFileSystemFullPath(),
                  SUBFOLDER_NOT_INCLUDED_MSG);
            }
          }
        }
      }
    }

    for (NfsFileDetails fileToCheck : nfsFilesToCheck) {
      String exportFiltersMsg =
          NfsExportContext.checkNfsExportFiltersMsgForFile(fileToCheck, archiveExportConfig);
      if (exportFiltersMsg != null) {
        plan.addCheckedNfsLinkMsg(
            fileToCheck.getFileSystemId(), fileToCheck.getFileSystemFullPath(), exportFiltersMsg);
      }
    }
  }

  private void setArchiveSizeLimitProperties(NfsExportPlan plan) {
    plan.setMaxArchiveSizeMBProp(diskSpaceChecker.getMaxArchiveSizeMB());
    plan.setCurrentlyAllowedArchiveSizeMB(diskSpaceChecker.getCurrentlyAllowedArchiveSizeMB());
  }
}
