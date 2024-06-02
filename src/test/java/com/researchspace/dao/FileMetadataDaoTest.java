package com.researchspace.dao;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SortOrder;
import com.researchspace.model.FileProperty;
import com.researchspace.model.FileStoreRoot;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.Session;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class FileMetadataDaoTest extends SpringTransactionalTest {

  private @Autowired FileMetadataDao filedao;

  private User user = null;

  @Before
  public void setUp() throws Exception {
    /* we initialize a random user, so files added to 1st user on the server,
    e.g. default inventory templates, are not polluting subsequent users' stats */
    User user = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(user);
  }

  @Test
  public void testGetFileUsageForUser() {
    user = createAndSaveUserIfNotExists("fileUser");
    FileProperty fprops = createAndSaveFileProperty("12345", "any", user.getUsername());

    long usage = filedao.getTotalFileUsageForUser(user);
    assertTrue(usage == 12345L);
  }

  @Test
  public void testGetFileUsageForGroup() {
    // empty group handled gracefully
    assertEquals(0, filedao.getTotalFileUsageForGroup(new Group()).intValue());
    User pi = createAndSaveAPi();
    User inGrp = createAndSaveRandomUser();
    User other = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(pi, inGrp, other);
    Group grp = createGroup(getRandomAlphabeticString("grp_"), pi);
    grp = addUsersToGroup(pi, grp, inGrp);
    createAndSaveFileProperty("100", "any1", pi.getUsername());
    createAndSaveFileProperty("150", "any12", inGrp.getUsername());
    createAndSaveFileProperty("190", "any13", other.getUsername());
    assertEquals(100 + 150, filedao.getTotalFileUsageForGroup(grp).intValue());
    assertNotNull(filedao.getTotalFileUsageForGroups(Arrays.asList(new Group[] {grp})));
  }

  @Test
  public void fileUsageForGroupsHandlesEmptyList() {
    assertEquals(0, filedao.getTotalFileUsageForGroups(Collections.emptyList()).size());
  }

  private int getTotalFileUsage() {
    return filedao.getTotalFileUsage().intValue();
  }

  @Test
  public void testGetTotalFileUsage() {
    final int initialTotal = getTotalFileUsage();
    final int EXPECTED_ADDITIONAL_SIZE = 500;
    FileProperty fprops = createAndSaveFileProperty("200", "any1", "any");
    FileProperty fprops2 = createAndSaveFileProperty("300", "any2", "any");
    assertEquals(initialTotal + EXPECTED_ADDITIONAL_SIZE, getTotalFileUsage());
  }

  @Test
  public void testGetTotalFileUsageForGroups() {
    final int initialTotal = getTotalFileUsage();
    final int EXPECTED_ADDITIONAL_SIZE = 500;
    FileProperty fprops = createAndSaveFileProperty("200", "any1", "any");
    FileProperty fprops2 = createAndSaveFileProperty("300", "any2", "any");
    assertEquals(initialTotal + EXPECTED_ADDITIONAL_SIZE, getTotalFileUsage());
  }

  private FileProperty createAndSaveFileProperty(String size, String uri, String owner) {
    FileProperty fprops = new FileProperty();
    fprops.setFileSize(size);
    // fprops.setFileUri(uri);
    fprops.setRelPath(uri);
    fprops.setFileOwner(owner);
    filedao.save(fprops);
    return fprops;
  }

  @Test
  public void testGetFileUsageForAllUser() {
    String uname1 = getRandomName(10);
    String uname2 = getRandomName(10);
    User u1LessUsage = createAndSaveUserIfNotExists(uname1);
    User u2MoreUsage = createAndSaveUserIfNotExists(uname2);

    FileProperty fprops = setupFileProperty(u1LessUsage, 1000);
    filedao.save(fprops);
    FileProperty fprops2 = setupFileProperty(u2MoreUsage, 2000);
    filedao.save(fprops2);

    PaginationCriteria pgCrit = PaginationCriteria.createDefaultForClass(User.class);
    Map<String, DatabaseUsageByUserGroupByResult> usageForUsers =
        filedao.getTotalFileUsageForAllUsers(pgCrit);
    assertTrue(usageForUsers.size() >= 3);

    List<User> users2 = List.of(u1LessUsage, u2MoreUsage);
    Map<String, DatabaseUsageByUserGroupByResult> usageForUsers2 =
        filedao.getTotalFileUsageForUsers(users2, pgCrit);
    assertEquals(2, usageForUsers2.size());

    // default ordering is desc
    assertEquals(u2MoreUsage.getUsername(), usageForUsers2.keySet().iterator().next());
    final long expectedSum = 3000; // 1000 + 2000
    long actualsum = 0L;
    for (DatabaseUsageByUserGroupByResult row : usageForUsers2.values()) {
      actualsum += row.getUsage();
    }
    assertEquals(expectedSum, actualsum);

    // sort asc
    pgCrit.setSortOrder(SortOrder.ASC);
    usageForUsers2 = filedao.getTotalFileUsageForUsers(users2, pgCrit);
    assertEquals(u1LessUsage.getUsername(), usageForUsers2.keySet().iterator().next());

    // now test count
    final long currentCount = filedao.getCountOfUsersWithFilesInFileSystem().longValue();
    // now add new user - the same,  since new users has not had files initialized
    String uname3 = getRandomName(10);
    User u3 = createAndSaveUserIfNotExists(uname3);
    assertEquals(currentCount, filedao.getCountOfUsersWithFilesInFileSystem().longValue());
  }

  @Test
  public void getUsageByGroup() {
    PaginationCriteria<Group> pgCrit = PaginationCriteria.createDefaultForClass(Group.class);
    ISearchResults<DatabaseUsageByGroupGroupByResult> results =
        filedao.getTotalFileUsageForLabGroups(pgCrit);
    assertNotNull(results);
  }

  @Test
  public void filePropertyRoot() {
    String uname1 = getRandomName(10);
    User u1 = createAndSaveUserIfNotExists(uname1);

    FileProperty fprops = setupFileProperty(u1, 100);
    FileStoreRoot root = new FileStoreRoot("file://some/uri/path/to/file_store/");
    root.setCurrent(true);
    root = filedao.saveFileStoreRoot(root);
    fprops.setRoot(root);
    filedao.save(fprops);
    assertEquals(root, getSession().get(FileStoreRoot.class, root.getId()));
    Map<String, String> props = new HashMap<>();
    props.put("fileSize", 100 + "");
    FileProperty loaded = filedao.findProperties(props).get(0);
    assertEquals(root, loaded.getRoot());

    assertNull(filedao.findByFileStorePath("xyz"));
    assertEquals(root, filedao.findByFileStorePath("some/uri/path/"));
    assertEquals(root, filedao.findByFileStorePath("file://some/uri/path/to/"));
    assertTrue(root.isCurrent());
    filedao.resetCurrentFileStoreRoot(false);
    sessionFactory.getCurrentSession().evict(root);
    root = filedao.findByFileStorePath("file://some/uri/path/to/");
    assertFalse(root.isCurrent());

    // needs file prefix to be retrieved
    root.setFileStoreRoot("no/file/prefix/file_store/");
    root = filedao.saveFileStoreRoot(root);
    assertEquals(null, filedao.findByFileStorePath("no/file/prefix"));
  }

  private FileProperty setupFileProperty(User u1, int fileSize) {
    FileProperty fprops = new FileProperty();
    fprops.setFileSize(fileSize + "");
    // fprops.setFileUri("aURI");
    fprops.setRelPath("relpath");
    fprops.setFileOwner(u1.getUsername());
    return fprops;
  }

  private Session getSession() {
    return sessionFactory.getCurrentSession();
  }

  @Test
  public void collectFilestoreResourcesBelongingToUser() throws FileNotFoundException, Exception {

    User user = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(user);

    List<File> paths = filedao.collectUserFilestoreResources(user);
    assertEquals(0, paths.size());

    StructuredDocument docD1 = createBasicDocumentInRootFolderWithText(user, "test");
    addAudioFileToField(docD1.getFields().get(0), user);

    paths = filedao.collectUserFilestoreResources(user);
    assertEquals(1, paths.size());
    File audioFile = paths.get(0);
    assertTrue(
        "expect audio filename but was " + audioFile.getName(),
        audioFile.getName().contains("mpthreetest"));
    assertTrue("expected file to exist", audioFile.exists());
    assertEquals(198658, audioFile.length());
  }
}
