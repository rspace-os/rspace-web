package com.researchspace.service.inventory;

import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.service.inventory.csvexport.CsvContentToExport;
import com.researchspace.service.inventory.csvexport.CsvExportMode;
import java.io.IOException;
import java.util.List;

/** To deal with inventory export requests. */
public interface InventoryExportManager {

  /**
   * Returns CSV content describing items from provided list of globalIds.
   *
   * @param globalIds
   * @param exportMode
   * @param includeSampleContent (optional) whether sample's subsamples should also be exported
   * @param user
   * @return string representing CSV content with exported items
   * @throws IOException
   */
  CsvContentToExport exportSelectedItemsAsCsvContent(
      List<GlobalIdentifier> globalIds,
      CsvExportMode exportMode,
      boolean includeSampleContent,
      boolean includeContainerContent,
      User user)
      throws IOException;

  /**
   * Returns CSV content describing all items owned by users from provided list of usernames
   *
   * @param usersToExport
   * @param exportMode
   * @param includeContainerContent
   * @param user
   * @return string representing CSV content with exported items
   * @throws IOException
   */
  CsvContentToExport exportUserItemsAsCsvContent(
      List<String> usersToExport,
      CsvExportMode exportMode,
      boolean includeContainerContent,
      User user)
      throws IOException;
}
