package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.testutils.RSpaceTestUtils;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
  @ValueSource(strings = {"IS1.jpg", "Picture1.png", "Picture1.tiff", "commentIcon.gif"})
  void acceptsRealImagesAndPreservesStreamContent(String fileName) throws IOException {
    byte[] expected = RSpaceTestUtils.getResourceAsByteArray(fileName);
    // a non-markable stream, so validation must wrap and rewind it
    InputStream validated =
        MediaFileContentValidator.verifyContentMatchesExtension(
            RSpaceTestUtils.getInputStreamOnFromTestResourcesFolder(fileName), fileName);
    assertArrayEquals(expected, IOUtils.toByteArray(validated));
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
