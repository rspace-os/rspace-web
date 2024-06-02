package com.researchspace.service.impl;

import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.CommunicationManager;
import com.researchspace.service.NotificationConfig;
import com.researchspace.service.impl.RecordDeletionManagerImpl.DeletionContext;
import java.util.Collections;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class RecordDeletionManagerImplTest {
  static class RecordDeletionManagerImplTSS extends RecordDeletionManagerImpl {

    Set<User> getUsersToNotifyOfDelete(User deleting, BaseRecord toDelete) {
      return Collections.emptySet();
    }
  }

  public @Rule MockitoRule rule = MockitoJUnit.rule();
  @Mock CommunicationManager commMgr;
  private User user;
  @InjectMocks RecordDeletionManagerImplTSS tss;

  @Before
  public void setUp() throws Exception {
    user = TestFactory.createAnyUser("any");
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testNotificationNotSentForTempDoc_RSPAC1446() {
    StructuredDocument doc = anyDoc();
    doc.setTemporaryDoc(true);
    tss.doNotification(user, DeletionContext.DOCUMENT, doc);
    Mockito.verify(commMgr, Mockito.never())
        .notify(
            Mockito.eq(user),
            Mockito.eq(doc),
            Mockito.any(NotificationConfig.class),
            Mockito.anyString());
  }

  @Test
  public void testNotificationSentForDoc_RSPAC1446() {
    StructuredDocument doc = anyDoc();
    doc.setId(1L);
    tss.doNotification(user, DeletionContext.DOCUMENT, doc);
    Mockito.verify(commMgr)
        .notify(
            Mockito.eq(user),
            Mockito.eq(doc),
            Mockito.any(NotificationConfig.class),
            Mockito.anyString());
  }

  private StructuredDocument anyDoc() {
    StructuredDocument doc = TestFactory.createAnySD();
    doc.setOwner(user);
    return doc;
  }
}
