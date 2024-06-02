package com.researchspace.dao;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.FileProperty;
import com.researchspace.model.FileStoreRoot;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Stores/retrieves FileProperty info and fileUsage querues */
public interface FileMetadataDao extends GenericDao<FileProperty, Long> {

  /**
   * @param wheres: pair of Key: column name(same as variable name) and value for where clause
   * @return List of FileProperty
   */
  List<FileProperty> findProperties(Map<String, String> wheres);

  /**
   * Runs through FileProperties owned by the user and collects File references to the resources.
   *
   * @return non-null but possibly empty list of Files pointing to user resources,
   */
  List<File> collectUserFilestoreResources(User user);

  /**
   * GEts file usage for a single user
   *
   * @return a Long of the size in bytes of tota files owned by that user.
   */
  Long getTotalFileUsageForUser(User user);

  /**
   * Gets file usage for the specified Collections of Users.
   *
   * @param user
   * @param pgCrit Pagination criteria.
   * @return
   */
  Map<String, DatabaseUsageByUserGroupByResult> getTotalFileUsageForUsers(
      Collection<User> user, PaginationCriteria<User> pgCrit);

  /**
   * Gets a paginated list of file usage
   *
   * @param pgCrit An optional PaginationCriteria
   * @return A Map<String, FileUsage> where the key is the username and the value is the file usage
   *     in the file repository for that user obtained by getUsage in the value object
   */
  Map<String, DatabaseUsageByUserGroupByResult> getTotalFileUsageForAllUsers(
      PaginationCriteria<User> pgCrit);

  /**
   * GEts a a count of the number of users that have files put in the filesystem (i.e., it omits
   * those users who have been created but not initialised with example media files.
   *
   * @return
   */
  Long getCountOfUsersWithFilesInFileSystem();

  /**
   * Gets total file usage, in bytes as a sum of file sizes in the FileProperty table.
   *
   * @return a Long &gt;= 0
   */
  Long getTotalFileUsage();

  /**
   * Gets total file usage for a group, in bytes as a sum of file sizes in the FileProperty table.
   *
   * @param group
   * @return a Long &gt;= 0
   */
  Long getTotalFileUsageForGroup(Group group);

  /**
   * Gets total fileUsage for each group,
   *
   * @param pgCrit a {@link PaginationCriteria}
   * @return
   */
  ISearchResults<DatabaseUsageByGroupGroupByResult> getTotalFileUsageForLabGroups(
      PaginationCriteria<Group> pgCrit);

  /**
   * Given a list of groups, will retrieve file usage information for them.
   *
   * @param grps can be empty but not <code>null</code>
   * @return
   */
  List<DatabaseUsageByGroupGroupByResult> getTotalFileUsageForGroups(Collection<Group> grps);

  /**
   * Given an absolute path, retrieves the {@link FileStoreRoot} for that path, or <code>null</code>
   * if not found.
   *
   * @param fileStorePath
   * @return A {@link FileStoreRoot} object or <code>null</code>.
   */
  FileStoreRoot findByFileStorePath(String fileStorePath);

  /**
   * Saves or updates a {@link FileStoreRoot} object
   *
   * @param root
   * @return a {@link FileStoreRoot}
   */
  FileStoreRoot saveFileStoreRoot(FileStoreRoot root);

  /**
   * Sets current = false across all file store roots
   *
   * @param external <code>true</code> if we're altering an external FS, <code>false</code>
   *     otherwise
   */
  void resetCurrentFileStoreRoot(boolean external);

  /**
   * Gets the current FileStoreRoot if set * @param external <code>true</code> if we're altering an
   * external FS, <code>false</code> otherwise
   *
   * @return
   */
  FileStoreRoot getCurrentFileStoreRoot(boolean external);
}
