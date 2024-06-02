package com.researchspace.service.cloud;

import static org.junit.Assert.assertEquals;

import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.model.User;
import com.researchspace.model.permissions.IGroupPermissionUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.testutils.TestGroup;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

// RA Feb 19
// Ideally we would annotate with @CommunityTest. But in this case
// something strange happens - on Jenkins, the user is not saved in the DB and authentication fails.
// no idea why this happens. Must be something to do with spring context reloading.
public class CloudGroupManagerTest extends SpringTransactionalTest {

  private @Autowired IGroupPermissionUtils grpPermUtils;

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @After
  public void after() throws Exception {
    super.setUp();
    propertyHolder.setCloud("false"); // revert to default setting
  }

  @Test
  public void userCanSelfDeleteOnCommunity_RSPAC1662() throws Exception {
    TestGroup tg = createTestGroup(1);
    User subject = tg.u1();
    logoutAndLoginAs(subject);
    // non community fail
    CoreTestUtils.assertExceptionThrown(
        () ->
            grpPermUtils.assertLeaveGroupPermissions(
                tg.getPi().getUsername(), subject, tg.getGroup()),
        AuthorizationException.class);
    // now succeeds
    propertyHolder.setCloud("true");
    assertEquals(
        subject,
        grpPermUtils.assertLeaveGroupPermissions(subject.getUsername(), subject, tg.getGroup()));

    // sole  pi not able to remove himself
    CoreTestUtils.assertExceptionThrown(
        () ->
            grpPermUtils.assertLeaveGroupPermissions(
                tg.getPi().getUsername(), subject, tg.getGroup()),
        AuthorizationException.class);
  }
}
