package com.researchspace.netfiles.irods;

import com.researchspace.api.v1.model.ApiExternalStorageOperationInfo;
import com.researchspace.api.v1.model.ApiExternalStorageOperationResult;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.netfiles.NfsAbstractClient;
import com.researchspace.netfiles.NfsClient;
import com.researchspace.netfiles.NfsException;
import com.researchspace.netfiles.NfsFileDetails;
import com.researchspace.netfiles.NfsFileTreeNode;
import com.researchspace.netfiles.NfsFileTreeOrderType;
import com.researchspace.netfiles.NfsFolderDetails;
import com.researchspace.netfiles.NfsResourceDetails;
import com.researchspace.netfiles.NfsTarget;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.irods.jargon.core.connection.IRODSAccount;
import org.irods.jargon.core.exception.JargonException;
import org.irods.jargon.core.pub.DataTransferOperations;
import org.irods.jargon.core.pub.IRODSAccessObjectFactory;
import org.irods.jargon.core.pub.IRODSFileSystem;
import org.irods.jargon.core.pub.domain.Collection;
import org.irods.jargon.core.pub.domain.DataObject;
import org.irods.jargon.core.pub.io.IRODSFile;
import org.irods.jargon.core.pub.io.IRODSFileFactory;
import org.irods.jargon.core.query.CollectionAndDataObjectListingEntry;

@Slf4j
public class IRODSClient extends NfsAbstractClient implements NfsClient {

  private final transient JargonFacade jargonFacade;
  private final IRODSAccount irodsAccount;

  public IRODSClient(IRODSAccount irodsAccount, JargonFacade jargonFacade) {
    super(irodsAccount.getUserName());
    this.irodsAccount = irodsAccount;
    this.jargonFacade = jargonFacade;
  }

  @Override
  public boolean isUserLoggedIn() {
    return username != null;
  }

  @Override
  public void tryConnectAndReadTarget(String target) throws NfsException {
    try {
      jargonFacade.tryConnectAndReadTarget(target, irodsAccount);
    } catch (JargonException e) {
      log.error(
          "Error Connecting to iRODS at: {} with username: {}",
          irodsAccount.getHost(),
          irodsAccount.getUserName());
      throw new NfsException("Error Connecting to iRODS", e);
    }
  }

  @Override
  public NfsFileTreeNode createFileTree(
      String target, String nfsOrder, NfsFileStore selectedUserFolder) throws NfsException {
    try {
      if (target == null || target.isEmpty()) {
        target = irodsAccount.getHomeDirectory();
      } else if (!target.startsWith("/")) {
        StringBuilder stringBuilder = new StringBuilder(target);
        stringBuilder.insert(0, "/");
        target = stringBuilder.toString();
      }
      NfsFileTreeNode rootNode = new NfsFileTreeNode();
      NfsFileTreeOrderType order = NfsFileTreeOrderType.parseOrderTypeString(nfsOrder);

      rootNode.setOrderType(order);

      List<CollectionAndDataObjectListingEntry> entries =
          jargonFacade.getListAllDataObjectsAndCollectionsUnderPath(target, irodsAccount);
      rootNode.setNodePath(target);
      entries.forEach(entry -> rootNode.addNode(getTreeNodeFromEntry(entry, selectedUserFolder)));
      return rootNode;

    } catch (JargonException e) {
      log.error(
          "Error Creating file tree from iRODS at {} on port {}",
          irodsAccount.getHost(),
          irodsAccount.getPort());
      throw new NfsException("Error Creating file tree from iRODS:", e);
    }
  }

  private NfsFileTreeNode getTreeNodeFromEntry(
      CollectionAndDataObjectListingEntry entry, NfsFileStore selectedUserFolder) {
    NfsFileTreeNode node = new NfsFileTreeNode();

    if (entry.isDataObject()) {
      node.calculateLogicPath(
          entry.getParentPath() + "/" + entry.getPathOrName(), selectedUserFolder);
      node.calculateFileName(entry.getPathOrName());
    } else {
      node.calculateLogicPath(entry.getPathOrName(), selectedUserFolder);
      node.calculateFileName(entry.getPathOrName().replace(entry.getParentPath(), ""));
    }
    node.setNodePath(entry.getPathOrName());
    node.setIsFolder(entry.isCollection());
    node.setFileDate(String.valueOf(entry.getModifiedAt()));
    node.setModificationDateMillis(entry.getModifiedAt().getTime());
    node.setFileSize(entry.getDisplayDataSize());
    node.setFileSizeBytes(entry.getDataSize());
    node.setNfsId((long) entry.getId());
    return node;
  }

