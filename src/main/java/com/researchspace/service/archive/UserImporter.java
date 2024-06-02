package com.researchspace.service.archive;

import com.researchspace.archive.ArchivalImportConfig;
import com.researchspace.archive.IArchiveModel;
import com.researchspace.archive.model.ArchiveUsers;
import com.researchspace.model.User;
import com.researchspace.service.UserExistsException;

/** Saves user information read from an XML export as new users */
public interface UserImporter {

  /**
   * @param importer The {@link User} performing the import
   * @param fromXml representation of od users.xml file
   * @param report The {@link ImportArchiveReport}, to add warnings/messages etc
   * @throws UserExistsException if a user in the archive already exists in RSpace.
   */
  void saveArchiveUsersToDatabase(User importer, ArchiveUsers fromXml, ImportArchiveReport report)
      throws UserExistsException;

  /**
   * @param archiveModel The parsed archive
   * @param iconfig The import configuration
   * @param importer The {@link User} performing the import
   * @param report The {@link ImportArchiveReport}, to add warnings/messages etc
   * @throws Exception
   */
  void createUsers(
      IArchiveModel archiveModel,
      ArchivalImportConfig iconfig,
      User importer,
      ImportArchiveReport report)
      throws Exception;
}
