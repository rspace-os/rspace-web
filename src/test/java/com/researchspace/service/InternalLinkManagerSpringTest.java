package com.researchspace.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.researchspace.dao.InternalLinkDao;
import com.researchspace.model.InternalLink;
import com.researchspace.model.User;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class InternalLinkManagerSpringTest extends SpringTransactionalTest {

  private @Autowired InternalLinkManager internalLinkMgr;
  private @Autowired InternalLinkDao internalLinkDao;

  @Test
  public void saveRetrieveInternalLinks()
      throws AuthorizationException, DocumentAlreadyEditedException {

    User user = createAndSaveUserIfNotExists(getRandomAlphabeticString("internalLinkUsr"));
    initialiseContentWithEmptyContent(user);

    StructuredDocument sourceDoc = createBasicDocumentInRootFolderWithText(user, "content1");
    StructuredDocument targetDoc = createBasicDocumentInRootFolderWithText(user, "content2");

    List<InternalLink> noLinks = internalLinkMgr.getLinksPointingToRecord(targetDoc.getId());
    assertTrue(noLinks.isEmpty());

    internalLinkDao.saveInternalLink(sourceDoc.getId(), targetDoc.getId());

    List<InternalLink> savedLink = internalLinkMgr.getLinksPointingToRecord(targetDoc.getId());
    assertEquals(1, savedLink.size());
    assertEquals(sourceDoc.getId(), savedLink.get(0).getSource().getId());
    assertEquals(targetDoc.getId(), savedLink.get(0).getTarget().getId());

    recordDeletionMgr.deleteRecord(sourceDoc.getParent().getId(), sourceDoc.getId(), user);
    List<InternalLink> deletedLink = internalLinkMgr.getLinksPointingToRecord(targetDoc.getId());
    assertTrue(deletedLink.isEmpty());
  }
}
