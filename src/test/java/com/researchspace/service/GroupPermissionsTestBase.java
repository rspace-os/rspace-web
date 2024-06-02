package com.researchspace.service;

import com.researchspace.core.util.TransformerUtils;
import com.researchspace.model.User;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.testutils.TestGroup;

public abstract class GroupPermissionsTestBase extends SpringTransactionalTest {

  User createCommunity(TestGroup testgrp) {
    User commAdmin = createAndSaveAdminUser();
    initialiseContentWithEmptyContent(commAdmin);
    TestCommunity testCommunity = createTestCommunity(TransformerUtils.toSet(testgrp), commAdmin);
    return commAdmin;
  }

  User addOtherPiToGroup(TestGroup testgrp) {
    User otherPi = createAndSaveAPi();
    initialiseContentWithEmptyContent(otherPi);
    addUsersToGroup(otherPi, testgrp.getGroup(), new User[] {});
    return otherPi;
  }
}
