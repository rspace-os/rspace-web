package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.model.Group;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.dtos.NotebookCreationResult;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.RecordToFolder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.impl.MovePermissionChecker;
import com.researchspace.service.impl.WorkspaceServiceImpl;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.shiro.authz.AuthorizationException;
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
  private static final Long GRANDPARENT_ID = null;

  private Folder target;
  private Folder source;
  private StructuredDocument doc;
  private User user;

  @Mock FolderManager folderManager;
  @Mock RecordManager recordManager;
  @Mock BaseRecordManager baseRecordManager;
  @Mock AuditTrailService auditService;
  @Mock MovePermissionChecker permissionChecker;
  @Mock SharingHandler recordShareHandler;
  @Mock GroupManager groupManager;

  @InjectMocks WorkspaceServiceImpl service;

  private Folder root;

  @BeforeEach
  void setUp() {
    root = folder(ROOT_ID);
    source = folder(SOURCE_FOLDER_ID);
    target = folder(TARGET_FOLDER_ID);
    user = TestFactory.createAnyUser("user");
    doc = docWithParent(RECORD_ID, source, user.getUsername());
  }

  private void mockHasMovePermission(boolean hasPermission) {
    when(permissionChecker.checkMovePermissions(
            any(User.class), any(Folder.class), any(BaseRecord.class)))
        .thenReturn(hasPermission);
  }

  @Test
  public void moveDocumentToAnotherFolderSuccess() {
    when(folderManager.getFolder(TARGET_FOLDER_ID, user)).thenReturn(target);
    commonMocks(user, target, doc);
    mockHasMovePermission(true);

    mockMoveSuccess(doc);
    var results =
        service.moveRecords(
            List.of(RECORD_ID),
            String.valueOf(TARGET_FOLDER_ID),
            SOURCE_FOLDER_ID,
            GRANDPARENT_ID,
            user);

    assertEquals(1, results.size());
    assertTrue(results.get(0).isSucceeded());
    verify(recordManager).move(RECORD_ID, TARGET_FOLDER_ID, SOURCE_FOLDER_ID, user);
    verify(auditService).notify(any());
  }

  @Test
  public void moveToRootUsingSlash() {
    commonMocks(user, root, doc);
    mockHasMovePermission(true);

    ServiceOperationResult<BaseRecord> moveResult = mock(ServiceOperationResult.class);
    when(moveResult.isSucceeded()).thenReturn(true);
    when(moveResult.getEntity()).thenReturn(doc);
    when(recordManager.move(RECORD_ID, ROOT_ID, SOURCE_FOLDER_ID, user)).thenReturn(moveResult);

    var results =
        service.moveRecords(List.of(RECORD_ID), "/", SOURCE_FOLDER_ID, GRANDPARENT_ID, user);

    assertEquals(1, results.size());
    assertTrue(results.get(0).isSucceeded());
    verify(recordManager).move(RECORD_ID, root.getId(), SOURCE_FOLDER_ID, user);
    verify(auditService).notify(any());
  }

  @Test
  public void moveWithTrailingSlashTargetId() {
    when(folderManager.getFolder(TARGET_FOLDER_ID, user)).thenReturn(target);
    commonMocks(user, target, doc);
    mockHasMovePermission(true);
    mockMoveSuccess(doc);

    var results =
        service.moveRecords(
            List.of(RECORD_ID), TARGET_FOLDER_ID + "/", SOURCE_FOLDER_ID, GRANDPARENT_ID, user);

    assertEquals(1, results.size());
    assertTrue(results.get(0).isSucceeded());
    verify(recordManager).move(RECORD_ID, TARGET_FOLDER_ID, SOURCE_FOLDER_ID, user);
    verify(auditService).notify(any());
  }

  @Test
  public void movingFolderIntoItselfThrowsException() {
    when(folderManager.getFolder(SOURCE_FOLDER_ID, user)).thenReturn(source);
    when(baseRecordManager.get(SOURCE_FOLDER_ID, user)).thenReturn(source);
    mockHasMovePermission(true);

    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                service.moveRecords(
                    List.of(SOURCE_FOLDER_ID),
                    String.valueOf(SOURCE_FOLDER_ID),
                    123L,
                    GRANDPARENT_ID,
                    user));

    assertEquals(
        "Attempt to move record with ID: " + SOURCE_FOLDER_ID + " to itself", ex.getMessage());
    verify(auditService, never()).notify(any());
    verify(recordManager, never())
        .move(any(Long.class), any(Long.class), any(Long.class), any(User.class));
  }

  @Test
  public void moveDocumentFailureNotCounted() {
    when(folderManager.getRootFolderForUser(user)).thenReturn(root);
    when(folderManager.getFolder(TARGET_FOLDER_ID, user)).thenReturn(target);
    when(baseRecordManager.get(RECORD_ID, user)).thenReturn(doc);
    when(recordManager.exists(RECORD_ID)).thenReturn(true);
    when(recordManager.get(RECORD_ID)).thenReturn(doc);
    when(recordManager.isSharedNotebookWithoutCreatePermission(user, target))
            .thenReturn(false);
    mockHasMovePermission(true);
    mockFail();

    var results =
        service.moveRecords(
            List.of(RECORD_ID),
            String.valueOf(TARGET_FOLDER_ID),
            SOURCE_FOLDER_ID,
            GRANDPARENT_ID,
            user);

    assertEquals(1, results.size());
    assertFalse(results.get(0).isSucceeded());
    verify(auditService, never()).notify(any());
  }

  @Test
  public void sameSourceAndTargetFolderThrowsException() {
    when(folderManager.getFolder(SOURCE_FOLDER_ID, user)).thenReturn(source);

    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                service.moveRecords(
                    List.of(RECORD_ID),
                    String.valueOf(SOURCE_FOLDER_ID),
                    SOURCE_FOLDER_ID,
                    GRANDPARENT_ID,
                    user));

    assertEquals("Source and target folder are the same. Id: " + SOURCE_FOLDER_ID, ex.getMessage());
    verify(recordManager, never())
        .move(any(Long.class), any(Long.class), any(Long.class), any(User.class));
    verify(auditService, never()).notify(any());
  }

  @Test
  public void invalidInputThrowsException() {
    assertThrows(
        IllegalArgumentException.class,
        () -> service.moveRecords(null, null, 0L, GRANDPARENT_ID, user));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.moveRecords(List.of(), null, 0L, GRANDPARENT_ID, user));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.moveRecords(List.of(1L), null, 0L, GRANDPARENT_ID, user));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.moveRecords(List.of(1L), "invalid", 0L, GRANDPARENT_ID, user));
  }

  @Test
  public void permissionDenied_throwsIllegalArgumentException() {
    when(folderManager.getFolder(TARGET_FOLDER_ID, user)).thenReturn(target);
    when(baseRecordManager.get(RECORD_ID, user)).thenReturn(doc);
    mockHasMovePermission(false);

    AuthorizationException ex =
        assertThrows(
            AuthorizationException.class,
            () ->
                service.moveRecords(
                    List.of(RECORD_ID),
                    String.valueOf(TARGET_FOLDER_ID),
                    SOURCE_FOLDER_ID,
                    GRANDPARENT_ID,
                    user));

    assertEquals(
        "User: " + user.getId() + " does not have permission to move record with ID: " + RECORD_ID,
        ex.getMessage());
  }

  @Test
  public void recordAlreadyInTargetFolder_throwsIllegalArgumentException() {
    when(folderManager.getFolder(TARGET_FOLDER_ID, user)).thenReturn(target);
    StructuredDocument docInTarget = docWithParent(RECORD_ID, target, user.getUsername());
    when(baseRecordManager.get(RECORD_ID, user)).thenReturn(docInTarget);
    mockHasMovePermission(true);

    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                service.moveRecords(
                    List.of(RECORD_ID),
                    String.valueOf(TARGET_FOLDER_ID),
                    999L,
                    GRANDPARENT_ID,
                    user));

    assertEquals("Record with ID: " + RECORD_ID + " already in target folder", ex.getMessage());
  }

  @Test
  public void createNotebookNonSharedNoGrandparentCreatesInParent() {
    long parentId = TARGET_FOLDER_ID;
    Folder parent = folder(parentId);
    when(folderManager.getFolder(parentId, user)).thenReturn(parent);

    long notebookId = 123L;
    when(folderManager.createNewNotebook(
            eq(parentId), eq("My NB"), any(DefaultRecordContext.class), eq(user)))
        .thenReturn(notebookWithId(notebookId));

    NotebookCreationResult result = service.createNotebook("My NB", parentId, null, user);

    assertEquals(notebookId, result.getNotebookId());
    assertEquals(parentId, result.getGrandParentId());
    assertNull(result.getGroupName());
    verify(auditService).notify(any());
  }

  @Test
  void createNotebookNonSharedParentUsesProvidedGrandparent() {
    long parentId = TARGET_FOLDER_ID;
    long grandparentId = 999L;
    Folder parent = folder(parentId);
    when(folderManager.getFolder(parentId, user)).thenReturn(parent);

    long createdId = 123L;
    when(folderManager.createNewNotebook(
            eq(parentId), eq("My NB"), any(DefaultRecordContext.class), eq(user)))
        .thenReturn(notebookWithId(createdId));

    NotebookCreationResult result = service.createNotebook("My NB", parentId, grandparentId, user);

    assertEquals(grandparentId, result.getGrandParentId());
    assertNull(result.getGroupName());
    verify(auditService).notify(any());
  }

  @Test
  void createNotebookSharedParentCreatesInRootAndSharesToGroup() {
    long parentId = TARGET_FOLDER_ID;
    Folder sharedParent = folder(parentId);
    sharedParent.addType(RecordType.SHARED_FOLDER);
    when(folderManager.getFolder(parentId, user)).thenReturn(sharedParent);

    when(folderManager.getRootFolderForUser(user)).thenReturn(root);
    long createdId = 123L;
    when(folderManager.createNewNotebook(
            eq(root.getId()), eq("Shared NB"), any(DefaultRecordContext.class), eq(user)))
        .thenReturn(notebookWithId(createdId));

    Group group =
        TestFactory.createAnyGroup(TestFactory.createAnyUserWithRole("pi", Role.PI_ROLE.getName()));
    RecordGroupSharing rgs = new RecordGroupSharing();
    rgs.setSharee(group);
    when(recordShareHandler.shareIntoSharedFolderOrNotebook(
            eq(user), eq(sharedParent), eq(createdId), eq(null)))
        .thenReturn(List.of(rgs));

    NotebookCreationResult result = service.createNotebook("Shared NB", parentId, null, user);

    assertEquals(createdId, result.getNotebookId());
    assertEquals(sharedParent.getId(), result.getGrandParentId());
    assertEquals(group.getDisplayName(), result.getGroupName());
    verify(recordShareHandler).shareIntoSharedFolderOrNotebook(user, sharedParent, createdId, null);
    verify(auditService).notify(any());
  }

  private Notebook notebookWithId(long id) {
    Notebook nb = new Notebook();
    nb.setId(id);
    return nb;
  }

  private void commonMocks(User user, Folder sharedTarget, StructuredDocument doc) {
    when(baseRecordManager.get(RECORD_ID, user)).thenReturn(doc);
    when(recordManager.exists(RECORD_ID)).thenReturn(true);
    when(recordManager.get(RECORD_ID)).thenReturn(doc);
    when(recordManager.isSharedNotebookWithoutCreatePermission(user, sharedTarget))
        .thenReturn(false);
    when(folderManager.getRootFolderForUser(any(User.class)))
            .thenReturn(root);
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
}
