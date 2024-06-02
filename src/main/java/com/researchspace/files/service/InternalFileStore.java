package com.researchspace.files.service;

import com.researchspace.model.FileStoreRoot;
import java.io.File;
import java.io.IOException;

/** Marker interface to discriminate between Internal and Composite file stores */
public interface InternalFileStore extends FileStore {

  /** File store category for thumbnails for Thumbnail objects */
  public static final String THUMBNAIL_THUMBNAIL_CATEGORY = "thumbnail";

  /** File store category for thumbnails for EcatDocumentFile */
  public static final String DOC_THUMBNAIL_CATEGORY = "ecatDoc-thumbnail";

  /** File store category for working images for EcatImage */
  public static final String IMG_WORKING_CATEGORY = "ecatImage-working";

  /** File store category for thumbnails for EcatImage */
  public static final String IMG_THUMBNAIL_CATEGORY = "ecatImage-thumbnail";

  /**
   * Sets the root folder of the FileStore. In application usage, this should not be changed, but is
   * intended for test code.
   *
   * @param baseDir A readable folder
   * @throws IOException
   */
  void setBaseDir(File baseDir) throws IOException;

  /**
   * To be called during application initialisation. Determines the current filestore root based on
   * system or environment property supplied at runtime.
   *
   * @return The current filestore root.
   */
  FileStoreRoot setupInternalFileStoreRoot();
}
