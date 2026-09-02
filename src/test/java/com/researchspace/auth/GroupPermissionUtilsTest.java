package com.researchspace.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.permissions.IGroupPermissionUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.testutils.TestGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class GroupPermissionUtilsTest extends SpringTransactionalTest {
  private @Autowired IGroupPermissionUtils grpPermUtils;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
  }

  @Test
  public void testSetReadOrEditAllPermissionsForPi() {
    TestGroup group = createTestGroup(1);
    logoutAndLoginAs(group.getPi());

    grpPermUtils.setReadOrEditAllPermissionsForPi(group.getGroup(), group.getPi(), true);
    //		User u1 = group.u1();
    //		StructuredDocument doc = createBasicDocumentInRootFolderWithText(u1, "any");
    assertTrue(group.getGroup().getUserGroupForUser(group.getPi()).isPiCanEditWork());
    grpPermUtils.setReadOrEditAllPermissionsForPi(group.getGroup(), group.getPi(), false);
    assertFalse(group.getGroup().getUserGroupForUser(group.getPi()).isPiCanEditWork());
  }
}
