package com.researchspace.service;

import com.researchspace.model.AppVersion;
import com.researchspace.model.RSMetaData;
import com.researchspace.model.Version;

public interface RSMetaDataManager extends GenericManager<RSMetaData, Long> {

  /**
   * Gets the latest version of this database.
   *
   * @return
   */
  public AppVersion getDatabaseVersion();

  /**
   * Boolean test for whether the specified version of the schema can be imported into the database.
   * This test is solely based on comparison of Version numbers, not on any inspection of the data
   *
   * @param schemaName
   * @param version
   * @return
   */
  boolean isArchiveImportable(String schemaName, Version version);
}