  @Override
  public NfsFileDetails queryForNfsFile(NfsTarget nfsTarget) {
    NfsFileDetails fileDetails = null;
    try {
      DataObject irodsDataObject = getIrodsDataObject(nfsTarget);
      fileDetails = new NfsFileDetails(irodsDataObject.getDataName());
      fileDetails.setFileSystemParentPath(irodsDataObject.getCollectionName());
      fileDetails.setFileSystemFullPath(irodsDataObject.getAbsolutePath());
      fileDetails.setSize(irodsDataObject.getDataSize());
      fileDetails.setNfsId((long) irodsDataObject.getId());
    } catch (JargonException e) {
      log.error(
          "Error Querying for file {}, with id {}", nfsTarget.getPath(), nfsTarget.getNfsId(), e);
    }
    return fileDetails;
  }

  @Override
  public NfsFileDetails queryNfsFileForDownload(NfsTarget nfsTarget) throws NfsException {
    NfsFileDetails fileDetails = null;
    try {
      DataObject irodsDataObject = getIrodsDataObject(nfsTarget);
      fileDetails = new NfsFileDetails(irodsDataObject.getDataName());
      fileDetails.setFileSystemParentPath(irodsDataObject.getCollectionName());
      fileDetails.setFileSystemFullPath(irodsDataObject.getAbsolutePath());
      fileDetails.setSize(irodsDataObject.getDataSize());
      fileDetails.setNfsId((long) irodsDataObject.getId());
      fileDetails.setRemoteInputStream(
          jargonFacade.getIRODSFileInputStreamById((long) irodsDataObject.getId(), irodsAccount));
    } catch (JargonException e) {
      log.error(
          "Error Querying for file download {}, with id: {}",
          nfsTarget.getPath(),
          nfsTarget.getNfsId(),
          e);
      throw new NfsException("Error Querying NFS File for Download: ", e);
    }
    return fileDetails;
  }

  @Override
  public NfsFolderDetails queryForNfsFolder(NfsTarget nfsTarget) throws NfsException {
    NfsFolderDetails folderDetails = null;
    try {
      Collection irodsFolder = getIrodsCollection(nfsTarget);
      folderDetails = new NfsFolderDetails(irodsFolder.getCollectionName());
      folderDetails.setFileSystemFullPath(irodsFolder.getAbsolutePath());
      folderDetails.setFileSystemParentPath(irodsFolder.getCollectionParentName());
      folderDetails.setNfsId((long) irodsFolder.getCollectionId());

      List<CollectionAndDataObjectListingEntry> folderEntries =
          jargonFacade.getListAllDataObjectsAndCollectionsUnderPath(
              irodsFolder.getAbsolutePath(), irodsAccount);
      for (CollectionAndDataObjectListingEntry child : folderEntries) {

        NfsResourceDetails childResource = null;
        if (child.isCollection()) {
          childResource = new NfsFolderDetails(child.getPathOrName());
        } else {
          childResource = new NfsFileDetails(child.getPathOrName());
          childResource.setSize(child.getDataSize());
        }
        childResource.setNfsId((long) child.getId());
        childResource.setFileSystemFullPath(child.getPathOrName());
        childResource.setFileSystemParentPath(child.getParentPath());
        folderDetails.getContent().add(childResource);
      }

    } catch (JargonException e) {
      log.error(
          "Error Querying for folder: {}, with id: {}",
          nfsTarget.getPath(),
          nfsTarget.getNfsId(),
          e);
      throw new NfsException("Error Retrieving Details for folder: " + nfsTarget, e);
    }

    return folderDetails;
  }

  @Override
  public boolean supportWritePermission() {
    return true;
  }

