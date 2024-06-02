package com.researchspace.service;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static org.junit.Assert.assertEquals;

import com.researchspace.model.EcatImage;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.User;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * This test class is outside of the Spring tests transaction environment. This is because auditing
 * only happens after a transaction is really committed to the database, and regular Spring Tests
 * always roll back. <br>
 */
public class BaseRecordManagerIT extends RealTransactionSpringTestBase {

  private @Autowired BaseRecordManager baseRecordMgr;

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void retrieveMediaFileByVersion() throws Exception {
    User anyUser = createAndSaveUser(getRandomName(10));
    setUpUserWithoutCustomContent(anyUser);
    logoutAndLoginAs(anyUser);

    EcatImage image = addImageToGallery(anyUser);
    image = updateImageInGallery(image.getId(), anyUser);

    EcatMediaFile firstImg =
        baseRecordMgr.retrieveMediaFile(anyUser, image.getId(), null, 1L, null);
    assertEquals(1, firstImg.getVersion());
    EcatMediaFile secondImg =
        baseRecordMgr.retrieveMediaFile(anyUser, image.getId(), null, 2L, null);
    assertEquals(2, secondImg.getVersion());
    EcatMediaFile latestImg =
        baseRecordMgr.retrieveMediaFile(anyUser, image.getId(), null, null, null);
    assertEquals(2, latestImg.getVersion());
  }
}
