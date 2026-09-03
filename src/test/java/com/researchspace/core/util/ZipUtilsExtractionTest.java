package com.researchspace.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZipUtilsExtractionTest {

  @TempDir Path tempDir;

  private int zipCounter = 0;

  private static class EntrySpec {
    final String name;
    final byte[] content;

    EntrySpec(String name, byte[] content) {
      this.name = name;
      this.content = content;
    }
  }

  private static EntrySpec dir(String name) {
    return new EntrySpec(name, null);
  }

  private static EntrySpec file(String name, byte[] content) {
    return new EntrySpec(name, content);
  }

  private File makeZip(EntrySpec... specs) throws IOException {
    File zip = tempDir.resolve("fixture" + (zipCounter++) + ".zip").toFile();
    try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(zip)) {
      for (EntrySpec spec : specs) {
        ZipArchiveEntry entry = new ZipArchiveEntry(spec.name);
        out.putArchiveEntry(entry);
        if (spec.content != null) {
          out.write(spec.content);
        }
        out.closeArchiveEntry();
      }
    }
    return zip;
  }

  private File newDestination() {
    return tempDir.resolve("dest" + zipCounter).toFile();
  }

  private static byte[] bytes(int length) {
    return new byte[length];
  }

  private static String readAfterExtract(File zip, File dest, String relativePath)
      throws IOException {
    ZipUtils.extractZip(zip, dest);
    return Files.readString(new File(dest, relativePath).toPath());
  }

  @Test
  void extractsNormalArchiveAndReturnsTopLevelFolder() throws IOException {
    File zip =
        makeZip(
            dir("root/"),
            dir("root/sub/"),
            file("root/sub/a.txt", "hello".getBytes(StandardCharsets.UTF_8)));
    File dest = newDestination();

    String topLevel = ZipUtils.extractZip(zip, dest);

    assertEquals(new File(dest, "root").getAbsolutePath(), topLevel);
    File extracted = new File(dest, "root/sub/a.txt");
    assertEquals("hello", Files.readString(extracted.toPath()));
  }

  @Test
  void roundTripsArchiveCreatedByZipUtils() throws IOException {
    File sourceRoot = tempDir.resolve("source/root").toFile();
    assertTrue(new File(sourceRoot, "sub").mkdirs());
    Files.writeString(sourceRoot.toPath().resolve("sub/a.txt"), "round trip");
    File zip = tempDir.resolve("roundtrip.zip").toFile();
    ZipUtils.createZip(zip, sourceRoot);
    File dest = newDestination();

    String topLevel = ZipUtils.extractZip(zip, dest);

    assertNotNull(topLevel);
    assertEquals("round trip", Files.readString(new File(dest, "root/sub/a.txt").toPath()));
  }

  @Test
  void archiveWithoutDirectoryEntriesExtractsRootFilesAndReturnsNull() throws IOException {
    File zip =
        makeZip(
            file("a.txt", "aa".getBytes(StandardCharsets.UTF_8)),
            file("b.txt", "bb".getBytes(StandardCharsets.UTF_8)));
    File dest = newDestination();

    String topLevel = ZipUtils.extractZip(zip, dest);

    assertNull(topLevel, "no directory entry means no top-level folder");
    assertEquals("aa", Files.readString(new File(dest, "a.txt").toPath()));
    assertEquals("bb", Files.readString(new File(dest, "b.txt").toPath()));
  }

  @Test
  void rejectsTraversalEntryAndRemovesPartialExtraction() throws IOException {
    File zip =
        makeZip(
            dir("root/"),
            file("root/good.txt", "good".getBytes(StandardCharsets.UTF_8)),
            file("../evil.txt", "evil".getBytes(StandardCharsets.UTF_8)));
    File dest = newDestination();

    assertThrows(InvalidArchiveException.class, () -> ZipUtils.extractZip(zip, dest));

    assertFalse(new File(tempDir.toFile(), "evil.txt").exists(), "escaped file must not exist");
    assertFalse(new File(dest, "root").exists(), "partial extraction must be removed");
  }

  @Test
  void rejectsBackslashTraversalEntry() throws IOException {
    File zip = makeZip(file("..\\..\\evil.txt", "evil".getBytes(StandardCharsets.UTF_8)));
    File dest = newDestination();

    assertThrows(InvalidArchiveException.class, () -> ZipUtils.extractZip(zip, dest));
    assertFalse(new File(tempDir.toFile(), "evil.txt").exists());
  }

  @Test
  void rejectsAbsoluteEntryName() throws IOException {
    File zip = makeZip(file("/abs.txt", "evil".getBytes(StandardCharsets.UTF_8)));
    File dest = newDestination();

    assertThrows(InvalidArchiveException.class, () -> ZipUtils.extractZip(zip, dest));
  }

  @Test
  void rejectsWindowsDriveEntryName() throws IOException {
    File zip = makeZip(file("C:evil.txt", "evil".getBytes(StandardCharsets.UTF_8)));
    File dest = newDestination();

    assertThrows(InvalidArchiveException.class, () -> ZipUtils.extractZip(zip, dest));
  }

  @Test
  void acceptsLeadingDotSlashEntry() throws IOException {
    // some zip writers prepend a "./" entry; it resolves to the root and must not be rejected
    File zip =
        makeZip(dir("./"), dir("root/"), file("root/a.txt", "ok".getBytes(StandardCharsets.UTF_8)));
    File dest = newDestination();

    assertEquals("ok", readAfterExtract(zip, dest, "root/a.txt"));
  }

  @Test
  void rejectsEntryWritingThroughSymlinkedAncestorAndKeepsTargetIntact() throws IOException {
    File outsideTarget = tempDir.resolve("outside").toFile();
    assertTrue(outsideTarget.mkdirs());
    File dest = newDestination();
    assertTrue(dest.mkdirs());
    // a pre-existing symlink in the destination that points outside the extraction root
    Files.createSymbolicLink(dest.toPath().resolve("linkdir"), outsideTarget.toPath());
    File zip = makeZip(file("linkdir/escaped.txt", "evil".getBytes(StandardCharsets.UTF_8)));

    assertThrows(InvalidArchiveException.class, () -> ZipUtils.extractZip(zip, dest));
    assertFalse(new File(outsideTarget, "escaped.txt").exists(), "must not write through the link");
  }

  @Test
  void rollbackKeepsPreExistingContentItDidNotCreate() throws IOException {
    File dest = newDestination();
    assertTrue(new File(dest, "keepme").mkdirs());
    Files.writeString(dest.toPath().resolve("keepme/precious.txt"), "keep me");
    // a file entry named "keepme" collides with the existing directory, failing the write
    File zip = makeZip(file("keepme", "overwrite".getBytes(StandardCharsets.UTF_8)));

    assertThrows(IOException.class, () -> ZipUtils.extractZip(zip, dest));

    assertEquals(
        "keep me",
        Files.readString(dest.toPath().resolve("keepme/precious.txt")),
        "rollback must not delete pre-existing content");
  }

  @Test
  void ioFailureMidExtractionRemovesPartialExtraction() throws IOException {
    File zip = makeZip(file("a.txt", bytes(4096)), file("b.txt", bytes(4096)));
    byte[] raw = Files.readAllBytes(zip.toPath());
    // give the second entry's deflate stream a reserved block type, an unconditional read error
    int secondEntry = indexOfLocalHeader(raw, 2);
    int nameLen = (raw[secondEntry + 26] & 0xFF) | ((raw[secondEntry + 27] & 0xFF) << 8);
    int extraLen = (raw[secondEntry + 28] & 0xFF) | ((raw[secondEntry + 29] & 0xFF) << 8);
    raw[secondEntry + 30 + nameLen + extraLen] = 0x06;
    File corrupt = tempDir.resolve("corrupt.zip").toFile();
    Files.write(corrupt.toPath(), raw);
    File dest = newDestination();

    assertThrows(IOException.class, () -> ZipUtils.extractZip(corrupt, dest));

    assertEquals(0, dest.list().length, "failed extraction must leave nothing behind");
  }

  @Test
  void rejectsDirectoryEntryCollidingWithExistingFile() throws IOException {
    File zip = makeZip(file("root", "not a dir".getBytes(StandardCharsets.UTF_8)), dir("root/"));
    File dest = newDestination();

    assertThrows(InvalidArchiveException.class, () -> ZipUtils.extractZip(zip, dest));

    assertEquals(0, dest.list().length, "failed extraction must leave nothing behind");
  }

  private static int indexOfLocalHeader(byte[] raw, int occurrence) {
    int found = 0;
    for (int i = 0; i < raw.length - 3; i++) {
      if (raw[i] == 0x50 && raw[i + 1] == 0x4B && raw[i + 2] == 3 && raw[i + 3] == 4) {
        if (++found == occurrence) {
          return i;
        }
      }
    }
    throw new IllegalStateException("local file header " + occurrence + " not found");
  }
}
