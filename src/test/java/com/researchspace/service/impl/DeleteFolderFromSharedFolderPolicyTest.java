package com.researchspace.service.impl;

import static com.researchspace.testutils.TestFactory.createAFolder;
import static com.researchspace.testutils.TestFactory.createANotebookWithNEntries;
import static com.researchspace.testutils.TestFactory.createAnyUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.dao.FolderDao;
import com.researchspace.model.User;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Notebook;
import com.researchspace.service.DeletionPlan;
import com.researchspace.testutils.TestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DeleteFolderFromSharedFolderPolicyTest {
  @InjectMocks DeleteFolderFromSharedFolderPolicy deletionOrderPolicy;

  @Mock FolderDao folderDao;

  User anyUser = null;

  Folder grpSharedFolder, topLevelSharedFolder;

  @BeforeEach
  public void setUp() throws Exception {
    anyUser = createAnyUser("any");
    grpSharedFolder = createGroupSharedFolder();
  }

  private Folder createGroupSharedFolder() {
    topLevelSharedFolder = createAFolder("Shared", anyUser);
    Folder groups = createAFolder("Groups", anyUser);
    Folder groupshare = createAFolder("GroupShare", anyUser);
    topLevelSharedFolder.addChild(groups, anyUser);
    groups.addChild(groupshare, anyUser);
    return groupshare;
  }

  @Test
  public void testCalculateDeletionOrderForSingleItem() {
    Mockito.when(folderDao.getUserSharedFolder(anyUser)).thenReturn(topLevelSharedFolder);
    Folder toDelete = createAFolder("ToDelete", anyUser);
    grpSharedFolder.addChild(toDelete, anyUser);
    DeletionPlan plan =
        deletionOrderPolicy.calculateDeletionOrder(toDelete, toDelete.getParent(), anyUser);
    assertEquals(1, plan.size());
    assertEquals(toDelete, plan.getFinalElementToRemove());
    assertEquals(toDelete, plan.iterator().next());
  }

  @Test
  public void rejectFolderToDeleteThatIsNotInSharedFolderTree() {
    Mockito.when(folderDao.getUserSharedFolder(anyUser))
        .thenReturn(TestFactory.createAFolder("notInTree", anyUser));
    Folder toDelete = createAFolder("ToDelete", anyUser);
    grpSharedFolder.addChild(toDelete, anyUser);
    assertThrows(
        IllegalArgumentException.class,
        () -> deletionOrderPolicy.calculateDeletionOrder(toDelete, toDelete.getParent(), anyUser));
  }

  @Test
  public void testNotebookEntriesArentAddedToPlan() {
    Mockito.when(folderDao.getUserSharedFolder(anyUser)).thenReturn(topLevelSharedFolder);
    Folder toDelete = createAFolder("ToDelete", anyUser);
    grpSharedFolder.addChild(toDelete, anyUser);
    Notebook nb = createANotebookWithNEntries("nb", anyUser, 2);
    toDelete.addChild(nb, anyUser);
    DeletionPlan plan =
        deletionOrderPolicy.calculateDeletionOrder(toDelete, toDelete.getParent(), anyUser);
    assertEquals(2, plan.size()); // folder and nb but not entries
    assertEquals(toDelete, plan.getFinalElementToRemove());
    assertEquals(nb, plan.iterator().next());
  }
}
