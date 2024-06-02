package com.researchspace.files.service;

import com.researchspace.model.FileProperty;
import com.researchspace.model.FileStoreRoot;
import com.researchspace.model.User;
import com.researchspace.service.FileDuplicateStrategy;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * Repository manager interface for add, retrieve, update and delete. It might be operation locally
 * or remotely, should be work both file and stream
 *
 * <p>Usage:
 *
 * <ol>
 *   <li>Configure the filestore baseDir by setting environment variable RS_FILE_BASE
 *   <li>construct FileProperty object, four essential fields: category: pdf, image, video, etc,
 *       group: research, lab name etc. you could import another words user: take for system,
 *       version fill with system, but you could import any words as well
 *   <li>then call FileStore methods to store/retrieve data/file
 *   <li>Insert/Update using saving, existCd will indicate if file existed, you will do: -1 return
 *       error, 0 override, 1 save as new one
 *   <li>Retrieve has two steps, using find to get FileProperty object, using the retrieved
 *       property, user are not allow directly access file. Should be always via property.
 *   <li>Unit test: FileStoreTest in service folder as example
 * </ol>
 */
public interface FileStore {
  /**
   * Save a file to the path to be calculated from {@link FileProperty}.
   *
   * @param fileProperty, a new constructed FileProperty,
   * @param sourceFile, specify the resource/file location
   * @param behaviourOnDuplicate, a {@link FileDuplicateStrategy}
   * @return new file URI in file store.
   */
  URI save(FileProperty fileProperty, File sourceFile, FileDuplicateStrategy behaviourOnDuplicate)
      throws IOException;

  /**
   * Saves the contents of the input stream to a new file, whose path is calculated from <code>
   * fileProperty</code>
   *
   * @param fileProperty: File property, should be filled
   * @param inStream, inputStream on the file to be stored.
   * @param fileName: stored file name not include path
   * @param behaviourOnDuplicate, a {@link FileDuplicateStrategy}
   * @return URI of file store, or <code>null</code> if already exists and <code>
   *     behaviourOnDuplicate</code> is FileDuplicateStrategy.ERROR
   */
  URI save(
      FileProperty fileProperty,
      InputStream inStream,
      String fileName,
      FileDuplicateStrategy behaviourOnDuplicate)
      throws IOException;

  /**
   * Gets an input stream to the resource. It is the caller's responsibility to close the stream
   * once finished.
   *
   * @param meta
   * @return A possibly <code>null</code> {@link FileInputStream} to the file referenced from the
   *     FileProperty
   */
  Optional<FileInputStream> retrieve(FileProperty meta);

  /**
   * Given a FileProperty object, will attempt to find file in FileStore based on FileProperty
   * object. This method differs from find(Map) in that it doesn't do a DB lookup, it looks straight
   * into FS based on FileProperty.
   *
   * @param meta
   * @return A File, which might or might not exist. Client should call exists(meta) if you want to
   *     know if a file exists.
   * @throws IOException
   */
  File findFile(FileProperty meta) throws IOException;

  /**
   * Boolean test for whether file with path based on these properties exists. This method will
   * internally set the
   *
   * @param meta
   * @return
   * @throws IOException
   */
  boolean exists(FileProperty meta) throws IOException;

  /**
   * Gets the current FileStoreRoot that is the root path of all filesystem properties.
   *
   * @return
   */
  FileStoreRoot getCurrentFileStoreRoot();

  /**
   * Gets the current local file store root. If external file systems are <b>not</b> configured,
   * this will return the same object as getCurrentFileStoreRoot();
   *
   * @return
   */
  FileStoreRoot getCurrentLocalFileStoreRoot();

  /**
   * Removes file from fileSystem. Note: currently has a dummy implementation, which doesn't remove
   * anything.
   *
   * @param fileProperty
   * @return true if file was removed
   */
  boolean removeFile(FileProperty fileProperty);

  /**
   * Physically deletes listed files from filestore. To be called after user deletion.
   *
   * @param filestoreFiles list of filestore files
   * @return number of successfully deleted files, null if empty list or other problem
   */
  Optional<Integer> removeUserFilestoreFiles(List<File> filestoreFiles);

  /** Checks if the list contains files that seems to be regular filestore files. */
  boolean verifyUserFilestoreFiles(List<File> filestoreFiles);

  /**
   * Facade to generate the FileProperty and save it on the file store. <br>
   * <strong>Note</strong> this is not implemented yet for external file stores (but we are not
   * using these in production yet)/
   *
   * @param fileCategory
   * @param user
   * @param originalFileName
   * @param inputStream
   * @return FileProperty the persisted FileProperty
   * @throws IOException
   */
  FileProperty createAndSaveFileProperty(
      String fileCategory, User user, String originalFileName, InputStream inputStream)
      throws IOException;
}
