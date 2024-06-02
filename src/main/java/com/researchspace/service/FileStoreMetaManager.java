package com.researchspace.service;

import com.researchspace.model.FileProperty;
import com.researchspace.model.FileStoreRoot;
import java.util.List;
import java.util.Map;

/** Wraps filestore metadata DB actions in a transaction */
public interface FileStoreMetaManager extends GenericManager<FileProperty, Long> {

  FileStoreRoot getCurrentFileStoreRoot(boolean external);

  FileStoreRoot saveFileStoreRoot(FileStoreRoot root);

  /**
   * @param wheres: pair of Key: column name(same as variable name) and value for where clause
   * @return List of FileProperty
   */
  public List<FileProperty> findProperties(Map<String, String> wheres);
}
