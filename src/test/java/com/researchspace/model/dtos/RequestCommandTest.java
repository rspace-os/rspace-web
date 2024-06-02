package com.researchspace.model.dtos;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.comms.CommunicationTargetFinderPolicy.TargetFinderPolicy;
import com.researchspace.model.User;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.comms.MsgOrReqstCreationCfg;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.EnumSet;
import org.apache.commons.lang.ArrayUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RequestCommandTest extends SpringTransactionalTest {

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test(expected = IllegalArgumentException.class)
  public void testSetTargetFinderPolicyThrowsIAEIfNotInEnum() {
    MsgOrReqstCreationCfg rc = new MsgOrReqstCreationCfg();
    rc.setTargetFinderPolicy("XXX");
  }

  public void testSetTargetFinderPolicyHappyCase() {

    for (TargetFinderPolicy tpc : EnumSet.allOf(TargetFinderPolicy.class)) {
      MsgOrReqstCreationCfg rc = new MsgOrReqstCreationCfg();
      rc.setTargetFinderPolicy(tpc.name());
    }
  }

  @Test
  public void testSetFilterByPermissions() {
    User u = userDao.getUserByUserName("user1a"); // already created by dbunit
    RSpaceTestUtils.logoutCurrUserAndLoginAs(u.getUsername(), "user1234");
    MsgOrReqstCreationCfg rc = new MsgOrReqstCreationCfg(u, permissionUtils);
    rc.setPermUtils(permissionUtils);
    // by default, won't have permissions to request ext share
    assertFalse(ArrayUtils.contains(rc.getAllMessageTypes(), MessageType.REQUEST_EXTERNAL_SHARE));
    assertTrue(ArrayUtils.contains(rc.getAllMessageTypes(), MessageType.SIMPLE_MESSAGE));
  }
}
