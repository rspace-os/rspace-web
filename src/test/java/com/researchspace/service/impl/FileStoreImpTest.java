package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.core.util.TransformerUtils;
import com.researchspace.dao.FileMetadataDao;
import com.researchspace.files.service.ExternalFileService;
import com.researchspace.files.service.ExternalFileStoreLocator;
import com.researchspace.files.service.FileStoreImpl;
import com.researchspace.model.FileProperty;
import com.researchspace.model.FileStoreRoot;
import com.researchspace.model.User;
import com.researchspace.service.FileDuplicateStrategy;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.TestFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.apache.commons.io.FilenameUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

public class FileStoreImpTest {

  class FileStoreImpTss extends InternalFileStoreImpl {
    Collection<File> rc;

    Collection<File> getUTF8misMatchFiles(String absPath, String fname) {
      return rc;
    }
  }

  InternalFileStoreImpl fs;
  String utf8FileName = "widok_z_łazika.png";
  String utf8FileName2 = "w?dok_z_łazika.png";
  User user;

  @BeforeEach
  public void setUp() throws Exception {
    fs = new InternalFileStoreImpl();
    user = TestFactory.createAnyUser("any");
  }

  @Test
  public void testHandlePossibleUTF8Error1() throws IOException {
    List<String> files = Arrays.asList(new String[] {utf8FileName, utf8FileName2});
    for (String file : files) {
      File utf8File = RSpaceTestUtils.getResource(file);
      FileProperty fp = setupFileStoreRoot(utf8File);
      String corruptedName = utf8File.getName().replace("ł", "?");
      // mimic messed up stream
      fp.setRelPath(corruptedName);

      FileInputStream stream = fs.handlePossibleUTF8Error(fp, fnfe());
      assertNotNull(stream);
    }
  }

  @Test
  public void testHandlePossibleUTF8ErrorCardinality() throws IOException {
    FileStoreImpTss tss = new FileStoreImpTss();
    // simulate inability to find matching file
    tss.rc = Collections.emptyList();
    File utf8File = RSpaceTestUtils.getResource(utf8FileName);
    FileProperty fp = setupFileStoreRoot(utf8File);
    String corruptedName = utf8File.getName().replace("ł", "?");
    // mimic messed up stream
    fp.setRelPath(corruptedName);
    assertThrows(FileNotFoundException.class, () -> tss.handlePossibleUTF8Error(fp, fnfe()));
  }

  @Test
  public void handlePossibleUTF8ErrorThrowsISEIfMultuplieMatches() throws IOException {
    FileStoreImpTss tss = new FileStoreImpTss();
    // simulate > 1 hit
    tss.rc = TransformerUtils.toList(new File("any1"), new File("any2"));
    File utf8File = RSpaceTestUtils.getResource(utf8FileName);
    FileProperty fp = setupFileStoreRoot(utf8File);
    String corruptedName = utf8File.getName().replace("ł", "?");
    // mimic messed up stream
    fp.setRelPath(corruptedName);
    assertThrows(IllegalStateException.class, () -> tss.handlePossibleUTF8Error(fp, fnfe()));
  }

