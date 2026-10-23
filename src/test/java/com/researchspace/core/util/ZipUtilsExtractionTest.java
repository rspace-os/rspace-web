package com.researchspace.core.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.abort;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
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

  /** A destination whose reported free space the test controls. */
  private static File withUsableSpace(File dir, LongSupplier usableSpace) {
    return new File(dir.getPath()) {
      @Override
      public long getUsableSpace() {
        return usableSpace.getAsLong();
      }
    };
  }

  private static byte[] randomBytes(int length) {
    byte[] content = new byte[length];
    new Random(42).nextBytes(content);
    return content;
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
    try {
      Files.createSymbolicLink(dest.toPath().resolve("linkdir"), outsideTarget.toPath());
    } catch (IOException | UnsupportedOperationException e) {
      // Windows needs Developer Mode or elevation to create symlinks
      abort("symlinks not supported here: " + e.getMessage());
    }
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

  @Test
  void rejectsArchiveWithTooManyEntriesAndRemovesPartialExtraction() throws IOException {
    File zip =
        makeZip(
            dir("root/"),
            file("root/a.txt", bytes(10)),
            file("root/b.txt", bytes(10)),
            file("root/c.txt", bytes(10)));
    File dest = newDestination();
    ZipExtractionLimits limits = new ZipExtractionLimits(0, 3, 0, 0, 0);

    ZipExtractionLimitException e =
        assertThrows(
            ZipExtractionLimitException.class, () -> ZipUtils.extractZip(zip, dest, limits));

    assertEquals(ZipExtractionLimitException.Limit.ENTRY_COUNT, e.getLimit());
    assertEquals(3, e.getLimitValue());
    assertEquals(0, dest.list().length, "failed extraction must leave nothing behind");
  }

  @Test
  void rejectsArchiveExpandingPastTotalSizeLimit() throws IOException {
    File zip = makeZip(file("a.txt", bytes(4096)), file("b.txt", bytes(4096)));
    File dest = newDestination();

    ZipExtractionLimitException e =
        assertThrows(
            ZipExtractionLimitException.class,
            () -> ZipUtils.extractZip(zip, dest, new ZipExtractionLimits(6000, 0, 0, 0, 0)));

    assertEquals(ZipExtractionLimitException.Limit.EXPANDED_SIZE, e.getLimit());
    assertEquals(6000, e.getLimitValue());
    assertEquals(0, dest.list().length, "failed extraction must leave nothing behind");
  }

  @Test
  void acceptsHighRatioEntryWhenWholeArchiveIsWithinRatio() throws IOException {
    // like an uncompressed image mask among ordinary attachments: the zeros inflate about 1000:1,
    // the archive as a whole about 30:1
    File zip =
        makeZip(
            file("photo.jpg", randomBytes(64 * 1024)), file("mask.tif", bytes(2 * 1024 * 1024)));
    File dest = newDestination();
    ZipExtractionLimits limits = new ZipExtractionLimits(0, 0, 0, 100, 1024);

    ZipUtils.extractZip(zip, dest, limits);

    assertEquals(2 * 1024 * 1024, new File(dest, "mask.tif").length());
  }

  @Test
  void acceptsHighRatioArchiveBelowTheFloor() throws IOException {
    File zip = makeZip(file("small.bin", bytes(64 * 1024)));
    File dest = newDestination();
    // floor above the archive's content, so the ratio is never judged
    ZipExtractionLimits limits = new ZipExtractionLimits(0, 0, 0, 100, 1024 * 1024);

    ZipUtils.extractZip(zip, dest, limits);

    assertEquals(64 * 1024, new File(dest, "small.bin").length());
  }

  @Test
  void rejectsArchiveWhoseWholeContentCompressesBeyondRatio() throws IOException {
    // no single entry passes the floor; the running total across entries does
    EntrySpec[] specs = new EntrySpec[64];
    for (int i = 0; i < specs.length; i++) {
      specs[i] = file("part" + i + ".bin", bytes(16 * 1024));
    }
    File zip = makeZip(specs);
    File dest = newDestination();
    // 1 MiB of zeros packs to roughly 9 KB, so a 50:1 bound leaves a wide margin
    ZipExtractionLimits limits = new ZipExtractionLimits(0, 0, 0, 50, 64 * 1024);

    ZipExtractionLimitException e =
        assertThrows(
            ZipExtractionLimitException.class, () -> ZipUtils.extractZip(zip, dest, limits));

    assertEquals(ZipExtractionLimitException.Limit.COMPRESSION_RATIO, e.getLimit());
    assertEquals(0, dest.list().length, "failed extraction must leave nothing behind");
  }

  @Test
  void zeroDisablesEachLimit() throws IOException {
    File zip = makeZip(file("bomb.bin", bytes(1024 * 1024)), file("b.txt", bytes(10)));
    File dest = newDestination();

    ZipUtils.extractZip(zip, dest, new ZipExtractionLimits(0, 0, 0, 0, 0));

    assertEquals(1024 * 1024, new File(dest, "bomb.bin").length());
  }

  @Test
  void rejectsExtractionWhenDestinationPartitionLacksFreeSpace() throws IOException {
    File zip = makeZip(file("a.txt", bytes(10)));
    File dest = newDestination();
    ZipExtractionLimits limits = new ZipExtractionLimits(0, 0, Long.MAX_VALUE, 0, 0);

    ZipExtractionLimitException e =
        assertThrows(
            ZipExtractionLimitException.class, () -> ZipUtils.extractZip(zip, dest, limits));

    assertEquals(ZipExtractionLimitException.Limit.FREE_DISK_SPACE, e.getLimit());
    assertEquals(0, dest.list().length, "failed extraction must leave nothing behind");
  }

  @Test
  void rejectsShortExtractionThatFinishesBelowDiskReserve() throws IOException {
    File zip = makeZip(file("a.txt", bytes(4096)));
    File dest = newDestination();
    AtomicInteger diskChecks = new AtomicInteger();
    ZipExtractionLimits limits = new ZipExtractionLimits(0, 0, 1, 0, 0);

    ZipExtractionLimitException e =
        assertThrows(
            ZipExtractionLimitException.class,
            () ->
                ZipUtils.extractZip(
                    zip,
                    withUsableSpace(
                        dest, () -> diskChecks.getAndIncrement() == 0 ? Long.MAX_VALUE : 0),
                    limits));

    assertEquals(ZipExtractionLimitException.Limit.FREE_DISK_SPACE, e.getLimit());
    assertEquals(0, dest.list().length, "failed extraction must leave nothing behind");
  }

  @Test
  void rejectsExtractionWhenPartitionFillsPartWay() throws IOException {
    File zip = makeZip(file("a.bin", bytes(20 * 1024 * 1024)));
    File dest = newDestination();
    AtomicInteger diskChecks = new AtomicInteger();
    AtomicLong writtenWhenFull = new AtomicLong();
    // plenty of space at the start, none by the first re-check after 16 MiB written
    ZipExtractionLimits limits = new ZipExtractionLimits(0, 0, 1, 0, 0);

    ZipExtractionLimitException e =
        assertThrows(
            ZipExtractionLimitException.class,
            () ->
                ZipUtils.extractZip(
                    zip,
                    withUsableSpace(
                        dest,
                        () -> {
                          if (diskChecks.getAndIncrement() == 0) {
                            return Long.MAX_VALUE;
                          }
                          writtenWhenFull.set(new File(dest, "a.bin").length());
                          return 0;
                        }),
                    limits));

    assertEquals(ZipExtractionLimitException.Limit.FREE_DISK_SPACE, e.getLimit());
    assertThat(writtenWhenFull.get()).isBetween(16L * 1024 * 1024, 20L * 1024 * 1024 - 1);
    assertEquals(0, dest.list().length, "failed extraction must leave nothing behind");
  }

  @Test
  void rejectsExtractionWhenPartitionFillsDuringEmptyEntries() throws IOException {
    EntrySpec[] specs = new EntrySpec[1100];
    for (int i = 0; i < specs.length; i++) {
      specs[i] = file("empty" + i + ".txt", bytes(0));
    }
    File zip = makeZip(specs);
    File dest = newDestination();
    AtomicInteger diskChecks = new AtomicInteger();
    AtomicInteger extractedWhenFull = new AtomicInteger();
    ZipExtractionLimits limits = new ZipExtractionLimits(0, 0, 1, 0, 0);

    ZipExtractionLimitException e =
        assertThrows(
            ZipExtractionLimitException.class,
            () ->
                ZipUtils.extractZip(
                    zip,
                    withUsableSpace(
                        dest,
                        () -> {
                          if (diskChecks.getAndIncrement() == 0) {
                            return Long.MAX_VALUE;
                          }
                          extractedWhenFull.set(dest.list().length);
                          return 0;
                        }),
                    limits));

    assertEquals(ZipExtractionLimitException.Limit.FREE_DISK_SPACE, e.getLimit());
    assertEquals(1023, extractedWhenFull.get(), "must stop at the 1024th entry");
    assertEquals(0, dest.list().length, "failed extraction must leave nothing behind");
  }

  @Test
  void rejectsEntryNestedTooDeep() throws IOException {
    File zip = makeZip(dir("a/".repeat(65)));
    File dest = newDestination();

    assertThrows(InvalidArchiveException.class, () -> ZipUtils.extractZip(zip, dest));
    assertEquals(0, dest.list().length, "failed extraction must leave nothing behind");
  }

  @Test
  void countsEachDirectoryADeepEntryCreates() throws IOException {
    File zip = makeZip(dir("a/b/c/d/e/"));
    File dest = newDestination();
    ZipExtractionLimits limits = new ZipExtractionLimits(0, 4, 0, 0, 0);

    ZipExtractionLimitException e =
        assertThrows(
            ZipExtractionLimitException.class, () -> ZipUtils.extractZip(zip, dest, limits));

    assertEquals(ZipExtractionLimitException.Limit.ENTRY_COUNT, e.getLimit());
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
