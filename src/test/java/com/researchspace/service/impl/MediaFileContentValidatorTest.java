package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.testutils.RSpaceTestUtils;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MediaFileContentValidatorTest {

  private static final byte[] JSP_CONTENT =
      "<% out.println(\"jsp-probe-\" + System.getProperty(\"os.name\")); %>"
          .getBytes(StandardCharsets.UTF_8);

  @ParameterizedTest
  @ValueSource(strings = {"image.jpg", "image.jpeg", "image.png", "image.gif", "IMAGE.JPG"})
  void rejectsNonImageContentWithImageExtension(String fileName) {
    assertThrows(
        ApiRuntimeException.class,
        () ->
            MediaFileContentValidator.verifyContentMatchesExtension(
                new ByteArrayInputStream(JSP_CONTENT), fileName));
  }

  @Test
  void rejectsUnrecognisableContentWithImageExtension() {
    byte[] noMagicBytes = new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08};
    assertThrows(
        ApiRuntimeException.class,
        () ->
            MediaFileContentValidator.verifyContentMatchesExtension(
                new ByteArrayInputStream(noMagicBytes), "image.jpg"));
  }

  @ParameterizedTest
  @CsvSource({
    "Picture1.png, image.jpg",
    "IS1.jpg, image.png",
    "commentIcon.gif, image.png",
    "Picture1.tiff, image.jpg"
  })
  void rejectsImageContentUnderADifferentImageExtension(String fixture, String claimedName)
      throws IOException {
    byte[] realImage = RSpaceTestUtils.getResourceAsByteArray(fixture);
    assertThrows(
        ApiRuntimeException.class,
        () ->
            MediaFileContentValidator.verifyContentMatchesExtension(
                new ByteArrayInputStream(realImage), claimedName));
  }

  /** jpg/jpeg and tif/tiff name the same format, so those spellings must be interchangeable. */
  @ParameterizedTest
  @CsvSource({
    "IS1.jpg, photo.jpeg",
    "IS1.jpg, photo.JPG",
    "Picture1.tiff, scan.tif",
    "Picture2.tif, scan.tiff"
  })
  void acceptsEquivalentExtensionSpellings(String fixture, String claimedName) throws IOException {
    byte[] realImage = RSpaceTestUtils.getResourceAsByteArray(fixture);
    InputStream validated =
        MediaFileContentValidator.verifyContentMatchesExtension(
            new ByteArrayInputStream(realImage), claimedName);
    assertArrayEquals(realImage, IOUtils.toByteArray(validated));
  }

  @ParameterizedTest
  @ValueSource(strings = {"IS1.jpg", "Picture1.png", "Picture1.tiff", "commentIcon.gif"})
  void acceptsRealImagesAndPreservesStreamContent(String fileName) throws IOException {
    byte[] expected = RSpaceTestUtils.getResourceAsByteArray(fileName);
    // a non-markable stream, so validation must wrap and rewind it
    InputStream validated =
        MediaFileContentValidator.verifyContentMatchesExtension(
            RSpaceTestUtils.getInputStreamOnFromTestResourcesFolder(fileName), fileName);
    assertArrayEquals(expected, IOUtils.toByteArray(validated));
  }

  /** bmp is an accepted image extension with no fixture in the test resources. */
  @Test
  void acceptsBmp() throws IOException {
    ByteArrayOutputStream bmp = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "bmp", bmp);
    InputStream validated =
        MediaFileContentValidator.verifyContentMatchesExtension(
            new ByteArrayInputStream(bmp.toByteArray()), "shape.bmp");
    assertArrayEquals(bmp.toByteArray(), IOUtils.toByteArray(validated));
  }

  @ParameterizedTest
  @ValueSource(strings = {"notes.txt", "page.jsp", "noExtension", ""})
  void ignoresNonImageExtensions(String fileName) throws IOException {
    InputStream original = new ByteArrayInputStream(JSP_CONTENT);
    InputStream validated =
        MediaFileContentValidator.verifyContentMatchesExtension(original, fileName);
    assertSame(original, validated);
  }

  @Test
  void ignoresNullFileName() throws IOException {
    InputStream original = new ByteArrayInputStream(JSP_CONTENT);
    InputStream validated = MediaFileContentValidator.verifyContentMatchesExtension(original, null);
    assertSame(original, validated);
  }
}
