package com.researchspace.dao;

import static org.junit.Assert.assertTrue;

import com.researchspace.model.EcatComment;
import com.researchspace.model.EcatCommentItem;
import java.util.List;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class EcatCommentDaoTest extends BaseDaoTestCase {

  private @Autowired EcatCommentDao ecatCommentDao;
  private @Autowired TextSearchDao textSearchDao;

  @Test
  public void testTextSearch() throws Exception {
    Long pid = 25L;
    EcatComment cm1 = new EcatComment();
    cm1.setComName("image1");
    cm1.setAuthor("someone1");
    cm1.setParentId(pid);

    EcatCommentItem itm11 = new EcatCommentItem();
    itm11.setItemContent("This is a comment 1 item 1");
    itm11.setLastUpdater("sunny1");
    cm1.addCommentItem(itm11);

    EcatCommentItem itm12 = new EcatCommentItem();
    itm12.setItemContent("This is a comment 1 item 2");
    itm12.setLastUpdater("sunny1");
    cm1.addCommentItem(itm12);
    ecatCommentDao.addComment(cm1);
    log.debug("COMMDEBUG: after add the id become " + cm1.getComId());

    EcatComment cm2 = new EcatComment();
    cm2.setComName("image2");
    cm2.setAuthor("someone2");
    cm2.setParentId(pid);

    EcatCommentItem itm21 = new EcatCommentItem();
    itm21.setItemContent("This is a comment 1 item 3");
    itm21.setLastUpdater("sunny2");
    cm2.addCommentItem(itm21);

    EcatCommentItem itm22 = new EcatCommentItem();
    itm22.setItemContent("This is a comment 1 item 4");
    itm22.setLastUpdater("sunny1");
    cm2.addCommentItem(itm22);
    ecatCommentDao.addComment(cm2);
    List<EcatComment> lst = ecatCommentDao.getCommentAll(pid);
    assertTrue(lst.size() > 0);

    String flds[] = {
      "comName", "author", "items.itemName", "items.fields.fieldData", "items.owner.username"
    };
    String match = "comment";
    List rst = textSearchDao.searchText(flds, match, EcatComment.class);
    assertTrue(rst.size() >= 0);
  }
}
