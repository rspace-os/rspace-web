package com.researchspace.dao;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.hibernate.LazyInitializationException;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.core.util.imageutils.ImageUtils;
import com.researchspace.model.EcatImage;
import com.researchspace.model.ImageBlob;
import com.researchspace.model.User;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.testutils.TestFactory;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class EcatImageDaoTest extends SpringTransactionalTest {

  private @Autowired EcatImageDao imageDao;
  private BufferedImage thumbImage = null;
  private BufferedImage workingImage = null;
  User user = null;

  @BeforeEach
  public void setUp() throws Exception {
    thumbImage = RSpaceTestUtils.getImageFromTestResourcesFolder("commentIcon30px.gif");
    workingImage = RSpaceTestUtils.getImageFromTestResourcesFolder("Picture1.png");
    user = createAndSaveRandomUser();
  }

  @Test
  public void testGetThumbnailBlobIsLazilyLoaded() throws Exception {
    byte[] thumbdata = ImageUtils.toBytes(thumbImage, "gif");
    ImageBlob thumbBlob = new ImageBlob(thumbdata);
    byte[] workingdata = ImageUtils.toBytes(workingImage, "png");
    ImageBlob workingBlob = new ImageBlob(workingdata);
    EcatImage image = TestFactory.createEcatImage(2L);
    image.setImageThumbnailed(thumbBlob);
    image.setWorkingImage(workingBlob);
    image.setOwner(user);
    image = imageDao.save(image);
    clearSessionAndEvictAll();
    // fresh load the image lazily
    final EcatImage image2 = imageDao.get(image.getId());
    clearSessionAndEvictAll();
    // blobs are proxied
    ImageBlob loadedThumbnail = image2.getImageThumbnailed();
    ImageBlob loadedWorkingImage = image2.getWorkingImage();
    assertThrows(LazyInitializationException.class, () -> loadedThumbnail.getData());
    assertThrows(LazyInitializationException.class, () -> loadedWorkingImage.getData());
    clearSessionAndEvictAll();

    // now initialize blobs
    final EcatImage image3 = imageDao.get(image.getId());
    image3.getImageThumbnailed().getData();
    image3.getWorkingImage().getData();
    clearSessionAndEvictAll();
    final int NONEMPTY_BTYE_ARRAY_LEN = 100;
    assertTrue(image3.getImageThumbnailed().getData().length > NONEMPTY_BTYE_ARRAY_LEN);
    assertTrue(image3.getWorkingImage().getData().length > NONEMPTY_BTYE_ARRAY_LEN);
  }
}
