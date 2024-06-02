package com.researchspace.service.impl;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.files.service.FileStore;
import com.researchspace.model.EcatImage;
import com.researchspace.model.FileProperty;
import com.researchspace.model.ImageBlob;
import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.RecordManager;
import com.researchspace.testutils.RSpaceTestUtils;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class ImageProcessorImplTest {

  public @Rule MockitoRule rule = MockitoJUnit.rule();

  @Mock RecordManager rcdMgr;
  @Mock FileStore fileStore;
  EcatImage img = null;
  User anyUser = null;
  static BufferedImage anyBR = null;

  static class ImageProcessorImplTSS extends ImageProcessorImpl {
    boolean setupRotateOrigFileStoreOK = true;
    boolean setupRotateTiffOK = true;

    Optional<BufferedImage> rotateFileStoreImage(
        byte timesToRotate, EcatImage ecatImage, FileProperty sourceFileProperty)
        throws IOException {
      return setupRotateOrigFileStoreOK ? Optional.of(anyBR) : Optional.empty();
    }

    Optional<BufferedImage> rotateOriginalTiffFile(
        Byte timesToRotate, EcatImage ecatImage, FileProperty sourceFileProperty)
        throws IOException {
      return setupRotateTiffOK ? Optional.of(anyBR) : Optional.empty();
    }

    BufferedImage rotateImage(byte timesToRotate, BufferedImage workingBI) {
      return workingBI;
    }

    BufferedImage readWorkingImage(EcatImage ecatImage) throws IOException {
      return anyBR;
    }
  }

  @InjectMocks ImageProcessorImplTSS imgHandler;

  @Before
  public void setUp() throws Exception {
    anyUser = TestFactory.createAnyUser("any");
    img = TestFactory.createEcatImage(1L);
    img.setOwner(anyUser);
    anyBR = RSpaceTestUtils.getImageFromTestResourcesFolder("Picture1.png");
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void rotateOKSmallFileWitoutWorkingImage() throws IOException {
    setupImage();
    //		when(mediaFac.updateThumbnailImage(Mockito.eq(img), Mockito.any(BufferedImage.class)))
    //		  .thenReturn(img);
    when(rcdMgr.save(img, anyUser)).thenReturn(img);
    imgHandler.rotate(img, (byte) 1, anyUser);
    verify(rcdMgr).save(img, anyUser);
    assertEquals(anyBR.getWidth(), img.getWidth());
    assertEquals(anyBR.getHeight(), img.getHeight());
  }

  @Test
  public void rotateOKTiffFile() throws IOException {
    setupImage();
    // set up as tiff
    img.setExtension("tif");
    img.setWorkingImage(anyImageBlob());
    //		when(mediaFac.updateThumbnailImage(Mockito.eq(img), Mockito.any(BufferedImage.class)))
    //		  .thenReturn(img);
    //		when(mediaFac.updateWorkingImage(Mockito.eq(img), Mockito.any(BufferedImage.class)))
    //		  .thenReturn(img);
    when(rcdMgr.save(img, anyUser)).thenReturn(img);
    imgHandler.rotate(img, (byte) 1, anyUser);

    verify(rcdMgr).save(img, anyUser);
    assertEquals(anyBR.getWidth(), img.getWidth());
    assertEquals(anyBR.getHeight(), img.getHeight());
    assertEquals(1, img.getRotation());
  }

  private void setupImage() {
    FileProperty fp = new FileProperty();
    fp.setFileOwner(anyUser.getUsername());

    img.setImageThumbnailed(anyImageBlob());
    img.setFileProperty(fp);
  }

  @Test
  public void imgNotUpdatedIfTiffRotationFails() throws IOException {
    imgHandler.setupRotateTiffOK = false;
    setupImage();
    // set up as tiff
    img.setExtension("tif");
    img.setWorkingImage(anyImageBlob());

    //		when(mediaFac.updateThumbnailImage(Mockito.eq(img), Mockito.any(BufferedImage.class)))
    //		  .thenReturn(img);
    //		when(mediaFac.updateWorkingImage(Mockito.eq(img), Mockito.any(BufferedImage.class)))
    //		  .thenReturn(img);
    when(rcdMgr.save(img, anyUser)).thenReturn(img);
    imgHandler.rotate(img, (byte) 1, anyUser);

    verify(rcdMgr, never()).save(img, anyUser);
    assertEquals(img.getWidth(), img.getWidth());
    assertEquals(img.getHeight(), img.getHeight());
    assertEquals(0, img.getRotation());
  }

  @Test
  public void imageNotUpdatedIfRotateSmallFileFails() throws IOException {
    setupImage();
    imgHandler.setupRotateOrigFileStoreOK = false; // will return empty
    imgHandler.rotate(img, (byte) 1, anyUser);
    verify(rcdMgr, never()).save(img, anyUser);
  }

  private ImageBlob anyImageBlob() {
    return new ImageBlob(new byte[] {1, 2, 3, 4, 5});
  }
}
