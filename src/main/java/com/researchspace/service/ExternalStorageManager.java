package com.researchspace.service;

import com.researchspace.model.User;
import com.researchspace.model.netfiles.ExternalStorageLocation;
import java.util.List;

/**
 * Manager for handling external storage locations
 *
 * @author nico
 */
public interface ExternalStorageManager {

  ExternalStorageLocation saveExternalStorageLocation(
      ExternalStorageLocation externalStorageLocation);

  List<ExternalStorageLocation> getAllExternalStorageLocations();

  List<ExternalStorageLocation> getAllExternalStorageLocationsByUser(User operationUser);
}
