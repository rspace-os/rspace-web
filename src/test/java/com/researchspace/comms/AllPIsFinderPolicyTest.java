package com.researchspace.comms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.researchspace.Constants;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.Set;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class AllPIsFinderPolicyTest extends SpringTransactionalTest {

  @Autowired
  @Qualifier("findAllPisPolicy")
  private CommunicationTargetFinderPolicy policy;

  @Test
  public void testFindPotentialTargetsFor() throws IllegalAddChildOperation {

    User pi1 = createAndSaveUserIfNotExists("pi1", Constants.PI_ROLE);
    User pi2 = createAndSaveUserIfNotExists("pi2", Constants.PI_ROLE);
    User pi3 = createAndSaveUserIfNotExists("pi3", Constants.PI_ROLE);
    User u1 = createAndSaveUserIfNotExists("u1");
    initialiseContentWithEmptyContent(pi1, pi2, pi3, u1);

    Set<User> initialPiTargets =
        policy.findPotentialTargetsFor(MessageType.REQUEST_EXTERNAL_SHARE, null, null, pi1);
    assertEquals(0, initialPiTargets.size());
    assertNotNull(policy.getFailureMessageIfUserInvalidTarget());

    Group g1 = createGroup("g1", pi1);
    Group g2 = createGroup("g2", pi2);
    Group g3 = createGroup("g3", pi3);
    addUsersToGroup(pi1, g1, u1);
    addUsersToGroup(pi2, g2, u1);
    addUsersToGroup(pi3, g3, u1);

    Set<User> potentialPITargets =
        policy.findPotentialTargetsFor(MessageType.REQUEST_EXTERNAL_SHARE, null, null, pi1);
    assertEquals(2, potentialPITargets.size());

    Set<User> matchingPITargets =
        policy.findPotentialTargetsFor(MessageType.REQUEST_EXTERNAL_SHARE, null, "3", pi1);
    assertEquals(1, matchingPITargets.size());
  }
}
