package com.researchspace.core.util.imageutils;

import static com.researchspace.core.testutil.CoreTestUtils.assertIllegalArgumentException;
import static com.researchspace.core.util.imageutils.ImageUtils.canScaleBySampling;
import static com.researchspace.core.util.imageutils.ImageUtils.convertTiffToPng;
import static com.researchspace.core.util.imageutils.ImageUtils.createThumbnail;
import static com.researchspace.core.util.imageutils.ImageUtils.getBufferedImageFromInputImageStream;
import static com.researchspace.core.util.imageutils.ImageUtils.getBufferedImageFromTiffFile;
import static com.researchspace.core.util.imageutils.ImageUtils.getBufferedImageFromUploadedFile;
import static com.researchspace.core.util.imageutils.ImageUtils.isTiff;
import static com.researchspace.core.util.imageutils.ImageUtils.scaleBySampling;
import static java.io.File.createTempFile;
import static org.apache.commons.io.FileUtils.getTempDirectory;
import static org.apache.commons.io.FileUtils.openInputStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

import com.researchspace.core.util.RSpaceCoreTestUtils;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

public class ImageUtilsTest {

  @Mock IMultipartFile multipartFile;
  String tiffFile = "src/test/resources/Picture1.tiff";
  String pngfile = "src/test/resources/Picture1.png";

  @BeforeEach
  public void setUp() throws Exception {
    openMocks(this);
  }

  @AfterEach
  public void tearDown() throws Exception {}

  @Test
  public void getImageFromTiff() throws IOException {
    final FileInputStream is = FileUtils.openInputStream(new File(tiffFile));
    assertNotNull(getBufferedImageFromUploadedFile("tiff", is));
  }

  @Test
  public void nullHandlingOfInputStreamsThrowsIAE() throws IOException {
    assertThrows(IllegalArgumentException.class, () -> ImageIO.createImageInputStream(null));
    assertThrows(IllegalArgumentException.class, () -> ImageIO.getImageReaders(null));
  }

  @Test
  public void getImageFromMultipartFile() throws IOException {
    final FileInputStream is = openInputStream(new File(pngfile));
    when(multipartFile.getInputStream()).thenReturn(is);
    when(multipartFile.getOriginalFilename()).thenReturn("Picture1.png");
    assertNotNull(getBufferedImageFromUploadedFile(multipartFile));
    verify(multipartFile, atLeastOnce()).getInputStream();
    verify(multipartFile, atLeastOnce()).getOriginalFilename();
  }

  @Test
  public void scale() throws IOException {
    final InputStream is = getInputStreamToResource("Picture1.png");
    final InputStream is2 = getInputStreamToResource("Picture1.png");

    BufferedImage original = getBufferedImageFromInputImageStream(is).get();
    final int MAX_WIDTH = 120;
    assertTrue(original.getWidth() > MAX_WIDTH);
    BufferedImage scaled = ImageUtils.scale(is2, MAX_WIDTH, "png").get();
    assertTrue(scaled.getWidth() <= MAX_WIDTH);
    is.close();
    is2.close();
  }

  private InputStream getInputStreamToResource(String string) throws IOException {
    String file = "src/test/resources/Picture1.png";
    final FileInputStream is = FileUtils.openInputStream(new File(file));
    return is;
  }

  @Test
  public void thumbnailThrowsIAEIfDimensionsNotValid() throws IOException {
    final InputStream is = getInputStreamToResource("Picture1.png");
    BufferedImage original = getBufferedImageFromInputImageStream(is).get();
    is.close();
    assertIllegalArgumentException(
        () -> createThumbnail(original, 0, 0, new ByteArrayOutputStream(), "png"));
  }

  @Test
  public void creatThumbnail() throws IOException {
    final InputStream is = getInputStreamToResource("Picture1.png");
    BufferedImage original = getBufferedImageFromInputImageStream(is).get();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    createThumbnail(original, 24, 24, baos, "png");
    is.close();
    assertTrue(baos.toByteArray().length > 0);
  }

  @Test
  public void isTiffByName() {
    assertTrue(isTiff("tiff"));
    assertTrue(isTiff("tif"));
    assertTrue(isTiff("tIff")); // case insensitive
  }

  @Test
  public void convertTiffToPngArgValidation() throws Exception {
    final File outFolder = getTempDirectory();
    // is not a tiff!
    assertIllegalArgumentException(() -> convertTiffToPng(new File(pngfile), outFolder, null));
    // outfolder is not a folder
    assertIllegalArgumentException(
        () -> convertTiffToPng(new File(tiffFile), createTempFile("any", "any"), null));
    assertIllegalArgumentException(() -> convertTiffToPng(null, outFolder, null));
  }

