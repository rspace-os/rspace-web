package com.researchspace.archive;

import java.io.File;

/**
 * Wrapper object to hold the references of unmarshaled XML StructuredDocument object and associated
 * files.
 */
public class ArchivalGalleryMetaDataParserRef extends AbstractArchivalParserRef {

  private ArchivalGalleryMetadata galleryXML;

  public ArchivalGalleryMetadata getGalleryXML() {
    return galleryXML;
  }

  public void setGalleryXML(ArchivalGalleryMetadata gelleryXML) {
    this.galleryXML = gelleryXML;
  }

  public boolean isMedia() {
    return true;
  }

  /**
   * GEts the media file encapsulated in the archive subfolder. Assumes there is only 1 file per
   * folder.
   *
   * @return A {@link File} or <code>null</code> if there is not an attachment file
   */
  public File getMediaFile() {
    if (!getFileList().isEmpty()) {
      return getFileList().get(0);
    } else {
      return null;
    }
  }
}
