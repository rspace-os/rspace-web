package com.researchspace.webapp.controller;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.Constants;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.views.TreeViewItem;
import java.security.Principal;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.springframework.web.servlet.ModelAndView;

public class FileTreeControllerMVCIT extends MVCTestBase {

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @Test
  public void getFileList() throws Exception {
    StructuredDocument sd = setUpLoginAsPIUserAndCreateADocument();
    ModelAndView m =
        this.mockMvc
            .perform(post("/fileTree/ajax/filesInModel").param("dir", "/").principal(mockPrincipal))
            .andExpect(status().isOk())
            .andReturn()
            .getModelAndView();
    assertTrue(m.getModelMap().containsKey("dir"));
    assertTrue(m.getModelMap().containsKey("records"));
    List<TreeViewItem> records = getListFromModel(m, "records", TreeViewItem.class);
    assertGalleryFolderIncluded(records, true);
    assertDocumentIncluded(records, getRootFolderForUser(piUser), toTreeViewItem(sd), true, true);
    recordDeletionMgr.deleteRecord(getRootFolderForUser(piUser).getId(), sd.getId(), piUser);
    m = doPostToListRoot("/", mockPrincipal);
    // now remove doc

    records = getListFromModel(m, "records", TreeViewItem.class);
    assertGalleryFolderIncluded(records, false);
    assertDocumentIncluded(records, getRootFolderForUser(piUser), toTreeViewItem(sd), false, false);
  }

  private ModelAndView doPostToListRoot(String dirValue, Principal subject) throws Exception {
    ModelAndView m;
    m =
        this.mockMvc
            .perform(
                post("/fileTree/ajax/filesInModel")
                    .param("dir", dirValue)
                    .param("showGallery", "false")
                    .principal(subject))
            .andExpect(status().isOk())
            .andReturn()
            .getModelAndView();
    return m;
  }

  private void assertDocumentIncluded(
      List<TreeViewItem> records,
      Folder parent,
      TreeViewItem toFind,
      boolean isShown,
      boolean isIncluded) {

    TreeViewItem inFolder = null;
    for (TreeViewItem child : records) {
      if (child.getId().equals(toFind.getId())) {
        inFolder = child;
      }
    }
    if (isIncluded) {
      assertTrue(inFolder != null);
    } else {
      assertTrue(inFolder == null);
      return;
    }
    if (isShown) {
      assertTrue(inFolder != null && !inFolder.isDeleted());
    } else {
      assertTrue(inFolder != null && (inFolder.isDeleted()));
    }
  }

  // RSPAC-773
  @Test
  @SuppressWarnings("unchecked")
  public void getFileListDiscriminatesBetweenSameNamedFolders() throws Exception {
    User any = createInitAndLoginAnyUser();
    Folder root = getRootFolderForUser(any);

    Folder f1 = createSubFolder(root, "folder", any);
    Folder f2 = createSubFolder(root, "folder", any);
    Folder f3 = createSubFolder(root, "folder", any);
    StructuredDocument d1 = createBasicDocumentInFolder(any, f2, "any");

    // Document exists in f2
    ModelAndView m = doPostToListRoot(f2.getId() + "/", new MockPrincipal(any.getUsername()));
    List<TreeViewItem> records = (List<TreeViewItem>) m.getModelMap().get("records");
    assertDocumentIncluded(records, f2, toTreeViewItem(d1), true, true);

    // Document does not exist in f1 or f3
    m = doPostToListRoot(f1.getId() + "/", new MockPrincipal(any.getUsername()));
    records = (List<TreeViewItem>) m.getModelMap().get("records");
    assertDocumentIncluded(records, f1, toTreeViewItem(d1), false, false);

    m = doPostToListRoot(f3.getId() + "/", new MockPrincipal(any.getUsername()));
    records = (List<TreeViewItem>) m.getModelMap().get("records");
    assertDocumentIncluded(records, f3, toTreeViewItem(d1), false, false);
  }

  private TreeViewItem toTreeViewItem(StructuredDocument d1) {
    // we're only needing the ID here.
    return new TreeViewItem(d1.getId(), null, null, false, null, null, null);
  }

  private void assertGalleryFolderIncluded(List<TreeViewItem> records, boolean isShown) {
    boolean galleryFound = false;
    for (TreeViewItem child : records) {
      if (child.isRootMedia()) {
        galleryFound = true;
      }
    }
    if (isShown) {
      assertTrue(galleryFound);
    } else {
      assertFalse(galleryFound);
    }
  }