  @TempDir Path tempDir;

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void duplicateStrategiesPreserveOrReplaceContents(boolean stream) throws IOException {
    FileMetadataDao metadata = setUpStore(stream);
    FileProperty original = new FileProperty();
    original.setFileCategory("test");
    original.setFileGroup("group");
    original.setFileUser("user");
    original.setFileVersion("v1");
    URI first = saveContents(original, "original", FileDuplicateStrategy.AS_NEW, stream);

    assertNull(saveContents(original.copy(), "rejected", FileDuplicateStrategy.ERROR, stream));
    assertEquals("original", Files.readString(Path.of(first)));
    ExternalFileStoreLocator locator = mock(ExternalFileStoreLocator.class);
    ExternalFileService external = mock(ExternalFileService.class);
    FileStoreImpl composite = new FileStoreImpl(fs, locator, external);
    if (stream) {
      try (var input = new ByteArrayInputStream("rejected".getBytes(StandardCharsets.UTF_8))) {
        assertNull(composite.save(original.copy(), input, "test.txt", FileDuplicateStrategy.ERROR));
      }
    } else {
      assertNull(
          composite.save(
              original.copy(), tempDir.resolve("test.txt").toFile(), FileDuplicateStrategy.ERROR));
    }
    verifyNoInteractions(locator, external);

    assertEquals(
        first, saveContents(original, "replacement", FileDuplicateStrategy.REPLACE, stream));
    assertEquals("replacement", Files.readString(Path.of(first)));

    FileProperty duplicate = original.copy();
    URI second = saveContents(duplicate, "new", FileDuplicateStrategy.AS_NEW, stream);
    assertNotEquals(first, second);
    assertEquals("new", Files.readString(Path.of(second)));
    assertEquals("replacement", Files.readString(Path.of(first)));
    assertEquals(Path.of(second).toFile(), fs.findFile(duplicate));

    URI third = saveContents(original.copy(), "third", FileDuplicateStrategy.AS_NEW, stream);
    assertNotEquals(first, third);
    assertNotEquals(second, third);
    assertEquals("new", Files.readString(Path.of(second)));
    assertEquals("third", Files.readString(Path.of(third)));

    FileProperty failedSave = original.copy();
    failedSave.setFileVersion("v2");
    IllegalStateException failure = new IllegalStateException("metadata unavailable");
    when(metadata.save(failedSave)).thenThrow(failure);
    assertSame(
        failure,
        assertThrows(
            IllegalStateException.class,
            () -> saveContents(failedSave, "failed", FileDuplicateStrategy.AS_NEW, stream)));
    assertFalse(fs.findFile(failedSave).exists());
    assertEquals("replacement", Files.readString(Path.of(first)));
  }

  @ParameterizedTest
  @MethodSource("collisionNames")
  void collidingLongNamesUseBoundedUuidNames(String name) throws IOException {
    setUpStore(true);
    FileProperty original = new FileProperty();
    original.setFileName(name);
    URI first = saveContents(original, "original", FileDuplicateStrategy.AS_NEW, true);
    FileProperty duplicate = original.copy();
    URI second = saveContents(duplicate, "duplicate", FileDuplicateStrategy.AS_NEW, true);
    String storedName = Path.of(second).getFileName().toString();
    String extension = FilenameUtils.getExtension(name);
    if (extension.getBytes(StandardCharsets.UTF_8).length > 20) {
      extension = "";
    }
    assertEquals(extension, FilenameUtils.getExtension(storedName));
    assertNotNull(UUID.fromString(FilenameUtils.getBaseName(storedName)));
    assertTrue(storedName.getBytes(StandardCharsets.UTF_8).length <= 57);
    assertEquals(storedName, duplicate.getFileName());
    assertEquals(Path.of(second).toFile(), fs.findFile(duplicate));
    assertEquals("original", Files.readString(Path.of(first)));
    assertEquals("duplicate", Files.readString(Path.of(second)));
  }

  static Stream<String> collisionNames() {
    return Stream.of(
        "a".repeat(251) + ".txt",
        "é".repeat(125) + ".csv",
        "test." + "x".repeat(21),
        "test.报告",
        "no-extension");
  }

  private FileMetadataDao setUpStore(boolean stream) throws IOException {
    Path store = Files.createDirectory(tempDir.resolve("store"));
    fs.setBaseDir(store.toFile());
    FileMetadataDao metadata = mock(FileMetadataDao.class);
    File base = fs.fileOp.getFoldOp().getBaseDir();
    FileStoreRoot root = new FileStoreRoot(base.toURI().toString());
    root.setCurrent(true);
    when(metadata.findByFileStorePath(base.getAbsolutePath())).thenReturn(root);
    if (stream) {
      when(metadata.getCurrentFileStoreRoot(false)).thenReturn(root);
    }
    fs.setFileMetadataDao(metadata);
    return metadata;
  }

  private URI saveContents(
      FileProperty property, String contents, FileDuplicateStrategy strategy, boolean stream)
      throws IOException {
    if (stream) {
      try (var input = new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8))) {
        return fs.save(property, input, "test.txt", strategy);
      }
    }
    Path source = tempDir.resolve("test.txt");
    Files.writeString(source, contents);
    return fs.save(property, source.toFile(), strategy);
  }

  private FileNotFoundException fnfe() {
    return new FileNotFoundException("from corrupted path");
  }

  private FileProperty setupFileStoreRoot(File utf8File) {
    FileProperty fp = new FileProperty();
    FileStoreRoot root = new FileStoreRoot(FilenameUtils.getFullPath(utf8File.getAbsolutePath()));
    fp.setRoot(root);
    return fp;
  }
}
