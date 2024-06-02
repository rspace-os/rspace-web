package com.researchspace.service.archive.export;

import com.researchspace.archive.ArchiveFileNameData;
import com.researchspace.core.util.imageutils.ImageUtils;
import com.researchspace.model.EcatMediaFile;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

/**
 * Helper class to perform media file retrieval and copying operations, returning relative links to
 * these media files.
 */
class RelativeLinkProcessor {

  private FieldExportContext exportContext;
  private FieldExporterSupport support;

  RelativeLinkProcessor(FieldExportContext exportContext, FieldExporterSupport support) {
    this.exportContext = exportContext;
    this.support = support;
  }

  // if we're handling a selection of records, we just copy the associate files into the document
  // folder in the archive. Else, we link to existing media item, which will be exported
  // independently
  // as part of user or group export.
  RelativeLinks getLinkReplacement(EcatMediaFile mediaFile) throws URISyntaxException, IOException {
    RelativeLinks relLinks = null;
    if (mediaFileShouldBeIncludedInDocumentFolder(
        mediaFile)) { // rspac1333, we have to get media file from FS
      relLinks = copyFromFileStoreToArchive(exportContext.getRecordFolder(), mediaFile);
    } else {
      // media file included in export by itself.
      relLinks = getLinkToMediaFile(mediaFile);
    }
    return relLinks;
  }

  private boolean mediaFileShouldBeIncludedInDocumentFolder(EcatMediaFile mediaFile) {
    return (isSelectionScope()
            && !isMediaFileSelectedForExport(mediaFile)) // col4 in RSPAC 998 Decision table
        // col1,2 in in RSPAC 998 Decision table
        // if media file is deleted, it must still be included if it is linked to by  documents.
        || mediaFile.isDeleted();
  }

  // if true, then we must be exporting from the Gallery or from search result listing
  // if false, we are exporting file linked to  a document.
  private boolean isMediaFileSelectedForExport(EcatMediaFile mediaFile) {
    return exportContext.getExportRecordList().containsRecord(mediaFile.getOid());
  }

  private boolean isSelectionScope() {
    return exportContext.getConfig().isSelectionScope();
  }

  // gets link to media file, assuming it is in an export folder by itself
  private RelativeLinks getLinkToMediaFile(EcatMediaFile mediaFile) {
    String originalFileToLinkTo =
        new ArchiveFileNameData(mediaFile, exportContext.getRevision()).toFileName();
    originalFileToLinkTo = getLinkToMediaFileInExport(mediaFile, originalFileToLinkTo);
    if (ImageUtils.isTiff(mediaFile.getExtension())) {
      return createRelativeLinksForTiff(originalFileToLinkTo);
    } else {
      return new RelativeLinks(originalFileToLinkTo, null);
    }
  }

  private RelativeLinks createRelativeLinksForTiff(String originalFileToLinkTo) {
    String pngCopyToLinkTo = FilenameUtils.removeExtension(originalFileToLinkTo);
    pngCopyToLinkTo = pngCopyToLinkTo + ".png";
    return new RelativeLinks(pngCopyToLinkTo, originalFileToLinkTo);
  }

  // for a tiff file gets the location of the original file, else empty -string there is no original
  // file
  String getOriginalFile(EcatMediaFile mediaFile) {
    if (ImageUtils.isTiff(mediaFile.getExtension())) {
      if (mediaFileShouldBeIncludedInDocumentFolder(mediaFile)) {
        return FilenameUtils.getName(mediaFile.getFileUri().toString());
      } else {
        return getLinkToMediaFile(mediaFile).getLinkToOriginalFile();
      }
    } else {
      return "";
    }
  }

  private String getLinkToMediaFileInExport(EcatMediaFile mediaFile, String originalFileToLinkTo) {
    return "../" + originalFileToLinkTo + File.separator + mediaFile.getName();
  }

  // in this case we're copying from FS to record's export location,
  // as we're not exporting the whole gallery
  private RelativeLinks copyFromFileStoreToArchive(File recordFolder, EcatMediaFile mediaFile)
      throws URISyntaxException, IOException {

    File fileToExport = support.getFileStore().findFile(mediaFile.getFileProperty());
    // if it's a tiff,
    if (ImageUtils.isTiff(mediaFile.getExtension())) {
      // we copy the tif so it's in the archive - and convert to png
      // so that link to png will display in browser.
      String origPath = copyResourceToArchiveFolder(fileToExport, recordFolder);
      File newPng =
          ImageUtils.convertTiffToPng(
              fileToExport, recordFolder, FilenameUtils.getBaseName(mediaFile.getName()));
      RelativeLinks links = new RelativeLinks(newPng.getName(), origPath);
      return links;
    } else {
      // we just copy as is, no conversion needed.
      String origPath = copyResourceToArchiveFolder(fileToExport, recordFolder);
      return new RelativeLinks(origPath, null);
    }
  }

  /**
   * Copies original resource (file or folder) to the export folder location. Ensures that available
   * disk space requirements are met (throws DiskSpaceLimitException otherwise).
   */
  String copyResourceToArchiveFolder(File resourceToExport, File archiveFolder) throws IOException {

    support
        .getDiskSpaceChecker()
        .assertEnoughDiskSpaceToCopyFileIntoArchiveDir(
            resourceToExport, exportContext.getExportFolder());

    if (resourceToExport.isFile()) {
      // no longer reads file into memory. should handle larger files RSPAC-1685
      FileUtils.copyFileToDirectory(resourceToExport, archiveFolder);
    } else {
      File archiveSubfolder = FileUtils.getFile(archiveFolder, resourceToExport.getName());
      FileUtils.copyDirectory(resourceToExport, archiveSubfolder);
    }
    return resourceToExport.getName();
  }
}
