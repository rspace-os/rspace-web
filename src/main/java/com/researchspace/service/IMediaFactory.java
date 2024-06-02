package com.researchspace.service;

import com.researchspace.model.*;
import com.researchspace.model.record.ImportOverride;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Factory methods for creating media document objects */
public interface IMediaFactory {

  /**
   * Generates an EcatDocumentFile.
   *
   * @param user A User that owns the EcatDocumentFile
   * @param fprop A FileProperty that refers to the file stored in the filestore
   * @param extensionType A String with the extension of the file
   * @param documentType A String with Document or Miscellaneous as a value
   * @param fileName A String with the filename
   * @return A EcatDocumentFile
   */
  EcatDocumentFile generateEcatDocument(
      User user,
      FileProperty fprop,
      String extensionType,
      String documentType,
      String fileName,
      ImportOverride override);

  /**
   * Generates an EcatAudio.
   *
   * @param user A User that owns the EcatAudio
   * @param fprop A FileProperty that refers to the file stored in the filestore
   * @param extensionType A String with the extension of the file
   * @param filename A String with the filename
   * @return A EcatAudio
   */
  EcatAudio generateEcatAudio(
      User user,
      FileProperty fprop,
      String extensionType,
      String filename,
      ImportOverride override);

  /**
   * Generates an EcatVideo.
   *
   * @param user A User that owns the Ecatvideo
   * @param fprop A FileProperty that refers to the file stored in the filestore
   * @param extensionType A String with the extension of the file
   * @param filename A String with the filename
   * @param override
   * @return A EcatVideo
   */
  EcatVideo generateEcatVideo(
      User user,
      FileProperty fprop,
      String extensionType,
      String filename,
      ImportOverride override);

  /**
   * Generates an EcatImage and its ImageThumbnailed and imageResized if it is needed.
   *
   * @param user A User that owns the EcatImage
   * @param fprop A FileProperty that refers to the file stored in the filestore
   * @param tempFile A File of the image it will save
   * @param extensionType A String with the extension of the file
   * @param filename A String with the filename
   * @param override - nullable
   * @return A EcatImage
   * @throws IOException
   */
  EcatImage generateEcatImage(
      User user,
      FileProperty fprop,
      File tempFile,
      String extensionType,
      String filename,
      ImportOverride override)
      throws IOException;

  /**
   * Generates an EcatChemistryFile and its ImageThumbnailed and imageResized if it is needed.
   *
   * @param user A User that owns the EcatChemistryFile
   * @param fprop A FileProperty that refers to the file stored in the filestore
   * @param extensionType A String with the extension of the file
   * @param filename A String with the filename
   * @param override
   * @return A EcatImage
   * @throws IOException
   */
  EcatChemistryFile generateEcatChemistryFile(
      User user, FileProperty fprop, String extensionType, String filename, ImportOverride override)
      throws IOException;

  void updateEcatImageWithUploadedFileDetails(
      EcatImage ecatImage, File imageFile, FileProperty fProp, String extension)
      throws IOException, FileNotFoundException;

  /**
   * Getter for max image in memory size allowed for tiff conversion
   *
   * @return
   */
  Long getMaxImageMemorySize();

  /**
   * Given a filename such as "myDocument.docx" retrieves the bytes of an icon for that file suffix
   * 'docx.png', if it exists. If there is no icon, returns
   * EcatMediaFactory.DEFAULT_GALLERY_ICON_PNG (shows a question mark)
   *
   * @param filename
   * @return
   * @throws IOException if no icon can be loaded. This unlikely as the fallback icon is always
   *     present
   */
  byte[] getFileSuffixIcon(String filename) throws IOException;
}
