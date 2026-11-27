package com.researchspace.service.archive;

import com.researchspace.archive.ArchivalField;
import com.researchspace.archive.ArchivalGalleryMetadata;
import com.researchspace.core.util.InvalidArchiveException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;

/**
 * A document folder inside an extracted archive. Finds the files that the document's XML names, and
 * refuses any name that resolves outside the extracted archive, since the XML comes from the
 * uploaded archive and cannot be trusted.
 */
final class ArchiveRecordFolder {

  private final Path archiveRoot;
  private final Path recordFolder;

  /**
   * @param archiveRoot the top folder of the extracted archive
   * @param recordFolder a document folder inside {@code archiveRoot}
   * @throws IOException if either folder cannot be read, or the record folder is outside the root
   */
  ArchiveRecordFolder(File archiveRoot, File recordFolder) throws IOException {
    this.archiveRoot = archiveRoot.toPath().toRealPath();
    this.recordFolder = recordFolder.toPath().toRealPath();
    if (!this.recordFolder.startsWith(this.archiveRoot)) {
      throw new InvalidArchiveException("record folder is outside the archive");
    }
  }

  /** The real path of this document folder. */
  Path path() {
    return recordFolder;
  }

  /**
   * Finds a file that the document XML names relative to this document folder, such as an
   * attachment's {@code linkFile}.
   *
   * @return the file, or empty if the name is blank or no regular file has that name
   * @throws InvalidArchiveException if the name is absolute or resolves outside the archive
   */
  Optional<File> file(String linkFile) throws InvalidArchiveException {
    if (StringUtils.isBlank(linkFile)) {
      return Optional.empty();
    }
    return resolve(recordFolder.toFile(), linkFile);
  }

  /**
   * Finds the XML file of a linked document. Its {@code linkFile} is a sibling folder name, and the
   * XML file inside it has the same name.
   *
   * @return the file, or empty if the name is blank or no regular file has that name
   * @throws InvalidArchiveException if the name is absolute or resolves outside the archive
   */
  Optional<File> linkedRecordXml(String linkFile) throws InvalidArchiveException {
    if (StringUtils.isBlank(linkFile)) {
      return Optional.empty();
    }
    File archiveFolder = recordFolder.getParent().toFile();
    return resolve(archiveFolder, linkFile + File.separator + linkFile + ".xml");
  }

  /**
   * Checks every file name in a field's metadata that the importer reads.
   *
   * @throws InvalidArchiveException if any name is absolute or resolves outside the archive
   */
  void checkFileNames(ArchivalField field) throws InvalidArchiveException {
    List<List<ArchivalGalleryMetadata>> fileLists =
        Stream.of(
                field.getImgMeta(),
                field.getAudioMeta(),
                field.getVideoMeta(),
                field.getAttachMeta(),
                field.getChemFileMeta(),
                field.getChemElementMeta(),
                field.getMathMeta(),
                field.getAnnotMeta(),
                field.getSktchMeta())
            .filter(Objects::nonNull)
            .toList();
    for (List<ArchivalGalleryMetadata> fileList : fileLists) {
      for (ArchivalGalleryMetadata agm : fileList) {
        file(agm.getLinkFile());
        file(agm.getLinkToOriginalFile());
      }
    }
    if (field.getLinkMeta() != null) {
      for (ArchivalGalleryMetadata agm : field.getLinkMeta()) {
        linkedRecordXml(agm.getLinkFile());
      }
    }
  }

  private Optional<File> resolve(File base, String name) throws InvalidArchiveException {
    try {
      // RSpace exports only write relative names, so an absolute one means the XML was edited
      if (name.startsWith("/") || name.startsWith("\\") || Path.of(name).isAbsolute()) {
        throw new InvalidArchiveException("absolute file name");
      }
      Path candidate = new File(base, name).toPath().normalize();
      if (!candidate.startsWith(archiveRoot)) {
        throw new InvalidArchiveException("file name resolves outside the archive");
      }
      if (!Files.exists(candidate)) {
        return Optional.empty();
      }
      // extraction only writes plain files, but check where links lead in case that changes
      Path real = candidate.toRealPath();
      if (!real.startsWith(archiveRoot)) {
        throw new InvalidArchiveException("file name resolves outside the archive");
      }
      return Files.isRegularFile(real) ? Optional.of(real.toFile()) : Optional.empty();
    } catch (InvalidArchiveException e) {
      throw e;
    } catch (InvalidPathException | IOException e) {
      throw new InvalidArchiveException("unreadable file name: " + e.getMessage());
    }
  }
}
