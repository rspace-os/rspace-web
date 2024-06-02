package com.researchspace.archive;

public enum ArchiveImportScope {

  /**
   * When importing, any information about users and groups will be ignored. All records will be
   * imported into a single user account.
   */
  IGNORE_USERS_AND_GROUPS,

  /**
   * If user data or group data is present i nthe archive, attempt to create these use accounts and
   * groups, or use existing groups/users with the same name. NOTE - currently enabling this value
   * would result in the Group importer being stored in the DB as the Creator of all of those Groups
   * - see UserImporter.saveArchiveUsersToDatabase
   */
  CREATE_USERS_AND_GROUPS;
}
