package com.researchspace.service;

import static org.junit.Assert.assertTrue;

import com.researchspace.model.EcatComment;
import com.researchspace.model.EcatCommentItem;
import com.researchspace.model.User;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/** Comment test moved from RecordManagerTest */
public class EcatCommentManagerTest extends SpringTransactionalTest {

  @Autowired private EcatCommentManager commentManager;

  private final Logger log = LoggerFactory.getLogger(getClass());

  private User user;

  @Before
  public void setUp() throws IllegalAddChildOperation {
    user = createAndSaveUserIfNotExists(getRandomAlphabeticString("any"));
    initialiseContentWithExampleContent(user);
    assertTrue(user.isContentInitialized());
    logoutAndLoginAs(user);
  }

  @After
  public void tearDown() throws Exception {
    RSpaceTestUtils.logout();
    super.tearDown();
  }

  @Test
  @SuppressWarnings("rawtypes")
  public void testComment() {
    final Long pid = 23L;
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
    commentManager.addComment(cm1);
    log.debug("COMMDEBUG: after add the id become " + cm1.getComId());

    List lst = commentManager.getCommentAll(pid);
    assertTrue(lst.size() > 0);

    EcatComment ec2 = (EcatComment) lst.get(0);
    log.debug("COMMDEBUG: " + ec2.getComId());
    List<EcatCommentItem> its = ec2.getItems();
    assertTrue(its.size() > 0);
    EcatCommentItem eitm = its.get(0);
    log.debug("COMMDEBUG: " + eitm.getItemContent());
  }
}
