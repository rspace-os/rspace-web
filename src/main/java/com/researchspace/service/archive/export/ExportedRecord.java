package com.researchspace.service.archive.export;

import com.researchspace.archive.ArchivalDocument;
import com.researchspace.archive.ArchivalGalleryMetadata;
import com.researchspace.archive.ArchiveFolder;
import com.researchspace.archive.model.ArchiveModelFactory;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.RecordToFolder;
import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Stores info collected during export process that is needed when writing the data. <br>
 * Either of archivedRecord or archivedMEdia file can be not null, but not both
 */
public class ExportedRecord {

  private Record exportedRecord;

  private ArchivalDocument archivedRecord;

  private File outFile;
  private File recordFolder;
  private ArchivalGalleryMetadata archivedMedia;

  /**
   * The original record being exported
   *
   * @return
   */
  Record getExportedRecord() {
    return exportedRecord;
  }

  /**
   * The export object; can be null if this object encapsulates an exported media file
   *
   * @return
   */
  ArchivalDocument getArchivedRecord() {
    return archivedRecord;
  }

  ExportedRecord(Record exportedRecord, ArchivalDocument archivedRecord) {
    this.exportedRecord = exportedRecord;
    this.archivedRecord = archivedRecord;
  }

  ExportedRecord(Record exportedRecord, ArchivalGalleryMetadata mediaFile) {
    this.exportedRecord = exportedRecord;
    this.archivedMedia = mediaFile;
  }

  ArchivalGalleryMetadata getArchivedMedia() {
    return archivedMedia;
  }

  void setArchivedMedia(ArchivalGalleryMetadata archivedMedia) {
    this.archivedMedia = archivedMedia;
  }

  /**
   * The file that this record's data will be written to.
   *
   * @param outputDocFile
   */
  void setOutFile(File outputDocFile) {
    this.outFile = outputDocFile;
  }

  /**
   * The folder that the exported file will be written to
   *
   * @param recordFolder
   */
  void setRecordFolder(File recordFolder) {
    this.recordFolder = recordFolder;
  }

  File getOutFile() {
    return outFile;
  }

  /**
   * The folder used to aggregate resources for a particular document or media item that is included
   * in the export.
   *
   * @return
   */
  File getRecordFolder() {
    return recordFolder;
  }

  /**
   * Sets parent for archive record. It's usually the owner's parent, but if the record has a few
   * parents, and owner's parent is not included in export, then it checks if maybe other of the
   * parents is included.
   *
   * <p>I.e. when exporting shared record the method will set parent to shared parent of the
   * exporter, rather than to record owner's parent.
   */
  void calculateParentFolder(List<ArchiveFolder> folderTree) {
    if (archivedRecord == null) {
      return; // media gallery records use other way to find parent
    }

    Optional<Folder> fldOpt = exportedRecord.getOwnerParent();
    Folder fld = null;
    // if owner's parent is not included in this archive, maybe we export some other parent
    if (folderTree != null && !isFolderOnList(fldOpt.get(), folderTree)) {
      Set<RecordToFolder> parents = exportedRecord.getParents();
      if (parents != null && !parents.isEmpty()) {
        for (RecordToFolder recordToFolder : parents) {
          Folder parent = recordToFolder.getFolder();
          if (isFolderOnList(parent, folderTree)) {
            fld = parent;
            break;
          }
        }
      }
    }
    new ArchiveModelFactory().addParentFolderDetails(Optional.ofNullable(fld), archivedRecord);
  }

  private boolean isFolderOnList(Folder fld, List<ArchiveFolder> folderTree) {
    if (fld != null && folderTree != null) {
      for (ArchiveFolder archiveFolder : folderTree) {
        if (fld.getId().equals(archiveFolder.getId())) {
          return true;
        }
      }
    }
    return false;
  }

  long getParentFolderId() {
    return archivedRecord != null
        ? archivedRecord.getFolderId()
        : archivedMedia.getParentGalleryFolderId();
  }
}
