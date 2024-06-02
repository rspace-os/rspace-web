package com.researchspace.archive.model;

import com.researchspace.archive.ArchivalLinkResolver;
import com.researchspace.archive.ArchivalMeta;
import com.researchspace.core.util.progress.ProgressMonitor;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.netfiles.NfsClient;
import com.researchspace.repository.spi.IRepository;
import java.io.File;
import java.util.Map;
import java.util.Set;

/** Immutable methods for accessing archiving configuration. */
public interface IArchiveExportConfig extends IExportConfig {

  /**
   * User entered description of data to be exported.
   *
   * @return
   */
  String getDescription();

  /**
   * Get the archive type, by default this is XML
   *
   * @return
   */
  String getArchiveType();

  boolean isELNArchive();

  /**
   * Gets the maximum ndepth of links to follow. The default is 1 - one level of linked records will
   * be followed
   *
   * @return
   */
  int getMaxLinkLevel();

  /**
   * Getter for the folder in which the archive will be generated.
   *
   * @return A {@link File} that is a folder.
   */
  File getTopLevelExportFolder();

  /**
   * Whether or not to export the revision history, or just the current version.
   *
   * @return
   */
  boolean isAllVersion();

  ArchivalMeta getArchivalMeta();

  /**
   * The user performing the export operation.
   *
   * @return
   */
  User getExporter();

  ArchivalLinkResolver getResolver();

  /**
   * Whether this archive is for a repository deposit ( in which case we need to include extra METS
   * information
   *
   * @return
   */
  boolean isDeposit();

  /**
   * If true, configures export to export XML; if false, configured to export HTML
   *
   * @return
   */
  boolean isArchive();

  String generateDocumentExportFileName(String documentFileName);

  /**
   * Gets a global identifier of the user/group to export. The type of this identifier should agree
   * with the ArchiveScope
   *
   * @return
   */
  GlobalIdentifier getUserOrGroupId();

  /**
   * Gets a DataRepository to handle depositions of data.
   *
   * @return
   */
  IRepository getRepository();

  /**
   * @return true if institutional filestore links should be downloaded and included in archive file
   */
  boolean isIncludeNfsLinks();

  /**
   * @return the filesize limit for institutional filestore files added to archive
   */
  long getMaxNfsFileSize();

  /**
   * @return array of lower-case file extensions that should be skipped when processing filestore
   *     file links
   */
  Set<String> getExcludedNfsFileExtensions();

  /**
   * @return map of nfs clients used by exporting user
   */
  Map<Long, NfsClient> getAvailableNfsClients();

  ProgressMonitor getProgressMonitor();
}
