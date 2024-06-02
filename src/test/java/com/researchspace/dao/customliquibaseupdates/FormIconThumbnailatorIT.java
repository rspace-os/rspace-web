package com.researchspace.dao.customliquibaseupdates;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.researchspace.core.util.imageutils.ImageUtils;
import com.researchspace.model.record.IconEntity;
import com.researchspace.service.IconImageManager;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.IOException;
import liquibase.exception.CustomChangeException;
import liquibase.exception.SetupException;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class FormIconThumbnailatorIT extends RealTransactionSpringTestBase {

  private @Autowired IconImageManager imgMgr;

  @Test
  public void testDoExecute() throws IOException, CustomChangeException, SetupException {
    final int initialCount = imgMgr.getAllIconIds().size();
    IconEntity toShrink = createAndSaveThumbnail("Picture1.png", "png");
    IconEntity unchanged = createAndSaveThumbnail("commentIcon30px.gif", "gif");
    toShrink = imgMgr.saveIconEntity(toShrink, false);
    Dimension b4ToShrink = new Dimension(toShrink.getWidth(), toShrink.getHeight());
    Dimension b4Unchanged = new Dimension(unchanged.getWidth(), unchanged.getHeight());
    unchanged = imgMgr.saveIconEntity(unchanged, false);
    // sanity c
    assertEquals(initialCount + 2, imgMgr.getAllIconIds().size());

    FormIconThumbnailator liquibase = new FormIconThumbnailator();
    liquibase.setUp();
    liquibase.execute(null);
    IconEntity shrunk = imgMgr.getIconEntity(toShrink.getId());
    Dimension after = new Dimension(shrunk.getWidth(), shrunk.getHeight());
    assertTrue(after.width < b4ToShrink.width && after.height < b4ToShrink.height);

    IconEntity unchangedAfter = imgMgr.getIconEntity(unchanged.getId());
    Dimension unchangedAfterD =
        new Dimension(unchangedAfter.getWidth(), unchangedAfter.getHeight());
    assertEquals(b4Unchanged, unchangedAfterD);
  }

  IconEntity createAndSaveThumbnail(String imgName, String type) throws IOException {

    IconEntity ie = new IconEntity();
    BufferedImage img = RSpaceTestUtils.getImageFromTestResourcesFolder(imgName);
    ie.setHeight(img.getHeight());
    ie.setWidth(img.getWidth());
    ie.setImgType("png");
    ie.setImgName(imgName + "_icon");
    ie.setIconImage(ImageUtils.toBytes(img, type));
    ie.setParentId(2L);
    return ie;
  }
}
