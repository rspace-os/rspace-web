package com.researchspace.netfiles.irods;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.irods.jargon.core.connection.IRODSAccount;
import org.irods.jargon.core.connection.auth.AuthResponse;
import org.irods.jargon.core.exception.JargonException;
import org.irods.jargon.core.pub.CollectionAO;
import org.irods.jargon.core.pub.CollectionAndDataObjectListAndSearchAO;
import org.irods.jargon.core.pub.DataObjectAO;
import org.irods.jargon.core.pub.DataTransferOperations;
import org.irods.jargon.core.pub.IRODSAccessObjectFactory;
import org.irods.jargon.core.pub.IRODSFileSystem;
import org.irods.jargon.core.pub.domain.Collection;
import org.irods.jargon.core.pub.domain.DataObject;
import org.irods.jargon.core.pub.io.IRODSFile;
import org.irods.jargon.core.pub.io.IRODSFileFactory;
import org.irods.jargon.core.pub.io.IRODSFileInputStream;
import org.irods.jargon.core.query.CollectionAndDataObjectListingEntry;
import org.irods.jargon.core.query.MetaDataAndDomainData;

/**
 * This facade provides a simplified interface with the Jargon(iRODS) library needed for the {@link
 * IRODSClient} class
 */
@Slf4j
public class JargonFacade {

  private IRODSAccessObjectFactory accessObjectFactory;

  private enum IRODSFileSystemSingletonHolder {
    INSTANCE();
    IRODSFileSystem irodsFileSystem;

    IRODSFileSystemSingletonHolder() {
      try {
        irodsFileSystem = IRODSFileSystem.instance();
      } catch (JargonException je) {
        log.error("Error Creating IRODSFileSystem Instance");
      }
    }

    public IRODSFileSystem getIRodFileSystem() {
      return irodsFileSystem;
    }
  }

  public JargonFacade() {
    try {
      this.accessObjectFactory =
          IRODSFileSystemSingletonHolder.INSTANCE.getIRodFileSystem().getIRODSAccessObjectFactory();
    } catch (JargonException e) {
      log.error("Error Constructing JargonFacade: ", e);
    }
  }

  public void tryConnectAndReadTarget(String target, IRODSAccount irodsAccount)
      throws JargonException {
    AuthResponse authResponse = accessObjectFactory.authenticateIRODSAccount(irodsAccount);
    if (authResponse.isSuccessful()) {
      getListAllDataObjectsAndCollectionsUnderPath(target, irodsAccount);
    }
  }

  public List<CollectionAndDataObjectListingEntry> getListAllDataObjectsAndCollectionsUnderPath(
      String path, IRODSAccount irodsAccount) throws JargonException {
    path = getAbsoluteIrodsPath(path, irodsAccount);
    CollectionAndDataObjectListAndSearchAO ao =
        accessObjectFactory.getCollectionAndDataObjectListAndSearchAO(irodsAccount);
    return ao.listAllDataObjectsAndCollectionsUnderPath(path);
  }

  public IRODSFile getIRODSFileById(Long id, IRODSAccount irodsAccount) throws JargonException {
    DataObject dataObject = getIRODSDataObjectById(id, irodsAccount);
    IRODSFileFactory fileFactory = accessObjectFactory.getIRODSFileFactory(irodsAccount);
    return fileFactory.instanceIRODSFile(dataObject.getAbsolutePath());
  }

  public IRODSFileInputStream getIRODSFileInputStreamById(Long id, IRODSAccount irodsAccount)
      throws JargonException {
    IRODSFileFactory fileFactory = accessObjectFactory.getIRODSFileFactory(irodsAccount);
    IRODSFile file = getIRODSFileById(id, irodsAccount);
    return fileFactory.instanceSessionClosingIRODSFileInputStream(file);
  }

  public DataObject getIRODSDataObjectByPath(String path, IRODSAccount irodsAccount)
      throws JargonException {
    path = getAbsoluteIrodsPath(path, irodsAccount);
    DataObjectAO ao = accessObjectFactory.getDataObjectAO(irodsAccount);
    return ao.findByAbsolutePath(path);
  }

  public DataObject getIRODSDataObjectById(Long id, IRODSAccount irodsAccount)
      throws JargonException {
    DataObjectAO ao = accessObjectFactory.getDataObjectAO(irodsAccount);
    return ao.findById(Math.toIntExact(id));
  }

  public List<MetaDataAndDomainData> getIRODSDataObjectAVUsByPath(
      String path, IRODSAccount irodsAccount) throws JargonException {
    path = getAbsoluteIrodsPath(path, irodsAccount);
    DataObjectAO ao = accessObjectFactory.getDataObjectAO(irodsAccount);
    return ao.findMetadataValuesForDataObject(path);
  }

  public Collection getIRODSCollectionByPath(String path, IRODSAccount irodsAccount)
      throws JargonException {
    path = getAbsoluteIrodsPath(path, irodsAccount);
    CollectionAO ao = accessObjectFactory.getCollectionAO(irodsAccount);
    return ao.findByAbsolutePath(path);
  }

  public Collection getIRODSCollectionById(Long id, IRODSAccount irodsAccount)
      throws JargonException {
    CollectionAO ao = accessObjectFactory.getCollectionAO(irodsAccount);
    return ao.findById(Math.toIntExact(id));
  }

  public DataTransferOperations getDataTransferOperations(IRODSAccount iRodsAccount)
      throws JargonException {
    return accessObjectFactory.getDataTransferOperations(iRodsAccount);
  }

  private String getAbsoluteIrodsPath(String path, IRODSAccount irodsAccount) {
    if (path.startsWith(irodsAccount.getHomeDirectory())) {
      return path;
    } else {
      return irodsAccount.getHomeDirectory() + path;
    }
  }
}
