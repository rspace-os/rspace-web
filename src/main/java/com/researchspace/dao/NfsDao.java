package com.researchspace.dao;

import com.researchspace.model.User;
import com.researchspace.model.netfiles.NfsFieldLink;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.model.record.StructuredDocument;
import java.util.Collection;
import java.util.List;

/** Data Access Object for net filestores and filesystems */
public interface NfsDao {

  /** Retrieve, save and delete network folders associated with a user */
  NfsFileStore getNfsFileStore(Long id);

  void saveNfsFileStore(NfsFileStore fileStore);

  void deleteNfsFileStore(NfsFileStore fileStore);

  /** For bulk operations on file stores. */
  List<NfsFileStore> getFileStores();

  /**
   * @param userId
   * @return not deleted file stores belonging to the user
   */
  List<NfsFileStore> getUserFileStores(Long userId);

  /**
   * @return lists all file systems
   */
  List<NfsFileSystem> getFileSystems();

  /**
   * @return list of active file systems
   */
  List<NfsFileSystem> getActiveFileSystems();

  /**
   * @return file system with a given id
   */
  NfsFileSystem getNfsFileSystem(Long id);

  /**
   * creates/updates file system
   *
   * @param fileSystem
   */
  void saveNfsFileSystem(NfsFileSystem fileSystem);

  /**
   * delete File System (only if it's not used by any Filestore yet).
   *
   * @param id of fileSystem to delete
   * @return whether fileSystem was successfully deleted
   */
  boolean deleteNfsFileSystem(Long id);

  List<NfsFieldLink> getFieldLinks(Long fieldId);

  void saveFieldLink(NfsFieldLink link);

  void deleteFieldLink(NfsFieldLink link);

  /**
   * Current, non-deleted documents with a text field linking any of the normalised absolute paths
   * on the filesystem, by ascending id. An empty collection of paths returns an empty list without
   * querying.
   *
   * @param filestoreOwner null for links through any user's filestore; otherwise only links through
   *     this user's filestores whose base and link path together start with a slash ({@code
   *     rootedBase} true) or without one (false), which SFTP reads under the login's home
   * @param rootedBase required with an owner, ignored without one
   */
  List<StructuredDocument> getDocumentsLinkingTo(
      Long fileSystemId, Collection<String> absolutePaths, User filestoreOwner, Boolean rootedBase);
}
