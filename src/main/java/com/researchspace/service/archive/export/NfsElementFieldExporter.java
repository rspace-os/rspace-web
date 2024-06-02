package com.researchspace.service.archive.export;

import com.researchspace.archive.ArchivalNfsFile;
import com.researchspace.linkedelements.FieldElementLinkPair;
import com.researchspace.model.netfiles.NfsElement;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.netfiles.NfsResourceDetails;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;

class NfsElementFieldExporter extends AbstractFieldExporter<NfsElement> {

  NfsElementFieldExporter() {}

  NfsElementFieldExporter(FieldExporterSupport support) {
    super(support);
  }

  @Override
  void createFieldArchiveObject(
      NfsElement item, String replacementLink, FieldExportContext context) {
    NfsFileStore fileStore = support.getNfsManager().getNfsFileStore(item.getFileStoreId());
    ArchivalNfsFile archiveNfs = archiveModelFactory.createArchivalNfs(fileStore, item);
    if (isExportWithNfsFilesIncluded(context)) {
      if (!item.toString().equals(replacementLink)) {
        archiveNfs.setAddedToArchive(true);
        archiveNfs.setArchivePath(replacementLink);
        if (item.isFolderLink()) {
          archiveNfs.setFolderLink(true);
          archiveNfs.setFolderExportSummaryMsg(
              context.getNfsContext().getDownloadSummaryMsgForNfsFolder(archiveNfs));
        }
      } else {
        archiveNfs.setErrorMsg(context.getNfsContext().getDownloadErrorMsgForNfsFile(archiveNfs));
      }
    }
    context.getArchiveField().addArchivalNfs(archiveNfs);
  }

  @Override
  String doUpdateLinkText(
      FieldElementLinkPair<NfsElement> nfsPair,
      String replacementLink,
      FieldExportContext context) {
    NfsElement element = nfsPair.getElement();
    NfsFileStore fileStore = support.getNfsManager().getNfsFileStore(element.getFileStoreId());
    String fullPath = fileStore.getFullPath(element).toFullPath();
    String newHref = null;
    if (isExportWithNfsFilesIncluded(context) && !element.toString().equals(replacementLink)) {
      newHref = "../" + NfsExportContext.FILESTORE_FILES_ARCHIVE_DIR + "/" + replacementLink;
    }
    return support
        .getRichTextUpdater()
        .updateNfsLinkOnExport(
            context.getArchiveField().getFieldData(), element, fullPath, newHref);
  }

  @Override
  String getReplacementUrl(FieldExportContext context, NfsElement item)
      throws URISyntaxException, IOException {

    boolean exportWithNfs = isExportWithNfsFilesIncluded(context);
    if (exportWithNfs) {
      NfsResourceDetails nfsResourceDetails =
          context.getNfsContext().getDownloadedNfsResourceDetails(item, support);
      if (nfsResourceDetails != null) {
        File nfsResourceFile = nfsResourceDetails.getLocalFile();
        File archiveDirForNfsPath = getArchiveDirForNfsPath(context, nfsResourceDetails);
        File archiveCopyFile = FileUtils.getFile(archiveDirForNfsPath, nfsResourceFile.getName());
        if (!archiveCopyFile.exists()) {
          new RelativeLinkProcessor(context, support)
              .copyResourceToArchiveFolder(nfsResourceFile, archiveDirForNfsPath);
        }
        return archiveDirForNfsPath.getName() + "/" + nfsResourceFile.getName();
      }
    }
    return item.toString(); // return unchanged, will keep pointing to external filesystem
  }

  private File getArchiveDirForNfsPath(FieldExportContext context, NfsResourceDetails nfsFile) {
    String nfsPathSubfolderName = getArchiveSubfolderNameForNfsResource(nfsFile);
    File archiveNfsDir = context.getNfsContext().getArchiveNfsDir();
    return FileUtils.getFile(archiveNfsDir, nfsPathSubfolderName);
  }

  private String getArchiveSubfolderNameForNfsResource(NfsResourceDetails nfsResource) {
    String fullPathToLastFolder = null;
    if (nfsResource.isFile()) {
      fullPathToLastFolder = nfsResource.getFileSystemParentPath();
    } else {
      fullPathToLastFolder = nfsResource.getFileSystemFullPath();
    }
    String nfsPathForHash = nfsResource.getFileSystemId() + "_" + fullPathToLastFolder;
    // using first part of sha hash as a directory name
    return Hex.encodeHexString(DigestUtils.sha1(nfsPathForHash)).substring(0, 16);
  }

  // we override to ignore revision history for RSPAC-1387, as Nfs stuff isn't audited
  @Override
  NfsElement getRevisionIfAvailable(Number revisionNo, NfsElement object) {
    return object;
  }

  private boolean isExportWithNfsFilesIncluded(FieldExportContext context) {
    boolean nfsContextAvailable = context.getConfig() != null && context.getNfsContext() != null;
    return nfsContextAvailable && context.getConfig().isIncludeNfsLinks();
  }
}
