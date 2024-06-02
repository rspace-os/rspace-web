package com.researchspace.dao;

import com.researchspace.model.record.BaseRecord;
import java.util.List;

/**
 * Performs queries to check the data integrity, that are not covered by RDMS relational constraints
 */
public interface DBIntegrityDAO {

  List<BaseRecord> getOrphanedRecords();

  List<Long> getTemporaryFavouriteDocs();
}