  @Test
  public void convertTffToPng() throws Exception {
    final File outFolder = FileUtils.getTempDirectory();
    // default name
    File newpngFile = convertTiffToPng(new File(tiffFile), outFolder, null);
    assertTrue(newpngFile.exists());
    assertTrue(newpngFile.getName().equals("Picture1.png"));
    // specified name
    newpngFile = convertTiffToPng(new File(tiffFile), outFolder, "xyz");
    assertTrue(newpngFile.exists());
    assertTrue(newpngFile.getName().equals("xyz.png"));
    // specified name wth spaces is ok
    newpngFile = convertTiffToPng(new File(tiffFile), outFolder, "xyz 123 abc");
    assertTrue(newpngFile.exists());
    assertTrue(newpngFile.getName().equals("xyz 123 abc.png"));
  }

  @Test
  @DisplayName("Invalid tiff returns empty optional")
  public void badTiff() throws IOException {
    File f = RSpaceCoreTestUtils.getResource("testImages/ImageJError.tiff");
    assertFalse(getBufferedImageFromTiffFile(f).isPresent());
  }

  @Test
  @DisplayName("Rescale image by sampling")
  public void sampledScale() throws Exception {
    FileInputStream fin = new FileInputStream(RSpaceCoreTestUtils.getResource("Picture1.png"));
    assertTrue(canScaleBySampling(fin));
    fin.close();
    fin = new FileInputStream(RSpaceCoreTestUtils.getResource("Picture1.png"));
    BufferedImage img = scaleBySampling(fin, ImageUtils.MAX_PAGE_DISPLAY_WIDTH, "Picture1.jpg");
    assertNotNull(img);
    assertTrue(img.getWidth() <= ImageUtils.MAX_PAGE_DISPLAY_WIDTH);

    // try sampled scale of tiff file, that seems to work after recent addition of zxing lib
    fin = new FileInputStream(RSpaceCoreTestUtils.getResource("Picture1.tiff"));
    assertTrue(canScaleBySampling(fin));
    fin.close();
    fin = new FileInputStream(RSpaceCoreTestUtils.getResource("Picture1.tiff"));
    BufferedImage tiffImg =
        scaleBySampling(fin, ImageUtils.MAX_PAGE_DISPLAY_WIDTH, "Picture1.tiff");
    assertNotNull(tiffImg);
    assertTrue(tiffImg.getWidth() <= ImageUtils.MAX_PAGE_DISPLAY_WIDTH);

    // random non-image file just returns false
    fin = new FileInputStream(RSpaceCoreTestUtils.getResource("xml-with-dtd.xml"));
    assertFalse(canScaleBySampling(fin));
  }

  @Test
  @DisplayName("Rotate invalid tiff file fails gracefully")
  public void rotateBadTiff() throws IOException {
    File notATiff = RSpaceCoreTestUtils.getResource("mathEquation.svg");
    File r1 = File.createTempFile("ro1", ".tif");
    assertFalse(ImageUtils.rotateTiff(notATiff, 1, r1));
  }

  @Test
  @DisplayName("Rotate tiff by multiples of Pi/2")
  public void rotateTiff() throws IOException {
    File tiff = RSpaceCoreTestUtils.getResource("Picture2.tif");

    File r1 = File.createTempFile("ro1", ".tif");
    assertTrue(ImageUtils.rotateTiff(tiff, 1, r1));
    File r2 = File.createTempFile("ro2", ".tif");
    assertTrue(ImageUtils.rotateTiff(tiff, 2, r2));
    File r3 = File.createTempFile("ro3", ".tif");
    assertTrue(ImageUtils.rotateTiff(tiff, 3, r3));
    // its > 3 are reduced mod 4
    assertTrue(ImageUtils.rotateTiff(tiff, 7, r3));
    assertFalse(ImageUtils.rotateTiff(tiff, 0, r3));

    // now assert Rotations
    BufferedImage originalTiff = getBufferedImageFromTiffFile(tiff).get();
    int randomX = 193;
    int randomY = 140;
    int originalRGB = originalTiff.getRGB(randomX, randomY);
    // 90
    BufferedImage r90 = getBufferedImageFromTiffFile(r1).get();
    assertWidthAndHeightSwapped(originalTiff, r90);
    assert90DegreePixelSwap(originalTiff, randomX, randomY, originalRGB, r90);
    // 180
    BufferedImage r180 = getBufferedImageFromTiffFile(r2).get();
    assertWidthAndHeightUnchanged(originalTiff, r180);
    assert180DegreePixelSwap(originalTiff, randomX, randomY, originalRGB, r180);
    // 270
    BufferedImage r270 = getBufferedImageFromTiffFile(r3).get();
    assertWidthAndHeightSwapped(originalTiff, r270);
    assert270DegreePixelSwap(originalTiff, randomX, randomY, originalRGB, r270);
  }

