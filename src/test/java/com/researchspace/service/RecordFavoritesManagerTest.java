package com.researchspace.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.core.util.TransformerUtils;
import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.BaseRecord.FavoritesStatus;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class RecordFavoritesManagerTest extends SpringTransactionalTest {

  private @Autowired RecordFavoritesManager recordFavoritesManager;
  private @Autowired RecordManager recordManager;
  private User user;

  @Before
  public void setUp() throws IllegalAddChildOperation {
    user = createAndSaveRandomUser();
    initialiseContentWithExampleContent(user);
  }

  @Test
  public void testAddRecordToFavorites() {
    StructuredDocument basicDocument =
        recordManager.createBasicDocument(user.getRootFolder().getId(), user);
    recordFavoritesManager.saveFavoriteRecord(basicDocument.getId(), user.getId());
    assertTrue(recordFavoritesManager.isFavoriteRecordBy(basicDocument.getId(), user.getId()));
  }

  @Test
  public void testDeleteRecordFromFavorites() {
    StructuredDocument basicDocument =
        recordManager.createBasicDocument(user.getRootFolder().getId(), user);
    recordFavoritesManager.saveFavoriteRecord(basicDocument.getId(), user.getId());
    assertTrue(recordFavoritesManager.isFavoriteRecordBy(basicDocument.getId(), user.getId()));
    recordFavoritesManager.deleteFavoriteRecord(basicDocument.getId(), user.getId());
    assertFalse(recordFavoritesManager.isFavoriteRecordBy(basicDocument.getId(), user.getId()));
  }

  @Test
  public void testAddFolderToFavorites() throws Exception {
    Folder folder = createFolder("anyfolder", user.getRootFolder(), user);
    recordFavoritesManager.saveFavoriteRecord(folder.getId(), user.getId());
    assertTrue(recordFavoritesManager.isFavoriteRecordBy(folder.getId(), user.getId()));
  }

  @Test
  public void testDeleteFolderFromFavorites() throws Exception {
    Folder folder = createFolder("anyfolder", user.getRootFolder(), user);
    recordFavoritesManager.saveFavoriteRecord(folder.getId(), user.getId());
    assertTrue(recordFavoritesManager.isFavoriteRecordBy(folder.getId(), user.getId()));
    recordFavoritesManager.deleteFavoriteRecord(folder.getId(), user.getId());
    assertFalse(recordFavoritesManager.isFavoriteRecordBy(folder.getId(), user.getId()));
  }

  @Test
  public void testAddNotebookToFavorites() throws Exception {
    Notebook notebook =
        createNotebookWithNEntries(user.getRootFolder().getId(), "anynotebook", 1, user);
    recordFavoritesManager.saveFavoriteRecord(notebook.getId(), user.getId());
    assertTrue(recordFavoritesManager.isFavoriteRecordBy(notebook.getId(), user.getId()));
  }

  @Test
  public void testDeleteNotebookFromFavorites() throws Exception {
    Notebook notebook =
        createNotebookWithNEntries(user.getRootFolder().getId(), "anynotebook", 1, user);
    recordFavoritesManager.saveFavoriteRecord(notebook.getId(), user.getId());
    assertTrue(recordFavoritesManager.isFavoriteRecordBy(notebook.getId(), user.getId()));
    assertFalse(FavoritesStatus.FAVORITE.equals(notebook.getFavoriteStatus()));
    recordFavoritesManager.updateTransientFavoriteStatus(TransformerUtils.toList(notebook), user);
    assertTrue((FavoritesStatus.FAVORITE.equals(notebook.getFavoriteStatus())));

    recordFavoritesManager.deleteFavoriteRecord(notebook.getId(), user.getId());
    assertFalse(recordFavoritesManager.isFavoriteRecordBy(notebook.getId(), user.getId()));
    recordFavoritesManager.updateTransientFavoriteStatus(TransformerUtils.toList(notebook), user);
    assertFalse(FavoritesStatus.FAVORITE.equals(notebook.getFavoriteStatus()));
  }

  @Test
  public void testGetFavoriteRecords() {
    StructuredDocument basicDocument1 =
        recordManager.createBasicDocument(user.getRootFolder().getId(), user);
    StructuredDocument basicDocument2 =
        recordManager.createBasicDocument(user.getRootFolder().getId(), user);
    recordFavoritesManager.saveFavoriteRecord(basicDocument1.getId(), user.getId());
    recordFavoritesManager.saveFavoriteRecord(basicDocument2.getId(), user.getId());
    assertTrue(recordFavoritesManager.getFavoriteRecordsByUser(user.getId()).size() > 1);
  }

  @Test
  public void testDeleteFavorites() throws Exception {
    Folder folder = createFolder("anyfolder", user.getRootFolder(), user);
    StructuredDocument basicDocument1 = recordManager.createBasicDocument(folder.getId(), user);
    StructuredDocument basicDocument2 = recordManager.createBasicDocument(folder.getId(), user);
    Folder subfolder = createFolder("anysubfolder", folder, user);
    recordFavoritesManager.saveFavoriteRecord(basicDocument1.getId(), user.getId());
    recordFavoritesManager.saveFavoriteRecord(basicDocument2.getId(), user.getId());
    recordFavoritesManager.saveFavoriteRecord(subfolder.getId(), user.getId());
    assertTrue(recordFavoritesManager.getFavoriteRecordsByUser(user.getId()).size() > 1);
    recordFavoritesManager.deleteFavorites(folder.getId(), user);
    assertEquals(0, recordFavoritesManager.getFavoriteRecordsByUser(user.getId()).size());
  }

  @Test
  public void testNotebookDeleteFavorites() throws Exception {
    Notebook notebook =
        createNotebookWithNEntries(user.getRootFolder().getId(), "anynotebook", 3, user);
    for (BaseRecord entry : notebook.getChildrens()) {
      recordFavoritesManager.saveFavoriteRecord(entry.getId(), user.getId());
    }
    assertTrue(recordFavoritesManager.getFavoriteRecordsByUser(user.getId()).size() > 1);
    recordFavoritesManager.deleteFavorites(notebook.getId(), user);
    assertEquals(0, recordFavoritesManager.getFavoriteRecordsByUser(user.getId()).size());
  }
}
