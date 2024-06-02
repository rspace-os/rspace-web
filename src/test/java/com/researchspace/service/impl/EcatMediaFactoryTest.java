package com.researchspace.service.impl;

import static com.researchspace.testutils.TestRunnerController.isJDK8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.model.EcatAudio;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EcatVideo;
import com.researchspace.model.FileProperty;
import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.IMediaFactory;
import com.researchspace.testutils.RSpaceTestUtils;
import ij.IJ;
import ij.ImagePlus;
import ij.io.FileSaver;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

public class EcatMediaFactoryTest {

  private static final String TIFF_ICON_PATH = "src/main/webapp/images/icons/tiff.png";
  private static final String UNPARSEABLE_TIFF_PATH = "testimages/ImageJError.tiff";
  private static final String IMAGE_J_ERROR_TIFF_NAME = "ImageJError.tiff";
  User user;
  IMediaFactory ecatImageFactoryAPI;
  EcatMediaFactory impl;
  @Mock ResourceLoader mockResourceLoader;
  @Mock Resource resource, resource2;
  @Rule public MockitoRule rule = MockitoJUnit.rule();

  @Before
  public void setUp() throws Exception {
    user = TestFactory.createAnyUser("user");
    impl = new EcatMediaFactory();
    impl.setResourceLoader(mockResourceLoader);
    ecatImageFactoryAPI = impl;
  }

  @After
  public void after() {
    impl.setMaxImageMemorySize(EcatImage.MAX_IMAGE_IN_MEMORY);
  }

  @Test
  public void createTiff2() throws IOException {
    ImagePlus image = IJ.openImage("src/test/resources/TestResources/MS20-09HCD3x400s3.tif");

    File tmpFile = File.createTempFile("test", "jpg");
    new FileSaver(image).saveAsJpeg(tmpFile.getAbsolutePath());
    assertTrue(tmpFile.length() > 10000);
  }

  @Test
  public void generateEcatImage() throws Exception {
    File file = RSpaceTestUtils.getResource("Picture1.png");
    assertTrue(file.exists());
    User user = TestFactory.createAnyUser("user");
    FileProperty fp = new FileProperty();

    EcatImage ecatImage =
        ecatImageFactoryAPI.generateEcatImage(user, fp, file, "png", file.getName(), null);
    assertNotNull(ecatImage.getOwner());
  }

  @Test
  public void generateEcatBigImage() throws Exception {
    File file = RSpaceTestUtils.getResource("Picture1.png");
    FileProperty fp = new FileProperty();
    EcatImage ecatImage =
        ecatImageFactoryAPI.generateEcatImage(user, fp, file, "png", "test.png", null);
    int maxWidth = (int) EcatImage.MAX_PAGE_DISPLAY_WIDTH;
    assertEquals(maxWidth, ecatImage.getWidthResized());
  }

  @Test(expected = IllegalArgumentException.class)
  public void generateEcatNullBUfferedImage() throws IOException, URISyntaxException {
    FileProperty fp = new FileProperty();
    EcatImage ecatImage =
        ecatImageFactoryAPI.generateEcatImage(user, fp, null, "png", "test.png", null);
  }

  @Test
  public void generateEcatImageSucceedsForResizeableUnparseableTiff()
      throws IOException, URISyntaxException {
    // RSPAC-1817 - this image is parsed OK on java11
    assumeTrue(isJDK8());

    FileProperty fp = new FileProperty();
    File file = RSpaceTestUtils.getResource(UNPARSEABLE_TIFF_PATH);
    when(mockResourceLoader.getResource(Mockito.endsWith("png"))).thenReturn(resource);
    when(resource.exists()).thenReturn(true);
    when(resource.getInputStream()).thenReturn(new FileInputStream(TIFF_ICON_PATH));
    // image will be resized
    impl.setMaxImageMemorySize(Long.MAX_VALUE);
    EcatImage ecatImage =
        ecatImageFactoryAPI.generateEcatImage(
            user, fp, file, "tiff", IMAGE_J_ERROR_TIFF_NAME, null);
    verify(resource, times(1)).getInputStream();
    assertNotNull(ecatImage.getWorkingImage());
  }

  @Test
  public void generateEcatImageSucceedsForNonresizableUnparseableTiff()
      throws IOException, URISyntaxException {
    // RSPAC-1817 - this image is parsed OK on java11
    assumeTrue(isJDK8());

    FileProperty fp = new FileProperty();
    File file = RSpaceTestUtils.getResource(UNPARSEABLE_TIFF_PATH);
    impl.setMaxImageMemorySize(1L); // we check non-resizable flow
    when(mockResourceLoader.getResource(Mockito.endsWith("png"))).thenReturn(resource);
    when(resource.exists()).thenReturn(true);
    when(resource.getInputStream()).thenReturn(new FileInputStream(TIFF_ICON_PATH));
    EcatImage ecatImage =
        ecatImageFactoryAPI.generateEcatImage(
            user, fp, file, "tiff", IMAGE_J_ERROR_TIFF_NAME, null);
    verify(resource, times(1)).getInputStream();
    assertNotNull(ecatImage.getWorkingImage());
  }

  @Test
  public void generateEcatVideo() throws Exception {
    User user = TestFactory.createAnyUser("user");
    FileProperty fp = new FileProperty();
    EcatVideo ecatVideo = ecatImageFactoryAPI.generateEcatVideo(user, fp, "flv", "video.flv", null);
    assertNotNull(ecatVideo.getOwner());
  }

  @Test
  public void generateEcatAudio() throws Exception {
    User user = TestFactory.createAnyUser("user");
    FileProperty fp = new FileProperty();

    EcatAudio ecatAudio =
        ecatImageFactoryAPI.generateEcatAudio(user, fp, "mp3", "mpthreetest.mp3", null);
    assertNotNull(ecatAudio.getOwner());
  }

  @Test
  public void getIconSuffixBytes() throws IOException, URISyntaxException {
    FileProperty fp = new FileProperty();
    File file = RSpaceTestUtils.getResource(UNPARSEABLE_TIFF_PATH);
    when(mockResourceLoader.getResource(Mockito.endsWith("png"))).thenReturn(resource);
    when(resource.exists()).thenReturn(true);
    when(resource.getInputStream()).thenReturn(new FileInputStream(TIFF_ICON_PATH));
    // image will be resized
    byte[] data = impl.getFileSuffixIcon("x.tiff");
    assertTrue(data.length > 0);
  }

  @Test
  public void getIconSuffixBytesReturnsDefault() throws IOException, URISyntaxException {

    when(mockResourceLoader.getResource(Mockito.endsWith("png"))).thenReturn(resource, resource2);
    when(resource.exists()).thenReturn(false);
    when(resource2.getInputStream()).thenReturn(new FileInputStream(TIFF_ICON_PATH));
    // image will be resized
    byte[] data = impl.getFileSuffixIcon("x.XYZ");
    assertTrue(data.length > 0);
  }
}
