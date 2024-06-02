package com.researchspace.dao;

import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.netfiles.NfsFileSystem;
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
}