  @Override
  public ApiExternalStorageOperationResult uploadFilesToNfs(
      String pathToFiles, Map<Long, File> mapRecordIdToFile) throws UnsupportedOperationException {
    IRODSFileSystem iRodsFileSystem = null;

    ApiExternalStorageOperationResult result = new ApiExternalStorageOperationResult();
    try {
      DataTransferOperations dtoIRODS = jargonFacade.getDataTransferOperations(irodsAccount);
      iRodsFileSystem = IRODSFileSystem.instance();
      IRODSAccessObjectFactory accessObjectFactory = iRodsFileSystem.getIRODSAccessObjectFactory();
      IRODSFileFactory irodsFileFactory = accessObjectFactory.getIRODSFileFactory(irodsAccount);

      IRODSFile iRodsFile = null;
      File currentFile;
      Long currentRecordId;
      String iRODSAbsolutePathFilename;
      for (Map.Entry<Long, File> recordFileEntry : mapRecordIdToFile.entrySet()) {
        currentRecordId = recordFileEntry.getKey();
        currentFile = recordFileEntry.getValue();
        iRODSAbsolutePathFilename = pathToFiles + '/' + currentFile.getName();
        try {
          iRodsFile = irodsFileFactory.instanceIRODSFile(iRODSAbsolutePathFilename);
          dtoIRODS.putOperation(currentFile, iRodsFile, null, null);
          if (iRodsFile.exists()) {
            Long irodsFileId =
                (long) getIrodsDataObject(new NfsTarget(iRODSAbsolutePathFilename)).getId();
            result.add(
                new ApiExternalStorageOperationInfo(
                    currentRecordId, irodsFileId, currentFile.getName(), true));
            log.info(
                "File [{}] successfully copied into IRODS path [{}]",
                currentFile.getName(),
                pathToFiles);
          } else {
            result.add(
                new ApiExternalStorageOperationInfo(
                    currentRecordId,
                    currentFile.getName(),
                    false,
                    "Unknown error copying file into IRODS"));
            log.error(
                "File [{}] failed to be copied into IRODS path [{}]",
                currentFile.getName(),
                pathToFiles);
          }
        } catch (JargonException ex) {
          result.add(
              new ApiExternalStorageOperationInfo(
                  currentRecordId, currentFile.getName(), false, ex.getMessage()));
          log.error(
              "File [{}] failed to be copied into IRODS path [{}]",
              currentFile.getName(),
              pathToFiles,
              ex);
        }
      }
    } catch (JargonException e) {
      log.error("An error occurred while processing files to copy into IRODS", e);
      throw new UnsupportedOperationException(e.getMessage());
    } finally {
      if (iRodsFileSystem != null) {
        iRodsFileSystem.closeAndEatExceptions();
      }
    }
    return result;
  }

  @Override
  public boolean deleteFilesFromNfs(Set<String> absolutePathFilenames)
      throws UnsupportedOperationException {
    IRODSFileSystem iRodsFileSystem = null;
    boolean success = true;
    try {
      iRodsFileSystem = IRODSFileSystem.instance();
      IRODSAccessObjectFactory accessObjectFactory = iRodsFileSystem.getIRODSAccessObjectFactory();
      IRODSFileFactory irodsFileFactory = accessObjectFactory.getIRODSFileFactory(irodsAccount);

      IRODSFile iRodsFile = null;
      for (String absolutePathName : absolutePathFilenames) {
        iRodsFile = irodsFileFactory.instanceIRODSFile(absolutePathName);
        if (iRodsFile.exists()) {
          success = iRodsFile.delete();
          if (!success) {
            throw new UnsupportedOperationException(
                "The file " + absolutePathName + " could not be deleted from IRODS");
          }
        }
      }
    } catch (JargonException e) {
      throw new UnsupportedOperationException(e.getMessage());
    } finally {
      if (iRodsFileSystem != null) {
        iRodsFileSystem.closeAndEatExceptions();
      }
    }
    return success;
  }

  public boolean iRodsFileExists(String absolutePathFilename) throws UnsupportedOperationException {
    IRODSFileSystem iRodsFileSystem = null;
    try {
      iRodsFileSystem = IRODSFileSystem.instance();
      IRODSAccessObjectFactory accessObjectFactory = iRodsFileSystem.getIRODSAccessObjectFactory();
      IRODSFileFactory irodsFileFactory = accessObjectFactory.getIRODSFileFactory(irodsAccount);

      return irodsFileFactory.instanceIRODSFile(absolutePathFilename).exists();
    } catch (JargonException e) {
      throw new UnsupportedOperationException(e.getMessage());
    } finally {
      if (iRodsFileSystem != null) {
        iRodsFileSystem.closeAndEatExceptions();
      }
    }
  }

  private DataObject getIrodsDataObject(NfsTarget nfsTarget) throws JargonException {
    DataObject irodsDataObject = null;
    if (nfsTarget.getNfsId() != null) {
      irodsDataObject = jargonFacade.getIRODSDataObjectById(nfsTarget.getNfsId(), irodsAccount);
    } else {
      irodsDataObject = jargonFacade.getIRODSDataObjectByPath(nfsTarget.getPath(), irodsAccount);
    }
    return irodsDataObject;
  }

  private Collection getIrodsCollection(NfsTarget nfsTarget) throws JargonException {
    Collection irodsCollection = null;
    if (nfsTarget.getNfsId() != null) {
      irodsCollection = jargonFacade.getIRODSCollectionById(nfsTarget.getNfsId(), irodsAccount);
    } else {
      irodsCollection = jargonFacade.getIRODSCollectionByPath(nfsTarget.getPath(), irodsAccount);
    }
    return irodsCollection;
  }
}
