package com.researchspace.dao;

import com.researchspace.model.netfiles.ExternalStorageLocation;
import java.util.List;

/**
 * Data Access Object for external storage locations
 *
 * @author nico
 */
public interface ExternalStorageDao extends GenericDao<ExternalStorageLocation, Long> {

  List<ExternalStorageLocation> getAllByOperationUser(Long operationUserId);
}
