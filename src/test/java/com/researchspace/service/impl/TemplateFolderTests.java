package com.researchspace.service.impl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.Folder;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.Before;
import org.junit.Test;

public class TemplateFolderTests extends SpringTransactionalTest {
  User any = null;

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @Test
  public void testDeleteSharedFolderNotPermitted() throws Exception {
    any = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(any);
    logoutAndLoginAs(any);
    Folder root = folderDao.getRootRecordForUser(any);
    Folder shared = folderDao.getUserSharedFolder(any);

    assertAuthorisationExceptionThrown(
        () -> recordDeletionMgr.deleteFolder(root.getId(), shared.getId(), any));
  }

  @Test
  public void test() {
    any = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(any);
    Folder templateFolder =
        recordFactory.createSystemCreatedFolder(Folder.TEMPLATE_MEDIA_FOLDER_NAME, any);
    templateFolder.addType(RecordType.TEMPLATE);
    perFactory.setUpACLForIndividualTemplateFolder(templateFolder, any);
    logoutAndLoginAs(any);
    assertTrue(permissionUtils.isPermitted(templateFolder, PermissionType.READ, any));
    assertFalse(permissionUtils.isPermitted(templateFolder, PermissionType.DELETE, any));
    assertFalse(permissionUtils.isPermitted(templateFolder, PermissionType.RENAME, any));
    assertFalse(permissionUtils.isPermitted(templateFolder, PermissionType.EXPORT, any));
    assertFalse(permissionUtils.isPermitted(templateFolder, PermissionType.SHARE, any));
    assertTrue(permissionUtils.isPermitted(templateFolder, PermissionType.CREATE_FOLDER, any));
    assertFalse(permissionUtils.isPermitted(templateFolder, PermissionType.SEND, any));
  }
}
