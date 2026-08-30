package com.researchspace.model.dtos;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.comms.CommunicationTargetFinderPolicy.TargetFinderPolicy;
import com.researchspace.model.User;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.comms.MsgOrReqstCreationCfg;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.EnumSet;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class RequestCommandTest extends SpringTransactionalTest {

  private @Autowired IPermissionUtils permissionUtils;

  @AfterEach
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testSetTargetFinderPolicyThrowsIAEIfNotInEnum() {
    MsgOrReqstCreationCfg rc = new MsgOrReqstCreationCfg();
    assertThrows(IllegalArgumentException.class, () -> rc.setTargetFinderPolicy("XXX"));
  }

  public void testSetTargetFinderPolicyHappyCase() {

    for (TargetFinderPolicy tpc : EnumSet.allOf(TargetFinderPolicy.class)) {
      MsgOrReqstCreationCfg rc = new MsgOrReqstCreationCfg();
      rc.setTargetFinderPolicy(tpc.name());
    }
  }

  @Test
  public void testSetFilterByPermissions() {
    User u = userDao.getUserByUsername("user1a"); // already created by dbunit
    RSpaceTestUtils.logoutCurrUserAndLoginAs(u.getUsername(), "user1234");
    MsgOrReqstCreationCfg rc = new MsgOrReqstCreationCfg(u, permissionUtils);
    rc.setPermUtils(permissionUtils);
    // by default, won't have permissions to request ext share
    assertFalse(ArrayUtils.contains(rc.getAllMessageTypes(), MessageType.REQUEST_EXTERNAL_SHARE));
    assertTrue(ArrayUtils.contains(rc.getAllMessageTypes(), MessageType.SIMPLE_MESSAGE));
  }
}
