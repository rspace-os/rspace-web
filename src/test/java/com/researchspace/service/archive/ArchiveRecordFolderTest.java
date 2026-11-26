package com.researchspace.service.archive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.archive.ArchivalField;
import com.researchspace.archive.ArchivalGalleryMetadata;
import com.researchspace.core.util.InvalidArchiveException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ArchiveRecordFolderTest {

  @TempDir Path temp;

  private Path root;
  private Path secret;
  private ArchiveRecordFolder folder;

  @BeforeEach
  void setUp() throws Exception {
    root = Files.createDirectories(temp.resolve("archive"));
    Path doc = Files.createDirectories(root.resolve("doc_a"));
    Files.writeString(doc.resolve("attachment.txt"), "in doc folder");
    Files.writeString(doc.resolve("Q:results.csv"), "colon in a Gallery file name");
    Files.writeString(
        Files.createDirectories(root.resolve("media_1")).resolve("image.png"), "in media");
    Files.writeString(
        Files.createDirectories(root.resolve("doc_b")).resolve("doc_b.xml"), "linked doc");
    secret = Files.writeString(temp.resolve("secret.txt"), "outside the archive");
    folder = new ArchiveRecordFolder(root.toFile(), doc.toFile());
  }

  @Test
  void findsFilesInsideTheArchive() throws Exception {
    assertEquals("attachment.txt", folder.file("attachment.txt").orElseThrow().getName());
    assertEquals("image.png", folder.file("../media_1/image.png").orElseThrow().getName());
    assertEquals("doc_b.xml", folder.linkedRecordXml("doc_b").orElseThrow().getName());
    // not mistaken for a Windows drive letter
    assertEquals("Q:results.csv", folder.file("Q:results.csv").orElseThrow().getName());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"missing.txt", "../media_1"})
  void missingBlankOrNonFileNamesAreEmpty(String linkFile) throws Exception {
    assertTrue(folder.file(linkFile).isEmpty());
  }

  @Test
  void refusesNamesThatClimbOutOfTheArchive() {
    String deep = "../".repeat(40) + secret.toAbsolutePath().toString().substring(1);

    assertThrows(InvalidArchiveException.class, () -> folder.file(deep));
    assertThrows(InvalidArchiveException.class, () -> folder.file("../../secret.txt"));
    assertThrows(InvalidArchiveException.class, () -> folder.file("../../missing.txt"));
    assertThrows(InvalidArchiveException.class, () -> folder.linkedRecordXml("../x"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"/etc/passwd", "\\etc\\passwd"})
  void refusesAbsoluteNames(String linkFile) {
    assertThrows(InvalidArchiveException.class, () -> folder.file(linkFile));
    assertThrows(InvalidArchiveException.class, () -> folder.linkedRecordXml(linkFile));
  }

  @Test
  void refusesLinksThatLeadOutOfTheArchive() throws Exception {
    Files.createSymbolicLink(root.resolve("doc_a/link.txt"), secret);

    assertThrows(InvalidArchiveException.class, () -> folder.file("link.txt"));
  }

  @Test
  void checkFileNamesCoversOriginalFileLinks() {
    ArchivalGalleryMetadata chem = new ArchivalGalleryMetadata();
    chem.setLinkFile("attachment.txt");
    chem.setLinkToOriginalFile("../../secret.txt");
    ArchivalField field = new ArchivalField();
    field.addArchivalChemdooleMetadata(chem);

    assertThrows(InvalidArchiveException.class, () -> folder.checkFileNames(field));
  }

  @Test
  void recordFolderMustBeInsideTheRoot() {
    File outside = temp.toFile();

    assertThrows(
        InvalidArchiveException.class, () -> new ArchiveRecordFolder(root.toFile(), outside));
  }
}
