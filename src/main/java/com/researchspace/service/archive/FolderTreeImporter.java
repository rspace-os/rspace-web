package com.researchspace.service.archive;

import com.researchspace.archive.ArchivalImportConfig;
import com.researchspace.archive.IArchiveModel;
import com.researchspace.model.record.Folder;
import com.researchspace.service.RecordContext;
import java.io.File;
import java.util.Map;

/** Handles folder tree generation from exports that contain a folderTree.xml document. */
public interface FolderTreeImporter {

  /**
   * Creates a folder tree, saving new folders to the database, returning a map of oldIdToNewFolder
   * (the new folders will have the new database ID).
   *
   * @param folderTree the folderTree.xml file
   * @param iconfig The {@link ArchivalImportConfig}
   * @param archive a read-only model of the archive
   * @param report
   * @return A Map.
   */
  Map<Long, Folder> createFolderTree(
      File folderTree,
      RecordContext context,
      ArchivalImportConfig iconfig,
      IArchiveModel archive,
      ImportArchiveReport report);
}
