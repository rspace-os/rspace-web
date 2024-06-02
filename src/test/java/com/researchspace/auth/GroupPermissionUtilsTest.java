package com.researchspace.auth;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.permissions.IGroupPermissionUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.testutils.TestGroup;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class GroupPermissionUtilsTest extends SpringTransactionalTest {
  private @Autowired IGroupPermissionUtils grpPermUtils;

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @After
  public void tearDown() throws Exception {}

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
