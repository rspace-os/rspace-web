package com.researchspace.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import com.researchspace.model.EcatImage;
import com.researchspace.model.Thumbnail;
import com.researchspace.model.Thumbnail.SourceType;
import com.researchspace.model.User;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.IOException;
import java.net.URISyntaxException;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ThumbnailManagerTest extends SpringTransactionalTest {

  @Autowired private ThumbnailManager thumbnailMgr;

  private User user;

  @Before
  public void setUp() throws Exception {
    user = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(user);
    logoutAndLoginAs(user);
  }

  @Test
  public void testThumbnailRegeneration()
      throws IOException, IllegalArgumentException, URISyntaxException {

    // upload two images
    EcatImage image1 = addImageToGallery(user, "Picture1.png");
    EcatImage image2 = addImageToGallery(user, "Picture2.png");

    // create two thumbnails for image, one revisioned
    Thumbnail imgThumbnail = new Thumbnail();
    imgThumbnail.setSourceId(image1.getId());
    imgThumbnail.setSourceType(SourceType.IMAGE);
    imgThumbnail.setWidth(50);
    imgThumbnail.setHeight(50);

    Thumbnail img2Thumbnail = imgThumbnail.getCopy();
    img2Thumbnail.setSourceId(image2.getId());

    // generate the thumbnails
    Thumbnail th1 = thumbnailMgr.getThumbnail(imgThumbnail, user);
    assertNotNull(th1);
    assertNotNull(th1.getThumbnailFP());
    Long th1Id = th1.getId();
    Long th1FpId = th1.getThumbnailFP().getId();

    Thumbnail th2 = thumbnailMgr.getThumbnail(img2Thumbnail, user);
    assertNotNull(th2);
    assertNotNull(th2.getThumbnailFP());
    Long th2Id = th2.getId();
    Long th2FpId = th2.getThumbnailFP().getId();

    // now delete thumbnails for first image
    thumbnailMgr.deleteImageThumbnails(image1, user);

    // retrieve again, thumbnail connected to first image should be re-generated
    imgThumbnail.setId(null);
    Thumbnail newTh1 = thumbnailMgr.getThumbnail(imgThumbnail, user);
    assertNotNull(newTh1);
    assertNotNull(newTh1.getThumbnailFP());
    assertNotEquals(th1Id, newTh1.getId());
    assertNotEquals(th1FpId, newTh1.getThumbnailFP().getId());

    // thumbnail connected to second image should stay the same
    img2Thumbnail.setId(null);
    Thumbnail newTh2 = thumbnailMgr.getThumbnail(img2Thumbnail, user);
    assertNotNull(newTh2);
    assertNotNull(newTh2.getThumbnailFP());
    assertEquals(th2Id, newTh2.getId());
    assertEquals(th2FpId, newTh2.getThumbnailFP().getId());
  }
}
