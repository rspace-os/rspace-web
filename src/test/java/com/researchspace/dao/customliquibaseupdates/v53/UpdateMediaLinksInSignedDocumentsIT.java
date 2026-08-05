package com.researchspace.dao.customliquibaseupdates.v53;

import static org.junit.Assert.assertEquals;

import com.researchspace.dao.customliquibaseupdates.AbstractDBHelpers;
import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedRecord;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.AuditManager;
import com.researchspace.service.RecordManager;
import com.researchspace.service.RecordSigningManager;
import java.io.IOException;
import java.util.List;
import liquibase.exception.CustomChangeException;
import liquibase.exception.SetupException;
import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class UpdateMediaLinksInSignedDocumentsIT extends AbstractDBHelpers {

  private @Autowired RecordManager recordManager;
  private @Autowired RecordSigningManager signingManager;
  private @Autowired AuditManager auditManager;

  private UpdateMediaLinksInSignedDocument updater;
  private User user;

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void checkOnlySignedDocumentsWithMediaAttachmentsAreUpdated()
      throws IOException, SetupException, CustomChangeException {
    user = createInitAndLoginAnyUser();

    // create four records
    StructuredDocument doc1 = recordManager.createBasicDocument(user.getRootFolder().getId(), user);
    StructuredDocument doc2 = recordManager.createBasicDocument(user.getRootFolder().getId(), user);
    StructuredDocument doc3 = recordManager.createBasicDocument(user.getRootFolder().getId(), user);
    StructuredDocument doc4 = recordManager.createBasicDocument(user.getRootFolder().getId(), user);

    // add gallery links to first, third and fourth record
    addImageToField(doc1.getFields().get(0), user);
    addImageToField(doc3.getFields().get(0), user);
    addImageToField(doc4.getFields().get(0), user);

    // mark the first and second record as signed
    doc1.setSigned(true);
    recordManager.save(doc1, user);
    doc2.setSigned(true);
    recordManager.save(doc2, user);
    // sign the fourth record through manager method - this should update links in field content
    doc4.setSigned(true);
    signingManager.signRecord(doc4.getId(), user, null, "signing doc4");

    // save the revision history of all four records
    List<AuditedRecord> doc1HistoryBefore = auditManager.getHistory(doc1, null);
    List<AuditedRecord> doc2HistoryBefore = auditManager.getHistory(doc2, null);
    List<AuditedRecord> doc3HistoryBefore = auditManager.getHistory(doc3, null);
    List<AuditedRecord> doc4HistoryBefore = auditManager.getHistory(doc4, null);

    // run liquibase update
    updater = new UpdateMediaLinksInSignedDocument();
    updater.setUp();
    updater.execute(null);

    // check revision history after update
    List<AuditedRecord> doc1HistoryAfter = auditManager.getHistory(doc1, null);
    List<AuditedRecord> doc2HistoryAfter = auditManager.getHistory(doc2, null);
    List<AuditedRecord> doc3HistoryAfter = auditManager.getHistory(doc3, null);
    List<AuditedRecord> doc4HistoryAfter = auditManager.getHistory(doc4, null);

    // ensure that only first record has a new revision, and the revision date is from time of
    // signing
    assertEquals(doc1HistoryBefore.size() + 1, doc1HistoryAfter.size());
    BaseRecord currentDoc1RevisionBefore =
        doc1HistoryBefore.get(doc1HistoryBefore.size() - 1).getEntity();
    BaseRecord currentDoc1RevisionAfter =
        doc1HistoryAfter.get(doc1HistoryAfter.size() - 1).getEntity();
    assertEquals(
        currentDoc1RevisionBefore.getModificationDate(),
        currentDoc1RevisionAfter.getModificationDate());

    assertEquals(doc2HistoryBefore.size(), doc2HistoryAfter.size());
    assertEquals(doc3HistoryBefore.size(), doc3HistoryAfter.size());
    assertEquals(doc4HistoryBefore.size(), doc4HistoryAfter.size());

    // run liquibase update again, that shouldn't create any new revisions
    updater = new UpdateMediaLinksInSignedDocument();
    updater.setUp();
    updater.execute(null);

    // check revision history after update
    List<AuditedRecord> doc1HistoryAfterSecondRun = auditManager.getHistory(doc1, null);
    List<AuditedRecord> doc2HistoryAfterSecondRun = auditManager.getHistory(doc2, null);
    List<AuditedRecord> doc3HistoryAfterSecondRun = auditManager.getHistory(doc3, null);
    List<AuditedRecord> doc4HistoryAfterSecondRun = auditManager.getHistory(doc4, null);
    assertEquals(doc1HistoryAfter.size(), doc1HistoryAfterSecondRun.size());
    assertEquals(doc2HistoryAfter.size(), doc2HistoryAfterSecondRun.size());
    assertEquals(doc3HistoryAfter.size(), doc3HistoryAfterSecondRun.size());
    assertEquals(doc4HistoryAfter.size(), doc4HistoryAfterSecondRun.size());
  }
}
