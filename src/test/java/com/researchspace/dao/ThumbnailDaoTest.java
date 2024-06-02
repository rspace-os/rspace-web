package com.researchspace.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;

import com.researchspace.model.Thumbnail;
import com.researchspace.model.Thumbnail.SourceType;
import com.researchspace.model.record.TestFactory;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.File;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ThumbnailDaoTest extends SpringTransactionalTest {

  @Autowired ThumbnailDao thumbnailDao;
  @Autowired EcatImageDao imageDao;

  @Test
  public void testGetByFieldId() throws Exception {
    Thumbnail thumbnail1 = TestFactory.createThumbnail(100, 100);
    thumbnail1.setSourceParentId(2L);
    thumbnailDao.save(thumbnail1);
    assertEquals(1, thumbnailDao.getByFieldId(thumbnail1.getSourceParentId()).size());
  }

  @Test
  public void testThumbnails() throws Exception {
    File imageTempFile = File.createTempFile("ImageTest", "png");
    Thumbnail thumbnail1 = TestFactory.createThumbnail(100, 100);
    thumbnailDao.save(thumbnail1);

    Thumbnail thumbnail2 = TestFactory.createThumbnail(200, 200);
    thumbnailDao.save(thumbnail2);

    // Try retrieving a couple different size thumbnails
    Thumbnail thumbnail100 = thumbnailDao.getThumbnail(thumbnail1);

    assertNotNull(thumbnail100);
    assertEquals(100, thumbnail100.getWidth());
    assertEquals(100, thumbnail100.getHeight());

    Thumbnail thumbnail200 = thumbnailDao.getThumbnail(thumbnail2);

    assertNotNull(thumbnail200);
    assertEquals(200, thumbnail200.getWidth());
    assertEquals(200, thumbnail200.getHeight());

    // Make sure it's handing us separate objects for different sizes
    assertNotSame(thumbnail100.getId(), thumbnail200.getId());

    assertEquals(2, thumbnailDao.getAll().size());

    // Check that a get against a nonexistent size doesn't work
    Thumbnail thumbnail3 = TestFactory.createThumbnail(300, 300);
    Thumbnail thumbnail300 = thumbnailDao.getThumbnail(thumbnail3);
    assertNull(thumbnail300);

    // Make sure a delete against a different id doesn't delete any of our
    // thumbnails
    thumbnailDao.deleteAllThumbnails(SourceType.IMAGE, 2l);
    assertEquals(thumbnailDao.getAll().size(), 2);

    // Try deleting one of our thumbnails. Make sure it only deleted the one
    // we intended.
    thumbnailDao.remove(thumbnail200.getId());
    thumbnail100 = thumbnailDao.getThumbnail(thumbnail1);
    thumbnail200 = thumbnailDao.getThumbnail(thumbnail2);

    assertNotNull(thumbnail100);
    assertNull(thumbnail200);

    // Make sure a delete against a different parent id doesn't delete any
    // of our thumbnails
    thumbnailDao.deleteAllThumbnails(SourceType.IMAGE, 1l, -1l);
    assertEquals(thumbnailDao.getAll().size(), 1);

    // Next, we will add a thumbnail with a source parent id
    Thumbnail thumbnail5 = TestFactory.createThumbnail(100, 100);
    thumbnail5.setSourceParentId(10l);
    thumbnailDao.save(thumbnail5);

    // And now, we will add a thumbnail with a source parent id and revision
    // id
    Thumbnail thumbnail4 = TestFactory.createThumbnail(100, 100);
    thumbnail4.setSourceParentId(10l);
    thumbnail4.setRevision(20l);
    thumbnailDao.save(thumbnail4);

    thumbnail100 = thumbnailDao.getThumbnail(thumbnail1);
    Thumbnail thumbnail100_5 = thumbnailDao.getThumbnail(thumbnail5);
    Thumbnail thumbnail100_4 = thumbnailDao.getThumbnail(thumbnail4);

    // Test that retrieval by source id gets a separate thumbnail
    assertNotNull(thumbnail100_5);
    assertEquals(100, thumbnail100_5.getWidth());
    assertEquals(100, thumbnail100_5.getHeight());

    // Test that retrieval by source id gets a separate thumbnail
    assertNotNull(thumbnail100_4);
    assertEquals(100, thumbnail100_4.getWidth());
    assertEquals(100, thumbnail100_4.getHeight());

    assertNotSame(thumbnail100.getId(), thumbnail100_5.getId());
    assertNotSame(thumbnail100.getId(), thumbnail100_4.getId());
    assertNotSame(thumbnail100_5.getId(), thumbnail100_4.getId());

    assertEquals(thumbnailDao.getAll().size(), 3);

    // Delete using the parent id and revision id and make sure it only
    // deletes the correct one
    thumbnailDao.deleteAllThumbnails(SourceType.IMAGE, 1l, 10l, 20l);
    assertEquals(thumbnailDao.getAll().size(), 2);
    for (Thumbnail thumbnail : thumbnailDao.getAll()) {
      assertNotSame(thumbnail.getId(), thumbnail100_4.getId());
    }

    // Delete using the parent id and make sure it only deletes the correct
    // one
    thumbnailDao.deleteAllThumbnails(SourceType.IMAGE, 1l, 10l);
    assertEquals(thumbnailDao.getAll().size(), 1);
    assertEquals(thumbnailDao.getAll().get(0).getId(), thumbnail1.getId());

    // Now delete all thumbnails and make sure they're gone
    thumbnailDao.deleteAllThumbnails(SourceType.IMAGE, 1l);
    assertEquals(thumbnailDao.getAll().size(), 0);
  }
}
