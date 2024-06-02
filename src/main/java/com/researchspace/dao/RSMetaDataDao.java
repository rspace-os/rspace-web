package com.researchspace.dao;

import com.researchspace.model.ArchiveVersionToAppVersion;
import com.researchspace.model.RSMetaData;
import com.researchspace.model.Version;
import java.util.List;

public interface RSMetaDataDao extends GenericDao<RSMetaData, Long> {

  List<ArchiveVersionToAppVersion> getArchiveVersionsToAppVersion();

  /**
   * @param archiveVersion
   * @param schemaName one of the attributeOverrides column names defined in {@link
   *     ArchiveVersionToAppVersion}
   * @return
   */
  ArchiveVersionToAppVersion getAppVersionForArchiveVersion(
      Version archiveVersion, String schemaName);
}
