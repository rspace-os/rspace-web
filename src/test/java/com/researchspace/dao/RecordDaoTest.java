package com.researchspace.dao;

// import org.compass.core.CompassTemplate;
// import org.compass.gps.CompassGps;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.core.util.SortOrder;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.FormState;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.RecordFactory;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.views.RecordTypeFilter;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectRetrievalFailureException;

@SuppressWarnings("deprecation")
public class RecordDaoTest extends BaseDaoTestCase {
  static RecordFactory factoryAPI;
  static StructuredDocument d1, d2, d3;
  static Folder f1, f2;
  static StructuredDocument sd = TestFactory.createAnySD(TestFactory.createAnyForm());
  static User anyuser = new User("any");

  @Autowired private RecordDao dao;
  @Autowired private FolderDao folderDao;
  @Autowired private UserDao userDao;
  @Autowired private FormDao formDao;

  @Test(expected = ObjectRetrievalFailureException.class)
  public void testGetRecordInvalid() throws Exception {
    // should throw DataAccessException
    dao.get(-1000L);
  }

  public void testIsRecord() throws Exception {
    assertFalse(dao.isRecord(-1000L));
    User u = createAndSaveRandomUser();
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(u, "any");
    assertTrue(dao.isRecord(doc.getId()));
    assertFalse(dao.isRecord(folderDao.getRootRecordForUser(u).getId()));
  }

  @Test
  public void testGetAllRecords() {
    System.err.println(dao.getAll().size());
  }

  @Test
  public void getMediaLinkedRecords() {
    assertEquals(0, dao.getInfosOfDocumentsLinkedToMediaFile(1L).size());
  }

  @Test
  public void testCountAllRecords() throws InterruptedException {
    // create 2 users with different record count.
    User u1 = createAndSaveUserIfNotExists(getRandomAlphabeticString("u1"));
    User u2 = createAndSaveUserIfNotExists(getRandomAlphabeticString("u2"));
    initialiseContentWithEmptyContent(u1, u2);
    logoutAndLoginAs(u1);
    createNotebookWithNEntries(u1.getRootFolder().getId(), "u1Notebook", 1, u1);
    logoutAndLoginAs(u2);
    createNotebookWithNEntries(u2.getRootFolder().getId(), "u1Notebook", 2, u2);

    PaginationCriteria<User> pgCrit = PaginationCriteria.createDefaultForClass(User.class);
    pgCrit.setSortOrder(SortOrder.ASC);
    Map<String, DatabaseUsageByUserGroupByResult> res =
        dao.getTotalRecordsForUsers(List.of(u1, u2), pgCrit);
    assertEquals(2, res.size());
    final long U1Count = res.get(u1.getUsername()).getUsage().longValue();
    assertEquals(U1Count + 1, res.get(u2.getUsername()).getUsage().longValue());

    assertEquals(u1.getUsername(), res.keySet().iterator().next());

    pgCrit.setSortOrder(SortOrder.DESC);
    res = dao.getTotalRecordsForUsers(List.of(u1, u2), pgCrit);
    assertEquals(u2.getUsername(), res.keySet().iterator().next());
  }

  @Test
  public void testGetNotebookContentsExcludeFolders() throws Exception {
    // create the test data
    Folder root = createTestData();

    // test the results of retrieveChildRecordIdsExcludeFolders
    List<Long> results = dao.getNotebookContentsExcludeFolders(root.getId());
    assertEquals(1, results.size());

    results = dao.getNotebookContentsExcludeFolders(f1.getId());
    assertEquals(1, results.size());

    results = dao.getNotebookContentsExcludeFolders(f2.getId());
    assertEquals(1, results.size());
  }

  @Test
  public void testGetRecordCountByUser() throws Exception {
    PaginationCriteria<User> pgCrit = PaginationCriteria.createDefaultForClass(User.class);
    Map<String, DatabaseUsageByUserGroupByResult> res = dao.getTotalRecordsForUsers(pgCrit);
    int INITIAL_COUNT = res.size();

    createTestData(); // add user and data
    Map<String, DatabaseUsageByUserGroupByResult> res2 = dao.getTotalRecordsForUsers(pgCrit);
    assertEquals(INITIAL_COUNT + 1, res2.size());
  }

  @Test
  public void getAllDocumentsInNotebookForUser() throws InterruptedException {
    User u = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(u);
    Notebook nb = createNotebookWithNEntries(u.getRootFolder().getId(), "nb", 4, u);
    StructuredDocument sd = createBasicDocumentInRootFolderWithText(u, "any");
    List<Long> docIdsInNotebooks = dao.getAllDocumentIdsInNotebooksForUser(u);
    assertFalse(docIdsInNotebooks.contains(sd.getId()));
    assertEquals(4, docIdsInNotebooks.size());
    assertEquals(
        1,
        dao.getRecordCountForUser(new RecordTypeFilter(EnumSet.of(RecordType.NOTEBOOK), true), u)
            .intValue());
  }

  /**
   * Makes a 3-deep nested folder structure a structured document at each level t1 /t2/t3/rtd
   *
   * @throws Exception
   */
  private Folder createTestData() throws Exception {

    factoryAPI = new RecordFactory();

    // create fake user
    String uname = "anyuser";
    anyuser = new User(uname);

    anyuser.setAccountExpired(false);
    anyuser.setFirstName("first");
    anyuser.setLastName("last");
    anyuser.setPassword("any");
    anyuser.setEmail(uname + "@b");
    anyuser.setAccountLocked(false);

    userDao.save(anyuser);

    Thread.sleep(2);

    // create 3 levels of folder adding a document at each level

    Folder root = factoryAPI.createRootFolder("root", anyuser);
    folderDao.save(root);
    Thread.sleep(2);

    f1 = TestFactory.createAFolder("level1", anyuser);
    addChild(root, f1, anyuser);
    Thread.sleep(2);

    f2 = TestFactory.createAFolder("level2", anyuser);
    addChild(f1, f2, anyuser);
    Thread.sleep(2);

    // create fake form and structured record instances
    RSForm t = TestFactory.createAnyForm("test form");
    t.setOwner(anyuser);
    t.setPublishingState(FormState.PUBLISHED);
    formDao.save(t);
    Thread.sleep(2);

    d1 = factoryAPI.createStructuredDocument(StructuredDocument.DEFAULT_NAME, anyuser, t);
    addChild(root, d1, anyuser);
    Thread.sleep(2);

    d2 = factoryAPI.createStructuredDocument(StructuredDocument.DEFAULT_NAME, anyuser, t);
    addChild(f1, d2, anyuser);
    Thread.sleep(2);

    d3 = factoryAPI.createStructuredDocument(StructuredDocument.DEFAULT_NAME, anyuser, t);
    addChild(f2, d3, anyuser);
    Thread.sleep(2);

    return root;
  }

  /**
   * This is duplicated from FolderMger to avoid a cyclic dependency between foldermgr and recordMgr
   * which buggers up the spring wiring. Handles all persistence of a adding a newly created object
   * to a persisted parent
   *
   * @throws IllegalAddChildOperation
   */
  public Folder addChild(Folder f, BaseRecord newTransientChild, User owner)
      throws IllegalAddChildOperation {

    saveChild(newTransientChild);
    f.addChild(newTransientChild, owner);
    folderDao.save(f);
    saveChild(newTransientChild);
    return f;
  }

  private void saveChild(BaseRecord newTransientChild) {
    if (newTransientChild.isFolder()) {
      folderDao.save((Folder) newTransientChild);
    } else {
      dao.save((Record) newTransientChild);
    }
  }
}
