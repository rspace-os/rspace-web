package com.researchspace.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectRetrievalFailureException;

/**
 * This test case uses real objects and a rollbacked transaction - it is an acceptance test for the
 * service layer but doesn't actually commit to the DB. A test executes within a single hibernate
 * session.
 */
public class BaseRecordManagerTest extends SpringTransactionalTest {

  private @Autowired BaseRecordManager baseRecordManager;
  private @Autowired RecordManager recordManager;
  private @Autowired RecordDeletionManager deletionMgr;

  private User user;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    user = createAndSaveUserIfNotExists(getRandomAlphabeticString("any"));
    initialiseContentWithExampleContent(user);
  }

  @Test
  public void testSavingBaseRecordProperty() {
    StructuredDocument basicDocument =
        recordManager.createBasicDocument(user.getRootFolder().getId(), user);
    assertNotNull(basicDocument);
    assertNull(basicDocument.getDescription());

    String TEST_NEW_DESCRIPTION = "new description";
    basicDocument.setDescription(TEST_NEW_DESCRIPTION);

    baseRecordManager.save(basicDocument, user);

    StructuredDocument retrievedDocument =
        (StructuredDocument) recordManager.get(basicDocument.getId());
    assertNotNull(retrievedDocument);
    assertEquals(
        "BaseRecord property should be updated",
        TEST_NEW_DESCRIPTION,
        retrievedDocument.getDescription());
  }

  @Test
  public void testGettingBaseRecord() throws Exception {
    StructuredDocument createdDocument =
        recordManager.createBasicDocument(user.getRootFolder().getId(), user);
    assertNotNull(createdDocument);

    BaseRecord retrievedRecord = baseRecordManager.get(createdDocument.getId(), user);
    assertNotNull(retrievedRecord);
    assertEquals("base record get should return created record", createdDocument, retrievedRecord);

    BaseRecord retrievedFolder = baseRecordManager.get(user.getRootFolder().getId(), user);
    assertNotNull(retrievedFolder);

    // getting unexisting record id throws exception
    assertExceptionThrown(
        () -> baseRecordManager.get(createdDocument.getId() + 1, user),
        ObjectRetrievalFailureException.class);
  }

  @Test
  public void includeOrExcludeDeletedFolder() throws Exception {
    Folder toDelete = createFolder("toDelete", folderDao.getRootRecordForUser(user), user);
    assertEquals(toDelete, baseRecordManager.get(toDelete.getId(), user));

    deletionMgr.deleteFolder(folderDao.getRootRecordForUser(user).getId(), toDelete.getId(), user);
    assertAuthorisationExceptionThrown(() -> baseRecordManager.get(toDelete.getId(), user));
    // exception not thrown if we include deleted folder.
    assertEquals(toDelete, baseRecordManager.get(toDelete.getId(), user, true));
  }
}
