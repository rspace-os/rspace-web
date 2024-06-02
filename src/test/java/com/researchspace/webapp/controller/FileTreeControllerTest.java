package com.researchspace.webapp.controller;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.views.TreeViewItem;
import com.researchspace.service.FolderManager;
import com.researchspace.service.UserManager;
import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.ui.ExtendedModelMap;

public class FileTreeControllerTest {

  public @Rule MockitoRule rule = MockitoJUnit.rule();
  @Mock UserManager userMgr;
  @Mock FolderManager folderMgr;
  Folder root1, otherRoot, gallery1, gallery2;
  User any, other;

  @InjectMocks FileTreeController fileTreeController;

  @Before
  public void setUp() throws Exception {
    any = TestFactory.createAnyUser("anyuser");
    root1 = TestFactory.createASystemFolder("Home", any);
    root1.addType(RecordType.ROOT);
    root1.setId(3L);
    other = TestFactory.createAnyUser("other");
    otherRoot = TestFactory.createASystemFolder("Home", other);
    otherRoot.addType(RecordType.ROOT);
    otherRoot.setId(4L);
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void nonGalleryAreDisplayed() throws UnsupportedEncodingException {
    Principal mockPrincipal = Mockito.mock(Principal.class);

    standardSetup(mockPrincipal);
    when(folderMgr.getFolderListingForTreeView(
            Mockito.eq(root1.getId()), Mockito.any(PaginationCriteria.class), Mockito.eq(any)))
        .thenReturn(noGallery());
    ExtendedModelMap results = new ExtendedModelMap();
    fileTreeController.listFilesInModel("/", false, results, mockPrincipal);
    assertEquals(1, getListFromModel(results, "records", TreeViewItem.class).size());
  }

  @Test
  public void galleryFolderAreHidden() throws UnsupportedEncodingException {
    Principal mockPrincipal = Mockito.mock(Principal.class);

    standardSetup(mockPrincipal);
    when(folderMgr.getFolderListingForTreeView(
            Mockito.eq(root1.getId()), Mockito.any(PaginationCriteria.class), Mockito.eq(any)))
        .thenReturn(galleryFolderPlusNormalItem());
    ExtendedModelMap results = new ExtendedModelMap();
    fileTreeController.listFilesInModel("/", false, results, mockPrincipal);
    // gallery folder is removed
    assertEquals(1, getListFromModel(results, "records", TreeViewItem.class).size());

    // check it's also removed for searching subfolders too -RSPAc-1494

    when(folderMgr.getFolder(Mockito.anyLong(), Mockito.eq(any))).thenReturn(otherRoot);
    when(folderMgr.getFolderListingForTreeView(
            Mockito.eq(otherRoot.getId()), Mockito.any(PaginationCriteria.class), Mockito.eq(any)))
        .thenReturn(galleryFolderPlusNormalItem());
    fileTreeController.listFilesInModel("-2", false, results, mockPrincipal);
    // gallery folder is removed as we
    assertEquals(1, getListFromModel(results, "records", TreeViewItem.class).size());
  }

  private void standardSetup(Principal mockPRincipal) {
    when(mockPRincipal.getName()).thenReturn(any.getUsername());
    when(userMgr.getUserByUsername(any.getUsername())).thenReturn(any);
    when(folderMgr.getRootFolderForUser(any)).thenReturn(root1);
  }

  @SuppressWarnings("unchecked")
  private <T> List<T> getListFromModel(ExtendedModelMap model, String property, Class<T> clazz) {
    return (List<T>) model.get(property);
  }

  ISearchResults<TreeViewItem> noGallery() {
    TreeViewItem res1 = normalDoc(1L);
    return new SearchResultsImpl<>(TransformerUtils.toList(res1), 0, 1L);
  }

  private ISearchResults galleryFolderPlusNormalItem() {
    TreeViewItem res1 = normalDoc(1L);
    TreeViewItem galleryFolder = galleryFolder(3L);
    return new SearchResultsImpl<>(TransformerUtils.toList(res1, galleryFolder), 0, 2L);
  }

  private TreeViewItem galleryFolder(long id) {
    return new TreeViewItem(
        id,
        Folder.MEDIAROOT,
        "ROOT_MEDIA:FOLDER",
        false,
        Instant.now().getEpochSecond(),
        Instant.now().getEpochSecond(),
        "");
  }

  private TreeViewItem normalDoc(Long id) {
    TreeViewItem res1 =
        new TreeViewItem(
            id,
            "anItem",
            "NORMAL",
            false,
            Instant.now().getEpochSecond(),
            Instant.now().getEpochSecond(),
            "");
    return res1;
  }
}
