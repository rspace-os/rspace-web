package com.researchspace.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.MediaUtils;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.record.Folder;
import com.researchspace.model.views.TreeViewItem;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FolderDaoTest extends SpringTransactionalTest {
  User user;

  @Before
  public void setUp() throws Exception {
    user = createAndSaveUserIfNotExists("any");
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testGetLabGroupFolderForUser() {
    assertNull(folderDao.getLabGroupFolderForUser(user));
    Folder systemFolder =
        recordFactory.createSystemCreatedFolder(Folder.LAB_GROUPS_FOLDER_NAME, user);
    folderDao.save(systemFolder);
    assertEquals(systemFolder, folderDao.getLabGroupFolderForUser(user));
  }

  @Test
  public void testGetCollabLabGroupFolderForUser() {
    assertNull(folderDao.getCollaborationGroupsSharedFolderForUser(user));
    Folder systemFolder =
        recordFactory.createSystemCreatedFolder(Folder.COLLABORATION_GROUPS_FLDER_NAME, user);
    folderDao.save(systemFolder);
    assertEquals(systemFolder, folderDao.getCollaborationGroupsSharedFolderForUser(user));
  }

  @Test
  public void testGetProjectGroupFolderForUser() {
    Folder systemFolder =
        recordFactory.createSystemCreatedFolder(Folder.PROJECT_GROUPS_FOLDER_NAME, user);
    folderDao.save(systemFolder);
    assertEquals(systemFolder, folderDao.getProjectGroupsSharedFolderForUser(user));
  }

  @Test
  public void testGetIndividualSharedFolderForUser() {
    assertNull(folderDao.getLabGroupFolderForUser(user));
    Folder systemFolder =
        recordFactory.createSystemCreatedFolder(Folder.INDIVIDUAL_SHARE_ITEMS_FLDER_NAME, user);
    folderDao.save(systemFolder);
    assertEquals(systemFolder, folderDao.getIndividualSharedItemsFolderForUser(user));
  }

  @Test
  public void testGetIndividualSharedFolderForUsers() {
    User u2 = createAndSaveUserIfNotExists("another");
    Folder systemFolder = recordFactory.createIndividualSharedFolder(user, u2);
    assertTrue(systemFolder.hasType(RecordType.INDIVIDUAL_SHARED_FOLDER_ROOT));
    folderDao.save(systemFolder);
    assertNotNull(folderDao.getIndividualSharedFolderForUsers(u2, user, null));
    assertNotNull(folderDao.getIndividualSharedFolderForUsers(user, u2, null));
    folderDao.remove(systemFolder.getId());

    // and try when created by u2 as well
    Folder systemFolder2 = recordFactory.createIndividualSharedFolder(u2, user);
    folderDao.save(systemFolder2);
    assertNotNull(folderDao.getIndividualSharedFolderForUsers(u2, user, null));
    assertNotNull(folderDao.getIndividualSharedFolderForUsers(user, u2, null));
  }

  @Test
  public void testTemplateFolderForUser() {
    User any = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(any);
    Folder templateFolder = folderDao.getTemplateFolderForUser(any);
    assertTrue(templateFolder.isTemplateFolder());
  }

  @Test
  public void testTreeViewListing() {
    User anyUser = createAndSaveRandomUser();
    List<Folder> roots = initialiseContentWithEmptyContent(anyUser);
    PaginationCriteria<TreeViewItem> pgCrit =
        PaginationCriteria.createDefaultForClass(TreeViewItem.class);
    pgCrit.setGetAllResults();
    ISearchResults<TreeViewItem> items =
        folderDao.getFolderListingForTreeView(roots.get(0).getId(), pgCrit);
    assertTrue(items.getHits() > 0);
  }

  @Test
  public void getApiInboxFolder() {
    User anyUser = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(anyUser);
    Folder folder =
        folderDao.getApiFolderForContentType(MediaUtils.IMAGES_MEDIA_FLDER_NAME, anyUser).get();
    assertNotNull(folder);
    assertTrue(folder.isApiInboxFolder());
    assertTrue(folder.isImportedContentFolder());
  }

  @Test
  public void getImportFolder() {
    User any = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(any);
    Folder folder = folderDao.getImportFolder(any).get();
    assertNotNull(folder);
    assertTrue(folder.isImportsFolder());
    assertTrue(folder.isImportedContentFolder());
  }
}
