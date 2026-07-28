package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.dao.FolderDao;
import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.RecordFactory;
import com.researchspace.testutils.TestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MovePermissionCheckerTest {
  @Mock IPermissionUtils permUtil;
  @Mock FolderDao fDao;

  @InjectMocks private MovePermissionChecker checker;

  @BeforeEach
  public void setUp() throws Exception {
    user = TestFactory.createAnyUser("user");
    other = TestFactory.createAnyUser("other");
    toMove = TestFactory.createAFolder("toMove", user);
    target = TestFactory.createAFolder("target", user);
    sharedFolder = TestFactory.createAFolder("sharedFolder", user);
    rootFolder = createARootFolderForUser(user);
  }

  private Folder createARootFolderForUser(User user) {
    return new RecordFactory().createRootFolder("root", user);
  }

  Folder toMove, target, sharedFolder, grp1, grp2, rootFolder;
  User user, other;

  @Test
  public void testCheckMovePermissions() throws IllegalAddChildOperation {
    when(permUtil.isPermitted(target, PermissionType.FOLDER_RECEIVE, user)).thenReturn(false);

    // can't move if target not allowed
    assertFalse(checker.checkMovePermissions(user, target, toMove));
    when(permUtil.isPermitted(target, PermissionType.FOLDER_RECEIVE, user)).thenReturn(true);
    when(permUtil.isPermitted(toMove, PermissionType.SEND, user)).thenReturn(false);

    // can't move if target IS allowed but can't send
    assertFalse(checker.checkMovePermissions(user, target, toMove));

    setUpPermissionsOK(target);
    // CAN move if perms OK and target not a system folder
    assertTrue(checker.checkMovePermissions(user, target, toMove));
  }

  @Test
  public void testCantShareIntoGroupFromOutside() throws IllegalAddChildOperation {
    setUpPermissionsOK(target);
    target.setSystemFolder(true);

    setUpSharedFolderHierarchy();
    // move across groups, disallowed
    assertFalse(checker.checkMovePermissions(user, target, toMove));

    setUpPermissionsOK(target);
    grp2.removeChild(toMove);
    assertFalse(checker.checkMovePermissions(user, target, toMove));

    setUpPermissionsOK(target);
    // now put in the same group, is allowed
    grp1.addChild(toMove, user);
    assertTrue(checker.checkMovePermissions(user, target, toMove));
  }

  protected void setUpSharedFolderHierarchy() throws IllegalAddChildOperation {
    Folder sub1 = TestFactory.createAFolder("sub1", user);
    grp1 = TestFactory.createAFolder("grp11", user);
    grp1.addType(RecordType.SHARED_GROUP_FOLDER_ROOT);
    grp2 = TestFactory.createAFolder("grp2", user);
    grp2.addType(RecordType.SHARED_GROUP_FOLDER_ROOT);

    sharedFolder.addChild(sub1, user);
    sub1.addChild(grp1, user);
    sub1.addChild(grp2, user);
    grp2.addChild(toMove, user);
    grp1.addChild(target, user);
  }

  // these 2 tests assert that loaading root folder does not trigger unnecessry DB call
  @Test
  public void homeFolderMoveOKDoesNotCallDatabaseMethod() throws IllegalAddChildOperation {
    setUpPermissionsOK(rootFolder);
    assertTrue(checker.checkMovePermissions(user, rootFolder, toMove));
    verify(fDao, never()).getUserSharedFolder(user);
  }

  @Test
  public void someoneElseshomeFolderMoveOKDoesNotCallDatabaseMethod()
      throws IllegalAddChildOperation {
    setUpPermissionsOK(rootFolder);
    assertFalse(checker.checkMovePermissions(other, rootFolder, toMove));
    verify(fDao, never()).getUserSharedFolder(user);
  }

  protected void setUpPermissionsOK(Folder target) {
    // lenient: someoneElseshomeFolderMoveOKDoesNotCallDatabaseMethod deliberately checks a
    // different user, so these stubbings are not matched by that test
    lenient()
        .when(permUtil.isPermitted(target, PermissionType.FOLDER_RECEIVE, user))
        .thenReturn(true);
    lenient().when(permUtil.isPermitted(toMove, PermissionType.SEND, user)).thenReturn(true);
    lenient().when(fDao.getUserSharedFolder(user)).thenReturn(sharedFolder);
  }
}
