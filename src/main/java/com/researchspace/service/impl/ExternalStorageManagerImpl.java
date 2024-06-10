package com.researchspace.service.impl;

import com.researchspace.dao.ExternalStorageDao;
import com.researchspace.model.User;
import com.researchspace.model.netfiles.ExternalStorageLocation;
import com.researchspace.service.ExternalStorageManager;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Implementation of ExternalStorageManager interface.
 *
 * @author nico
 */
@Service
public class ExternalStorageManagerImpl implements ExternalStorageManager {

  @Autowired private ExternalStorageDao externalStorageDao;

  public ExternalStorageLocation saveExternalStorageLocation(
      ExternalStorageLocation externalStorageLocation) {
    return externalStorageDao.save(externalStorageLocation);
  }

  @Override
  public List<ExternalStorageLocation> getAllExternalStorageLocations() {
    return externalStorageDao.getAll();
  }

  @Override
  public List<ExternalStorageLocation> getAllExternalStorageLocationsByUser(User operationUser) {
    return externalStorageDao.getAllByOperationUser(operationUser.getId());
  }
}
