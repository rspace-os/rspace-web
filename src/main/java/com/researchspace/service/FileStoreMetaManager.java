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
   * @param wheres: Map of column name to value for where clause
   * @return List of FileProperty matching wheres
   */
  List<FileProperty> findProperties(Map<String, String> wheres);
}
