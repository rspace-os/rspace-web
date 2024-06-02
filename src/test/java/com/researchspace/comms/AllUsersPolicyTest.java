package com.researchspace.comms;

import static org.junit.Assert.assertEquals;

import com.researchspace.model.User;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.Set;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class AllUsersPolicyTest extends SpringTransactionalTest {

  @Autowired
  @Qualifier("allUserPolicy")
  private CommunicationTargetFinderPolicy policy;

  @Test
  public void testFindPotentialTargetsFor() throws IllegalAddChildOperation {

    int initialUserCount =
        userMgr.getUsers().size() - 1; // anonymous user is removed from potential targets

    User u1 = createAndSaveUserIfNotExists("user1aupt");
    User u2 = createAndSaveUserIfNotExists("user2aupt");
    initialiseContentWithEmptyContent(u1, u2);

    Set<User> allTargets =
        policy.findPotentialTargetsFor(MessageType.SIMPLE_MESSAGE, null, null, u1);
    assertEquals(initialUserCount + 2, allTargets.size());

    // partial term search
    Set<User> matchingTargets =
        policy.findPotentialTargetsFor(MessageType.SIMPLE_MESSAGE, null, "er2aup", u1);
    assertEquals(1, matchingTargets.size());
  }
}
