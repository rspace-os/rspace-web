package com.researchspace.service.impl;

import static com.researchspace.model.record.TestFactory.createAFolder;
import static com.researchspace.model.record.TestFactory.createANotebookWithNEntries;
import static com.researchspace.model.record.TestFactory.createAnyUser;
import static org.junit.Assert.assertEquals;

import com.researchspace.dao.FolderDao;
import com.researchspace.model.User;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.DeletionPlan;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;

@RunWith(MockitoJUnitRunner.class)
public class DeleteFolderFromSharedFolderPolicyTest {

  @Rule public MockitoRule rule = MockitoJUnit.rule();
  @InjectMocks DeleteFolderFromSharedFolderPolicy deletionOrderPolicy;

  @Mock FolderDao folderDao;

  User anyUser = null;

  Folder grpSharedFolder, topLevelSharedFolder;

  @Before
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

  @After
  public void tearDown() throws Exception {}

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

  @Test(expected = IllegalArgumentException.class)
  public void rejectFolderToDeleteThatIsNotInSharedFolderTree() {
    Mockito.when(folderDao.getUserSharedFolder(anyUser))
        .thenReturn(TestFactory.createAFolder("notInTree", anyUser));
    Folder toDelete = createAFolder("ToDelete", anyUser);
    grpSharedFolder.addChild(toDelete, anyUser);
    deletionOrderPolicy.calculateDeletionOrder(toDelete, toDelete.getParent(), anyUser);
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
