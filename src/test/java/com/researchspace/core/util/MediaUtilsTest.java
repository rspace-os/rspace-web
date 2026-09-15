package com.researchspace.core.util;

import static com.researchspace.core.util.MediaUtils.DOCUMENT_MEDIA_FLDER_NAME;
import static com.researchspace.core.util.MediaUtils.IMAGES_MEDIA_FLDER_NAME;
import static com.researchspace.core.util.MediaUtils.extractFileTypeFromPath;
import static com.researchspace.core.util.MediaUtils.getSupportedFileTypesForGallerySection;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.ArrayUtils;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class MediaUtilsTest {

  @ParameterizedTest
  @ValueSource(strings = {MediaUtils.DMP_MEDIA_FLDER_NAME, "not a gallery section", ""})
  void getExtensionsForGalleryFolderArgumentValidation(String unknownGF) {
    assertThrows(
        IllegalArgumentException.class, () -> getSupportedFileTypesForGallerySection(unknownGF));
  }

  @ParameterizedTest
  @MethodSource("getInsertableGalleryMediaFolders")
  void getExtensionsForGalleryFiles(String galleryFolder) {
    assertTrue(getSupportedFileTypesForGallerySection(galleryFolder).length > 0);
  }

  static String[] getInsertableGalleryMediaFolders() {
    return MediaUtils.getInsertableGalleryMediaFolders();
  }

  @Test
  void getExtensionsForImageGallery() {
    assertTrue(
        ArrayUtils.contains(
            getSupportedFileTypesForGallerySection(IMAGES_MEDIA_FLDER_NAME), "png"));
  }

  @Test
  void getExtensionsForDocumentGallery() {
    assertTrue(
        ArrayUtils.contains(
            getSupportedFileTypesForGallerySection(DOCUMENT_MEDIA_FLDER_NAME), "md"));
  }

  @Test
  void getExtensionsReturnsCopy() {
    String[] imageExts = getSupportedFileTypesForGallerySection(IMAGES_MEDIA_FLDER_NAME);
    imageExts[0] = "something else";
    String[] imageExts2 = getSupportedFileTypesForGallerySection(IMAGES_MEDIA_FLDER_NAME);
    assertFalse(imageExts2[0].equals("something else"));
  }

  @Test
  void testIsImageFile() {
    assertFalse(MediaUtils.isImageFile(null)); // null safe
    assertTrue(MediaUtils.isImageFile("tif"));
    assertTrue(MediaUtils.isImageFile("TIF")); // case insenstive
  }

  @Test
  void isChemistryFile() {
    assertFalse(MediaUtils.isChemistryFile(null)); // null safe
    assertFalse(MediaUtils.isChemistryFile("jpg"));
    assertTrue(MediaUtils.isChemistryFile("mol")); // case insensitive
    assertTrue(MediaUtils.isChemistryFile("MOL")); // case insensitive
  }

  @Test
  void testDMPsNotUploadable() {
    assertFalse(
        Arrays.stream(getInsertableGalleryMediaFolders())
            .anyMatch(s -> s.equals(MediaUtils.DMP_MEDIA_FLDER_NAME)));
  }

  @Test
  void isDNAFile() {
    assertFalse(MediaUtils.isDNAFile(null)); // null safe
    assertFalse(MediaUtils.isDNAFile(""));
    assertTrue(MediaUtils.isDNAFile("dna")); // case insensitive
    assertTrue(MediaUtils.isDNAFile("GB")); // case insensitive
    assertTrue(MediaUtils.isDNAFile("ab1")); // case insensitive
    assertTrue(MediaUtils.isDNAFile("AB1")); // case insensitive
  }

  @Test
  void testgetExtension() {
    assertNull(MediaUtils.getExtension(null)); // null safe
    assertEquals("tif", MediaUtils.getExtension("xyz.tif"));
    assertEquals(
        "wholeFilename",
        MediaUtils.getExtension("wholeFilename")); // no																				// extension
  }

  @Test
  void getExtensionFromPath() {
    assertEquals(DOCUMENT_MEDIA_FLDER_NAME, extractFileTypeFromPath("/a/b/c/d.file.txt"));
  }

  @Test
  void getIconLocation() {
    assertEquals("/images/icons/txt.png", MediaUtils.getIconPathForSuffix("txt.png"));
    assertEquals("/images/getInfo12.png", MediaUtils.getIconPathForSuffix("getInfo12.png"));
  }

  @Test
  void getUniqueFileName() {
    String originalFileName = "abcde.pdf";
    assertTrue(MediaUtils.makeFileNameUnique(originalFileName).matches("abcde_\\d+\\.pdf"));
  }

  @Test
  void supportedDNATypes() {
    List<String> dnaNameStrings = MediaUtils.supportedDNATypes();
    assertThat(dnaNameStrings, Matchers.hasItem("gb"));
    assertThrows(UnsupportedOperationException.class, () -> dnaNameStrings.add("pdb"));
  }
}
