package com.researchspace.service.inventory;

import com.researchspace.api.v1.controller.InventoryImportPostFullValidator.ApiInventoryImportPostFull;
import com.researchspace.api.v1.controller.SamplesApiController;
import com.researchspace.api.v1.model.ApiInventoryImportParseResult;
import com.researchspace.api.v1.model.ApiInventoryImportResult;
import com.researchspace.api.v1.model.ApiInventoryImportSampleParseResult;
import com.researchspace.model.User;
import java.io.IOException;
import java.io.InputStream;

/** To deal with inventory import requests. */
public interface InventoryImportManager {

  /**
   * Parse csv file and return suggested sample template + other information
   *
   * @param filename
   * @param inputStream
   * @param createdBy
   * @return
   * @throws IOException
   */
  ApiInventoryImportSampleParseResult parseSamplesCsvFile(
      String filename, InputStream inputStream, User createdBy) throws IOException;

  ApiInventoryImportParseResult parseContainersCsvFile(InputStream inputStream, User createdBy)
      throws IOException;

  ApiInventoryImportParseResult parseSubSamplesCsvFile(InputStream inputStream, User createdBy)
      throws IOException;

  ApiInventoryImportResult importInventoryCsvFiles(
      ApiInventoryImportPostFull importPostFull, User user) throws IOException;

  /*
   * for testing
   */
  void setSamplesController(SamplesApiController samplesController);
}
