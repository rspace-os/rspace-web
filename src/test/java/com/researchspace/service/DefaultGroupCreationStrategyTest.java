package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.model.Group;
import com.researchspace.model.GroupType;
import com.researchspace.model.User;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class DefaultGroupCreationStrategyTest extends SpringTransactionalTest {

  private @Autowired IGroupCreationStrategy strategy;

  @Test
  public void testCreateAndSaveGroupUserUserGroupTypeUserArray() {

    User pi = createAndSaveAPi();
    User grpMember = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(pi, grpMember);
    logoutAndLoginAs(pi);
    Group grp = strategy.createAndSaveGroup(pi, pi, GroupType.LAB_GROUP, grpMember, pi);
    assertEquals(2, grpMgr.getGroupEventsForGroup(pi, grp).size());
  }
}
