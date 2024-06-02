package com.researchspace.service;

import com.researchspace.archive.model.IArchiveExportConfig;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.netfiles.NfsClient;
import com.researchspace.netfiles.NfsExportPlan;
import java.util.List;
import java.util.Map;

/** Methods supporting export of inistitutional filestore files. */
public interface NfsExportManager {

  /**
   * Generates export plan for document list by scanning the documents and extracting nfs links.
   *
   * <p>Also calculates the list of filesystems used by found nfs links, without actually connecting
   * to them.
   *
   * @param docsToExport list of record ids to export
   * @return export plan with foundNfsLinks and foundFileSystems properties set
   */
  NfsExportPlan generateQuickExportPlan(List<GlobalIdentifier> docsToExport);

  /**
   * Tries connecting to file systems found in export plan. Updates plan.foundFileSystems with
   * 'loggedAs' data for file systems that user is actually logged into.
   *
   * @param plan
   * @param nfsClients
   * @param user exporting user (may be possible to auto-log-in into some file systems)
   */
  void checkLoggedAsStatusForFileSystemsInExportPlan(
      NfsExportPlan plan, Map<Long, NfsClient> nfsClients, User user);

  /**
   * Connects to filesystems and retrieve details of every nfs link from export plan object, then
   * checks the archive export config filters (e.g. filesize/type) and add explanation messages for
   * resources that will be skipped.
   *
   * <p>Updates plan.checkedNfsLinks property, and archive size limits properties.
   *
   * @param plan
   * @param nfsClients
   * @param archiveExportConfig
   */
  void scanFileSystemsForFoundNfsLinks(
      NfsExportPlan plan,
      Map<Long, NfsClient> nfsClients,
      IArchiveExportConfig archiveExportConfig);
}
