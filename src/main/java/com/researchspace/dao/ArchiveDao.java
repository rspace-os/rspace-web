package com.researchspace.dao;

import com.researchspace.model.ArchivalCheckSum;
import java.util.List;

public interface ArchiveDao extends GenericDao<ArchivalCheckSum, String> {

  /**
   * Gets archive rws that have not expired yet. The reason we can't just delete expired
   * ArchiveChecksums outright is that they are needed to validate archives when they are reimported
   * into the database.
   *
   * @return A {@link List} of {@link ArchivalCheckSum} objects.
   */
  public List<ArchivalCheckSum> getUnexpiredArchives();
}
