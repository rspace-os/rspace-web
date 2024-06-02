/**
 * RSpace Inventory API Access your RSpace Inventory programmatically. All requests require
 * authentication.
 */
package com.researchspace.api.v1;

import com.researchspace.api.v1.controller.InventoryImportApiController.ApiInventoryImportSettingsPost;
import com.researchspace.api.v1.model.ApiInventoryImportParseResult;
import com.researchspace.api.v1.model.ApiInventoryImportResult;
import com.researchspace.model.User;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;

@RequestMapping("/api/inventory/v1/import")
public interface InventoryImportApi {

  /**
   * @param file csv file with samples data
   * @param user current user
   * @return sample template that'll be created for the data
   */
  @PostMapping(path = "/parseFile")
  ApiInventoryImportParseResult parseImportFile(MultipartFile file, String fileType, User user)
      throws BindException;

  /**
   * @param samplesFile csv file with samples data
   * @param subSamplesFile csv file with subsamples data
   * @param containersFile csv file with containers data
   * @param settings import settings
   * @param user current user
   * @return result object
   */
  @PostMapping(path = "/importFiles")
  ApiInventoryImportResult importFromCsv(
      MultipartFile containersFile,
      MultipartFile samplesFile,
      MultipartFile subSamplesFile,
      ApiInventoryImportSettingsPost settings,
      User user)
      throws BindException;
}
