package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.RecordToFolder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.impl.WorkspaceServiceImpl;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class WorkspaceServiceTest {

  private static final long ROOT_ID = 12L;
  private static final long RECORD_ID = 34L;
  private static final long TARGET_FOLDER_ID = 56L;
  private static final long SOURCE_FOLDER_ID = 78L;

  private Folder target;
  private Folder source;
  private StructuredDocument doc;
  private User user;

  @Mock FolderManager folderManager;
  @Mock RecordManager recordManager;
  @Mock BaseRecordManager baseRecordManager;
  @Mock AuditTrailService auditService;

  @InjectMocks WorkspaceServiceImpl service;

  private Folder root;

  @BeforeEach
  void setUp() {
    root = folder(ROOT_ID);
    source = folder(SOURCE_FOLDER_ID);
    target = folder(TARGET_FOLDER_ID);
    user = TestFactory.createAnyUser("user");
    doc = docWithParent(RECORD_ID, source, user.getUsername());
    when(folderManager.getRootFolderForUser(any(User.class))).thenReturn(root);
  }

  @Test
  public void moveDocumentToAnotherFolderSuccess() {
    when(folderManager.getFolder(TARGET_FOLDER_ID, user)).thenReturn(target);
    commonMocks(user, target, doc);

    mockMoveSuccess(doc);
    var results =
        service.moveRecords(
            List.of(RECORD_ID), String.valueOf(TARGET_FOLDER_ID), SOURCE_FOLDER_ID, user);

    assertEquals(1, results.size());
    assertTrue(results.get(0).isSucceeded());
    verify(recordManager).move(RECORD_ID, TARGET_FOLDER_ID, SOURCE_FOLDER_ID, user);
    verify(auditService).notify(any());
  }

  @Test
  public void moveToRootUsingSlash() {
    commonMocks(user, root, doc);

    // successful move to root folder
    ServiceOperationResult<BaseRecord> moveResult = mock(ServiceOperationResult.class);
    when(moveResult.isSucceeded()).thenReturn(true);
    when(moveResult.getEntity()).thenReturn(doc);
    when(recordManager.move(RECORD_ID, ROOT_ID, SOURCE_FOLDER_ID, user)).thenReturn(moveResult);

    var results = service.moveRecords(List.of(RECORD_ID), "/", SOURCE_FOLDER_ID, user);

    assertEquals(1, results.size());
    assertTrue(results.get(0).isSucceeded());
    verify(recordManager).move(RECORD_ID, root.getId(), SOURCE_FOLDER_ID, user);
    verify(auditService).notify(any());
  }

  @Test
  public void moveWithTrailingSlashTargetId() {
    when(folderManager.getFolder(TARGET_FOLDER_ID, user)).thenReturn(target);
    commonMocks(user, target, doc);
    mockMoveSuccess(doc);

    var results =
        service.moveRecords(List.of(RECORD_ID), TARGET_FOLDER_ID + "/", SOURCE_FOLDER_ID, user);

    assertEquals(1, results.size());
    assertTrue(results.get(0).isSucceeded());
    verify(recordManager).move(RECORD_ID, TARGET_FOLDER_ID, SOURCE_FOLDER_ID, user);
    verify(auditService).notify(any());
  }

  @Test
  public void movingFolderIntoItselfIsSkipped() {
    when(folderManager.getFolder(SOURCE_FOLDER_ID, user)).thenReturn(source);

    var results =
        service.moveRecords(
            List.of(SOURCE_FOLDER_ID), String.valueOf(SOURCE_FOLDER_ID), 123L, user);

    assertEquals(0, results.size());
    verify(auditService, never()).notify(any());
    verify(recordManager, never())
        .move(any(Long.class), any(Long.class), any(Long.class), any(User.class));
  }

  @Test
  public void moveDocumentFailureNotCounted() {
    when(folderManager.getRootFolderForUser(user)).thenReturn(root);
    when(folderManager.getFolder(TARGET_FOLDER_ID, user)).thenReturn(target);
    commonMocks(user, target, doc);
    mockFail();

    var results =
        service.moveRecords(
            List.of(RECORD_ID), String.valueOf(TARGET_FOLDER_ID), SOURCE_FOLDER_ID, user);

    assertEquals(1, results.size());
    assertFalse(results.get(0).isSucceeded());
    verify(auditService, never()).notify(any());
  }

  @Test
  public void noOpMoveToSameFolderReturnsZeroAndDoesNotInvokeMove() {
    // Target is the same as the current parent (source)
    when(folderManager.getFolder(SOURCE_FOLDER_ID, user)).thenReturn(source);

    // Minimal stubbing to avoid unnecessary stubs
    when(baseRecordManager.get(RECORD_ID, user)).thenReturn(doc);
    when(recordManager.exists(RECORD_ID)).thenReturn(true);
    when(recordManager.get(RECORD_ID)).thenReturn(doc);

    var results =
        service.moveRecords(
            List.of(RECORD_ID), String.valueOf(SOURCE_FOLDER_ID), SOURCE_FOLDER_ID, user);

    assertEquals(1, results.size());
    assertFalse(results.get(0).isSucceeded());
    assertEquals("Record already in target folder", results.get(0).getMessage());
    verify(recordManager, never())
        .move(any(Long.class), any(Long.class), any(Long.class), any(User.class));
    verify(auditService, never()).notify(any());
  }

  @Test
  public void invalidInputThrowsException() {
    assertThrows(IllegalArgumentException.class, () -> service.moveRecords(null, null, 0L, user));
    assertThrows(
        IllegalArgumentException.class, () -> service.moveRecords(List.of(), null, 0L, user));
    assertThrows(
        IllegalArgumentException.class, () -> service.moveRecords(List.of(1L), null, 0L, user));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.moveRecords(List.of(1L), "invalid", 0L, user));
  }

  private void commonMocks(User user, Folder sharedTarget, StructuredDocument doc) {
    when(baseRecordManager.get(RECORD_ID, user)).thenReturn(doc);
    when(recordManager.exists(RECORD_ID)).thenReturn(true);
    when(recordManager.get(RECORD_ID)).thenReturn(doc);
    when(recordManager.isSharedNotebookWithoutCreatePermission(user, sharedTarget))
        .thenReturn(false);
  }

  private Folder folder(long id) {
    Folder f = new Folder();
    f.setId(id);
    return f;
  }

  private StructuredDocument docWithParent(long id, Folder parent, String owner) {
    StructuredDocument d = TestFactory.createAnySD();
    d.setId(id);
    Set<RecordToFolder> parents = new HashSet<>();
    parents.add(new RecordToFolder(d, parent, owner));
    d.setParents(parents);
    return d;
  }

  private void mockMoveSuccess(BaseRecord entity) {
    ServiceOperationResult<BaseRecord> moveResult = mock(ServiceOperationResult.class);
    when(moveResult.isSucceeded()).thenReturn(true);
    when(moveResult.getEntity()).thenReturn(entity);
    when(recordManager.move(RECORD_ID, TARGET_FOLDER_ID, SOURCE_FOLDER_ID, user))
        .thenReturn(moveResult);
  }

  private void mockFail() {
    ServiceOperationResult<BaseRecord> moveResultFail = mock(ServiceOperationResult.class);
    when(moveResultFail.isSucceeded()).thenReturn(false);
    when(recordManager.move(RECORD_ID, TARGET_FOLDER_ID, SOURCE_FOLDER_ID, user))
        .thenReturn(moveResultFail);
  }

  @Test
  public void moveRecordsCountSuccess_countsSuccessfulMoves() {
    when(folderManager.getFolder(TARGET_FOLDER_ID, user)).thenReturn(target);
    commonMocks(user, target, doc);

    // success path
    mockMoveSuccess(doc);
    int moved =
        service.moveRecordsCountSuccess(
            List.of(RECORD_ID), String.valueOf(TARGET_FOLDER_ID), SOURCE_FOLDER_ID, user);
    assertEquals(1, moved);

    // failure path
    mockFail();
    int movedFail =
        service.moveRecordsCountSuccess(
            List.of(RECORD_ID), String.valueOf(TARGET_FOLDER_ID), SOURCE_FOLDER_ID, user);
    assertEquals(0, movedFail);
  }
}
