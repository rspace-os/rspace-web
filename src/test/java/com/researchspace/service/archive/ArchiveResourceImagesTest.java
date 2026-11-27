package com.researchspace.service.archive;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ArchiveResourceImagesTest {

  @TempDir Path tempDir;

  private Path root;
  private Path recordFolder;
  private Path resources;
  private ArchiveResourceImages resourceImages;

  @BeforeEach
  void setUp() throws IOException {
    root = Files.createDirectories(tempDir.resolve("extracted/RSpaceArchive"));
    recordFolder = Files.createDirectories(root.resolve("doc_Untitled-1"));
    resources = Files.createDirectories(root.resolve("resources"));
    Files.write(resources.resolve("smiley.gif"), gif());
    resourceImages = new ArchiveResourceImages(root);
  }

  @Test
  void resolvesBareFilenameInSiblingResourcesFolder() throws IOException {
    assertThat(resourceImages.resolve(recordFolder, "../resources/smiley.gif"))
        .contains(resources.resolve("smiley.gif").toRealPath());
  }

  @Test
  void resolvesWhenExtractionRootIsReachedThroughSymlinkedTempDirectory() throws IOException {
    Path linkedTemp =
        Files.createSymbolicLink(tempDir.resolve("linked"), tempDir.resolve("extracted"));
    Path linkedRoot = linkedTemp.resolve("RSpaceArchive");

    assertThat(
            new ArchiveResourceImages(linkedRoot)
                .resolve(linkedRoot.resolve("doc_Untitled-1"), "../resources/smiley.gif"))
        .contains(resources.resolve("smiley.gif").toRealPath());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "../resources/missing.gif",
        "../resources/nested/smiley.gif",
        "../resources/../resources/smiley.gif",
        "../resources/..",
        "../resources/.",
        "..\\resources\\smiley.gif",
        "../resources/nested\\smiley.gif",
        "../resources/smiley.gif?v=1",
        "../resources/smiley.gif#top",
        "../resources/%2e%2e%2fsmiley.gif",
        "resources/smiley.gif",
        "/images/smiley.gif",
        "../resources/"
      })
  void rejectsAnythingButAnExistingBareFilename(String src) throws IOException {
    Files.createDirectories(resources.resolve("nested"));
    Files.write(resources.resolve("nested/smiley.gif"), gif());

    assertThat(resourceImages.resolve(recordFolder, src)).isEmpty();
  }

  @Test
  void rejectsDirectoryWithImageName() throws IOException {
    Files.createDirectories(resources.resolve("folder.gif"));

    assertThat(resourceImages.resolve(recordFolder, "../resources/folder.gif")).isEmpty();
  }

  @Test
  void rejectsSymlinkToFileOutsideArchive() throws IOException {
    Path outside = Files.write(tempDir.resolve("outside.gif"), gif());
    Files.createSymbolicLink(resources.resolve("escape.gif"), outside);

    assertThat(resourceImages.resolve(recordFolder, "../resources/escape.gif")).isEmpty();
  }

  @Test
  void rejectsSymlinkToFileElsewhereInArchive() throws IOException {
    Path inArchive = Files.write(recordFolder.resolve("inside.gif"), gif());
    Files.createSymbolicLink(resources.resolve("inside.gif"), inArchive);

    assertThat(resourceImages.resolve(recordFolder, "../resources/inside.gif")).isEmpty();
  }

  @Test
  void rejectsResourcesFolderThatIsSymlinkToAnotherArchiveFolder() throws IOException {
    Path otherRoot = Files.createDirectories(tempDir.resolve("other/RSpaceArchive"));
    Path otherRecord = Files.createDirectories(otherRoot.resolve("doc_Untitled-1"));
    Path images = Files.createDirectories(otherRoot.resolve("images"));
    Files.write(images.resolve("smiley.gif"), gif());
    Files.createSymbolicLink(otherRoot.resolve("resources"), images);

    assertThat(new ArchiveResourceImages(otherRoot).resolve(otherRecord, "../resources/smiley.gif"))
        .isEmpty();
  }

  @Test
  void rejectsRecordFolderOutsideExtractionRoot() throws IOException {
    Path otherRecord = Files.createDirectories(tempDir.resolve("elsewhere/doc_Untitled-1"));
    Path otherResources = Files.createDirectories(tempDir.resolve("elsewhere/resources"));
    Files.write(otherResources.resolve("smiley.gif"), gif());

    assertThat(resourceImages.resolve(otherRecord, "../resources/smiley.gif")).isEmpty();
  }

  @Test
  void acceptsOnlyCompletelyDecodableImagesWithImageExtension() throws IOException {
    byte[] header = "GIF89a".getBytes(US_ASCII);
    byte[] corruptBody = new byte[64];
    System.arraycopy(header, 0, corruptBody, 0, header.length);
    // 16 x 16 logical screen without a colour table, followed by an unknown block
    byte[] screen = {16, 0, 16, 0, 0, 0, 0};
    System.arraycopy(screen, 0, corruptBody, header.length, screen.length);
    for (int i = header.length + screen.length; i < corruptBody.length; i++) {
      corruptBody[i] = (byte) 0x99;
    }

    assertTrue(ArchiveResourceImages.isDecodableImage(resources.resolve("smiley.gif")));
    assertFalse(
        ArchiveResourceImages.isDecodableImage(
            Files.write(resources.resolve("broken.gif"), "not an image".getBytes(US_ASCII))));
    assertFalse(
        ArchiveResourceImages.isDecodableImage(
            Files.write(resources.resolve("corrupt.gif"), corruptBody)));
    assertFalse(
        ArchiveResourceImages.isDecodableImage(Files.write(resources.resolve("notes.txt"), gif())));
  }

  private static byte[] gif() throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "gif", out);
    return out.toByteArray();
  }
}