  @Test
  public void getDirListForMoveTargetTreeList() throws Exception {

    User user = createAndSaveUser(getRandomName(10));
    Principal mockPrincipal = new MockPrincipal(user);
    logoutAndLoginAs(user);
    Folder userRoot = initUser(user, true); // initialise content

    // check returns listing for root folder
    ModelAndView m =
        this.mockMvc
            .perform(
                post("/fileTree/ajax/directoriesInModel")
                    .param("dir", "/")
                    .param("initialLoad", "true")
                    .principal(mockPrincipal))
            .andExpect(status().isOk())
            .andReturn()
            .getModelAndView();
    assertTrue(m.getModelMap().containsKey("results"));
    List<BaseRecord> res = getListFromModel(m, "results", BaseRecord.class);
    assertEquals(1, res.size());
    assertEquals(userRoot.getId(), res.get(0).getId());
    assertTrue(((Folder) res.get(0)).isRootFolder());

    // check listing by folder id
    ModelAndView m2 =
        this.mockMvc
            .perform(
                post("/fileTree/ajax/directoriesInModel")
                    .param("dir", userRoot.getId() + "")
                    .principal(mockPrincipal))
            .andExpect(status().isOk())
            .andReturn()
            .getModelAndView();
    assertTrue(m2.getModelMap().containsKey("results"));
    List<BaseRecord> res2 = getListFromModel(m2, "results", BaseRecord.class);
    assertEquals(2, res2.size());

    // check listing by folder id, with showNotebooks option
    ModelAndView m3 =
        this.mockMvc
            .perform(
                post("/fileTree/ajax/directoriesInModel")
                    .param("dir", userRoot.getId() + "")
                    .param("showNotebooks", "true")
                    .principal(mockPrincipal))
            .andExpect(status().isOk())
            .andReturn()
            .getModelAndView();
    assertTrue(m3.getModelMap().containsKey("results"));
    List<BaseRecord> res3 = getListFromModel(m3, "results", BaseRecord.class);
    assertEquals(4, res3.size());
  }

  @Test
  public void getDirsAndNotebooksForShareDlg() throws Exception {

    User pi = createAndSaveUser(getRandomName(10), Constants.PI_ROLE);
    Long piRootId = initUser(pi).getId();
    logoutAndLoginAs(pi);

    User user = createAndSaveUser(getRandomName(10));
    initUser(user);

    Group group = createGroupForUsersWithDefaultPi(pi, user);
    group = grpMgr.getGroup(group.getId());
    Long groupSharedFolderId = group.getCommunalGroupFolderId();

    // sharing one notebook for read, one for write
    Notebook sharedForRead = createNotebookWithNEntries(piRootId, "shared for read", 0, pi);
    shareNotebookWithGroup(pi, sharedForRead, group, "read");
    Notebook sharedForEdit = createNotebookWithNEntries(piRootId, "shared for edit", 0, pi);
    shareNotebookWithGroup(pi, sharedForEdit, group, "write");

    // own notebooks doesn't show on share dialog, so Pi user doesn't have anything listed
    ModelAndView viewPi =
        this.mockMvc
            .perform(
                post("/fileTree/ajax/directoriesInModel")
                    .param("dir", group.getCommunalGroupFolderId() + "")
                    .param("showNotebooksForShare", "true")
                    .principal(new MockPrincipal(pi)))
            .andExpect(status().isOk())
            .andReturn()
            .getModelAndView();
    assertTrue(viewPi.getModelMap().containsKey("results"));
    List<BaseRecord> resPi = getListFromModel(viewPi, "results", BaseRecord.class);
    assertEquals(0, resPi.size());

    logoutAndLoginAs(user);
    Folder folder = folderMgr.getInitialisedFolder(groupSharedFolderId, user, null);
    assertEquals(2, folder.getChildrens().size());

    // only notebook shared for write should be returned when other user lists directories
    ModelAndView viewUser =
        this.mockMvc
            .perform(
                post("/fileTree/ajax/directoriesInModel")
                    .param("dir", group.getCommunalGroupFolderId() + "")
                    .param("showNotebooksForShare", "true")
                    .principal(new MockPrincipal(user)))
            .andExpect(status().isOk())
            .andReturn()
            .getModelAndView();
    assertTrue(viewUser.getModelMap().containsKey("results"));
    List<BaseRecord> resUser = getListFromModel(viewUser, "results", BaseRecord.class);
    assertEquals(1, resUser.size());
    assertEquals(sharedForEdit.getId(), resUser.get(0).getId());
  }

  @SuppressWarnings("unchecked")
  private <T> List<T> getListFromModel(ModelAndView model, String property, Class<T> clazz) {
    return (List<T>) model.getModelMap().get(property);
  }
}
