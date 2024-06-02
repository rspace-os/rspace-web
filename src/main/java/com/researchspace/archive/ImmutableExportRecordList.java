package com.researchspace.archive;

import com.researchspace.model.core.GlobalIdentifier;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Read only methods of ExportRecordList. Standard implementation is expected to be
 * ExportRecordList.
 */
public interface ImmutableExportRecordList {

  List<GlobalIdentifier> getRecordsToExport();

  Set<GlobalIdentifier> getAssociatedFieldAttachments();

  List<ArchiveFolder> getFolderTree();

  /**
   * Ignores any version information - permissions do not vary depending on version
   *
   * @param rcdId
   * @return
   */
  boolean containsFieldAttachment(GlobalIdentifier rcdId);

  /**
   * Boolean test as to whether the list of recordsToExport contains the given <code>
   * GlobalIdentifier</code>
   *
   * @param rcdId
   * @return
   */
  boolean containsRecord(GlobalIdentifier rcdId);

  boolean containsFolder(Long id);

  /**
   * Gets 1st record id to export
   *
   * @return
   */
  GlobalIdentifier getFirstRecordToExport();

  /**
   * number of records in export list
   *
   * @return
   */
  int getRecordsToExportSize();

  /**
   * Analyzes the list of ArchiveFolders to find folders whose parent Id is either null or is not
   * included in this folder list;
   *
   * @return A subset of of <code> getFolderTree()</code>which are top-level folders.
   */
  List<ArchiveFolder> getTopLevelFolders();

  /**
   * Analyzes the list of ArchiveFolders to find folders whose parent Id is the argument Id.
   *
   * @param parentId
   * @return a possibly empty but non-null list.
   */
  List<ArchiveFolder> getChildren(Long parentId);

  /** Gets the Archive folder with the given ID, as an Optional. */
  Optional<ArchiveFolder> getArchiveFolder(Long id);
}