  @Test
  @DisplayName("Rotate image by multiples of pi/2")
  public void rotateImage() throws IOException {
    InputStream is = RSpaceCoreTestUtils.getInputStreamOnFromTestResourcesFolder("Picture1.png");
    BufferedImage originalImage = getBufferedImageFromInputImageStream(is).get();
    int randomX = 193;
    int randomY = 140;
    int originalRGB = originalImage.getRGB(randomX, randomY);

    // rotate 90
    BufferedImage rotated = ImageUtils.rotateImage(originalImage, Math.PI / 2); // 90 degrees
    assertWidthAndHeightSwapped(originalImage, rotated);
    assert90DegreePixelSwap(originalImage, randomX, randomY, originalRGB, rotated);

    // 180 degrees
    BufferedImage rotatedBy180 = ImageUtils.rotateImage(originalImage, Math.PI); // 180 degrees
    assertWidthAndHeightUnchanged(originalImage, rotatedBy180);
    assert180DegreePixelSwap(originalImage, randomX, randomY, originalRGB, rotatedBy180);

    // 270 degrees
    BufferedImage rotatedBy270 =
        ImageUtils.rotateImage(originalImage, Math.PI * 3 / 2); // 180 degrees
    assertWidthAndHeightSwapped(originalImage, rotatedBy270);
    assert270DegreePixelSwap(originalImage, randomX, randomY, originalRGB, rotatedBy270);
  }

  private void assert270DegreePixelSwap(
      BufferedImage originalImage,
      int randomX,
      int randomY,
      int originalRGB,
      BufferedImage rotatedBy270) {
    assertEquals(
        originalRGB, rotatedBy270.getRGB(randomY - 1, originalImage.getWidth() - randomX - 1));
  }

  private void assert180DegreePixelSwap(
      BufferedImage originalImage,
      int randomX,
      int randomY,
      int originalRGB,
      BufferedImage rotatedBy180) {
    assertEquals(
        originalRGB,
        rotatedBy180.getRGB(
            originalImage.getWidth() - randomX - 1, originalImage.getHeight() - randomY - 1));
  }

  private void assertWidthAndHeightUnchanged(
      BufferedImage originalImage, BufferedImage rotatedBy180) {
    assertEquals(originalImage.getWidth(), rotatedBy180.getWidth());
    assertEquals(originalImage.getHeight(), rotatedBy180.getHeight());
  }

  private void assert90DegreePixelSwap(
      BufferedImage originalImage,
      int randomX,
      int randomY,
      int originalRGB,
      BufferedImage rotated) {
    assertEquals(originalRGB, rotated.getRGB(originalImage.getHeight() - randomY - 1, randomX - 1));
  }

  private void assertWidthAndHeightSwapped(BufferedImage originalImage, BufferedImage rotated) {
    assertEquals(originalImage.getWidth(), rotated.getHeight());
    assertEquals(originalImage.getHeight(), rotated.getWidth());
  }

  @Test
  @DisplayName("Parse web base64 png/jpeg image with helper methods")
  public void checkBase64HelperMethods() throws IOException {

    NullPointerException nullArgNpe =
        Assertions.assertThrows(
            NullPointerException.class, () -> ImageUtils.getExtensionFromBase64DataImage(null));
    assertEquals(
        "Expected base64 web image string but was null or empty.", nullArgNpe.getMessage());
    IllegalArgumentException notBase64Iae =
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> ImageUtils.getImageBytesFromBase64DataImage("data:image/png;base64,»Þõûã"));
    assertEquals("Image string doesn't seem to be base64-encoded", notBase64Iae.getMessage());
    IllegalArgumentException notBase64Iae2 =
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> ImageUtils.getImageBytesFromBase64DataImage("asdf"));
    assertEquals("Expected base64 web image string to contain a ','.", notBase64Iae2.getMessage());

    byte[] imageBytes =
        Base64.decodeBase64(RSpaceCoreTestUtils.getResourceAsByteArray("Picture1.b64"));
    String exampleImage = ImageUtils.getBase64DataImageFromImageBytes(imageBytes, "png");

    assertEquals("png", ImageUtils.getExtensionFromBase64DataImage(exampleImage));
    assertEquals(83667, ImageUtils.getImageBytesFromBase64DataImage(exampleImage).length);
  }
}
