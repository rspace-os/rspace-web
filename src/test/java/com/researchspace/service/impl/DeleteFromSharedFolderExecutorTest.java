package com.researchspace.service.impl;

import static com.researchspace.model.record.TestFactory.createAnyUser;
import static org.junit.Assert.assertEquals;

import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.RSPath;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.views.CompositeRecordOperationResult;
import com.researchspace.service.DeletionPlan;
import com.researchspace.service.FolderManager;
import com.researchspace.service.RecordSharingManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
public class DeleteFromSharedFolderExecutorTest {

  @Rule public MockitoRule rule = MockitoJUnit.rule();
  @InjectMocks DeleteFromSharedFolderExecutor executor;
  @Mock RecordSharingManager sharingMgr;
  @Mock FolderManager folderMgr;
  DeletionPlan plan = null;
  User anyUser = null;
  Folder parentFolder, toDelete, child;
  List<BaseRecord> pathElements1 = new ArrayList<>();

  @Before
  public void setUp() throws Exception {

    anyUser = createAnyUser("any");
    parentFolder = TestFactory.createAFolder("parent", anyUser);
    toDelete = TestFactory.createAFolder("toDelete", anyUser);
    child = TestFactory.createAFolder("child", anyUser);
    parentFolder.addChild(toDelete, anyUser);
    toDelete.addChild(child, anyUser);
    pathElements1 = Arrays.asList(new BaseRecord[] {parentFolder, toDelete, child});
  }

  @After
  public void tearDown() throws Exception {}

  @Test(expected = IllegalStateException.class)
  public void rejectFinalItemToDeleteIfNotFolder() {
    RSPath path = new RSPath(pathElements1);
    plan = new DeletionPlan(anyUser, path, parentFolder);
    plan.add(aDocument());
    executor.execute(anyResults(), plan);
  }

  @Test
  public void deleteSingleEmptyFolder() {
    RSPath path = new RSPath(pathElements1);
    plan = new DeletionPlan(anyUser, path, parentFolder);
    plan.add(child);
    CompositeRecordOperationResult result = anyResults();
    executor.execute(result, plan);
    // unshare not called as we're deleting a single folder
    Mockito.verifyZeroInteractions(sharingMgr);
    assertEquals(1, result.getRecords().size());
  }

  @Test
  public void deleteNestedFolder() {
    RSPath path = new RSPath(pathElements1);
    plan = new DeletionPlan(anyUser, path, parentFolder);
    plan.add(toDelete);
    plan.add(child);
    toDelete.setId(1L);
    CompositeRecordOperationResult result = anyResults();
    executor.execute(result, plan);
    // unshare not called as we're deleting  folders
    Mockito.verifyZeroInteractions(sharingMgr);
    // child folder removed
    Mockito.verify(folderMgr).removeBaseRecordFromFolder(child, toDelete.getId());
    assertEquals(2, result.getRecords().size());
  }

  @Test
  public void deleteNotebookDoesntDeleteEntries() {
    RSPath path = new RSPath(pathElements1);
    plan = new DeletionPlan(anyUser, path, parentFolder);
    plan.add(toDelete);
    plan.add(child);
    Notebook nb = TestFactory.createANotebook("nb", anyUser);

    nb.addChild(TestFactory.createAnySD(), anyUser);
    child.addChild(nb, anyUser);
    plan.add(nb);
    toDelete.setId(1L);
    CompositeRecordOperationResult result = anyResults();
    executor.execute(result, plan);
    // unshare not called as we're deleting  folders
    Mockito.verify(sharingMgr).unshareFromSharedFolder(plan.getUser(), nb, plan.getPath());
    // child folder removed
    Mockito.verify(folderMgr).removeBaseRecordFromFolder(child, toDelete.getId());
    assertEquals(3, result.getRecords().size()); // 2 folders and nb
  }

  private StructuredDocument aDocument() {
    return TestFactory.createAnySD();
  }

  private CompositeRecordOperationResult anyResults() {
    return new CompositeRecordOperationResult(null, parentFolder);
  }
}
