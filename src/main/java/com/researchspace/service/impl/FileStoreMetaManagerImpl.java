package com.researchspace.service.impl;

import com.researchspace.dao.FileMetadataDao;
import com.researchspace.model.FileProperty;
import com.researchspace.model.FileStoreRoot;
import com.researchspace.service.FileStoreMetaManager;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FileStoreMetaManagerImpl extends GenericManagerImpl<FileProperty, Long>
    implements FileStoreMetaManager {

  private FileMetadataDao fileDao;

  public FileStoreMetaManagerImpl(@Autowired FileMetadataDao userDao) {
    this.dao = userDao;
    this.fileDao = userDao;
  }

  @Override
  public FileStoreRoot getCurrentFileStoreRoot(boolean external) {
    return fileDao.getCurrentFileStoreRoot(external);
  }

  @Override
  public FileStoreRoot saveFileStoreRoot(FileStoreRoot root) {
    return fileDao.saveFileStoreRoot(root);
  }

  /**
   * Retrieve FileProperty
   *
   * @param wheres, key=column. For using Constants of FileProperty,
   * @return a set of FileProperty objects
   */
  @Override
  public List<FileProperty> findProperties(Map<String, String> searchCriteria) {
    return fileDao.findProperties(searchCriteria);
  }
}
