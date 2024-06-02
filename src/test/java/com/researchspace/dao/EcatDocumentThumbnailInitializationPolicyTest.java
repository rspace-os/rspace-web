package com.researchspace.dao;

import static org.junit.Assert.assertTrue;

import com.researchspace.core.util.imageutils.ImageUtils;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.ImageBlob;
import com.researchspace.model.User;
import com.researchspace.model.record.EcatDocumentThumbnailInitializationPolicy;
import com.researchspace.model.record.TestFactory;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import java.awt.image.BufferedImage;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class EcatDocumentThumbnailInitializationPolicyTest extends SpringTransactionalTest {

  EcatDocumentThumbnailInitializationPolicy policy;
  private User anyuser;
  BufferedImage thumbImage = null;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    policy = new EcatDocumentThumbnailInitializationPolicy();
    anyuser = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(anyuser);
    thumbImage = RSpaceTestUtils.getImageFromTestResourcesFolder("commentIcon30px.gif");
    logoutAndLoginAs(anyuser);
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testLazyLoadingofEcatDocumentThumbnail() throws Exception {
    EcatDocumentFile doc = TestFactory.createEcatDocument(0, anyuser);
    byte[] thumbdata = ImageUtils.toBytes(thumbImage, "gif");
    ImageBlob thumbBlob = new ImageBlob(thumbdata);
    doc.setThumbNail(thumbBlob);
    doc = (EcatDocumentFile) recordDao.save(doc);

    clearSessionAndEvictAll();
    final EcatDocumentFile doc2 = (EcatDocumentFile) recordDao.get(doc.getId());
    clearSessionAndEvictAll();
    doc2.getThumbNail().getId(); // ok to get id
    assertLazyInitializationExceptionThrown(() -> doc2.getThumbNail().getData());

    clearSessionAndEvictAll();
    final EcatDocumentFile doc3 = (EcatDocumentFile) recordDao.get(doc.getId());
    policy.initialize(doc3);
    clearSessionAndEvictAll();
    assertTrue(doc3.getThumbNail().getData().length > 40);
  }
}
