package com.researchspace.service.archive;

import static com.researchspace.core.util.MediaUtils.isImageFile;
import static com.researchspace.core.util.imageutils.ImageUtils.isTiff;
import static org.apache.commons.io.FilenameUtils.getExtension;

import com.researchspace.archive.ArchivalGalleryMetaDataParserRef;
import java.io.File;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles inconsistencies in export names and file types when tiffs are exported either from
 * Gallery or for whole user export. <br>
 * Gallery or whole-user XML exports of tif images contain a .png file generated during export.
 * During import, this png should not be imported, only the tiff file
 */
@Slf4j
class FileImportConflictResolver {

  private ArchivalGalleryMetaDataParserRef galleryRef;

  FileImportConflictResolver(ArchivalGalleryMetaDataParserRef galleryRef) {
    this.galleryRef = galleryRef;
  }

  public File getFileToImport() {
    File fileToImport = galleryRef.getMediaFile();
    String fileExt = getExtension(fileToImport.getName());

    // short-circuit for non-image files
    if (!isImageFile(fileExt)) {
      return fileToImport;
    }
    // we might have a png filename, but actually want to import a tiff from which
    // a png was generated.
    if (isConflictingFileNameWithImageType(galleryRef, fileExt)) {
      log.info(
          "Conflict between  extension {} and actual filename extension {} ",
          galleryRef.getGalleryXML().getExtension(),
          fileExt);
      // we have a mismatch. IS there is a tif file ?
      Optional<File> tif =
          galleryRef.getFileList().stream()
              .filter(f -> isTiff(getExtension(f.getName())))
              .findFirst();
      if (tif.isPresent()) {
        galleryRef.getGalleryXML().setFileName(tif.get().getName());
        log.info("Will actually import tif file {} ", tif.get().getName());
        return tif.get();
      } else {
        throw new IllegalStateException(
            "There is no tiff file existing for " + galleryRef.getGalleryXML().getFileName());
      }

    } else {
      return fileToImport;
    }
  }

  private boolean isConflictingFileNameWithImageType(
      ArchivalGalleryMetaDataParserRef galleryRef, String fileExt) {
    return isTiff(galleryRef.getGalleryXML().getExtension())
        && isTiff(getExtension(galleryRef.getGalleryXML().getName()))
        && !isTiff(getExtension(galleryRef.getGalleryXML().getFileName()));
  }
}
