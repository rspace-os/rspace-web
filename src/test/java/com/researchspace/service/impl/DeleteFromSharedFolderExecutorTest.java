package com.researchspace.service.impl;

import static com.researchspace.testutils.TestFactory.createAnyUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.RSPath;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.views.CompositeRecordOperationResult;
import com.researchspace.service.DeletionPlan;
import com.researchspace.service.FolderManager;
import com.researchspace.service.RecordSharingManager;
import com.researchspace.testutils.TestFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DeleteFromSharedFolderExecutorTest {
  @InjectMocks DeleteFromSharedFolderExecutor executor;
  @Mock RecordSharingManager sharingMgr;
  @Mock FolderManager folderMgr;
  DeletionPlan plan = null;
  User anyUser = null;
  Folder parentFolder, toDelete, child;
  List<BaseRecord> pathElements1 = new ArrayList<>();

  @BeforeEach
  public void setUp() throws Exception {

    anyUser = createAnyUser("any");
    parentFolder = TestFactory.createAFolder("parent", anyUser);
    toDelete = TestFactory.createAFolder("toDelete", anyUser);
    child = TestFactory.createAFolder("child", anyUser);
    parentFolder.addChild(toDelete, anyUser);
    toDelete.addChild(child, anyUser);
    pathElements1 = Arrays.asList(new BaseRecord[] {parentFolder, toDelete, child});
  }

  @Test
  public void rejectFinalItemToDeleteIfNotFolder() {
    RSPath path = new RSPath(pathElements1);
    plan = new DeletionPlan(anyUser, path, parentFolder);
    plan.add(aDocument());
    assertThrows(IllegalStateException.class, () -> executor.execute(anyResults(), plan));
  }

  @Test
  public void deleteSingleEmptyFolder() {
    RSPath path = new RSPath(pathElements1);
    plan = new DeletionPlan(anyUser, path, parentFolder);
    plan.add(child);
    CompositeRecordOperationResult result = anyResults();
    executor.execute(result, plan);
    // unshare not called as we're deleting a single folder
    Mockito.verifyNoInteractions(sharingMgr);
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
    Mockito.verifyNoInteractions(sharingMgr);
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
