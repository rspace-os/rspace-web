package com.researchspace.comms;

import static com.researchspace.testutils.TestFactory.createAnyRecord;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.Group;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.dtos.ShareConfigElement;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.testutils.TestFactory;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class StrictPermissionPolicyTest extends SpringTransactionalTest {

  @Autowired
  @Qualifier("strictTargetFinderPolicy")
  private CommunicationTargetFinderPolicy policy;

  @Test
  public void singleUserNotInGrpCanSendReviewDocRequestToSelf() throws Exception {
    User any = TestFactory.createAnyUser("any");
    Record sd1 = createAnyRecord(any);
    Set<User> recipients =
        policy.findPotentialTargetsFor(MessageType.REQUEST_RECORD_REVIEW, sd1, null, any);
    assertIterableEquals(java.util.List.of(any), recipients);
    recipients = policy.findPotentialTargetsFor(MessageType.REQUEST_RECORD_WITNESS, sd1, null, any);
    assertTrue(recipients.isEmpty());
  }

  @Test
  public void testSharedNotebook() throws Exception {
    User pi1 = createAndSaveAPi();
    User pi2 = createAndSaveAPi();
    User u1 = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(pi1, pi2, u1);
    Group group1 = createGroup("g1", pi1);
    addUsersToGroup(pi1, group1, pi2, u1);
    logoutAndLoginAs(u1);
    // a regular user can share a notebook, and individual entries will offer witnesses:
    Notebook nb = createNotebookWithNEntries(u1.getRootFolder().getId(), "u1nb", 2, u1);
    StructuredDocument entry = nb.getChildrens().iterator().next().asStrucDoc();
    shareNotebookWithGroup(u1, nb, group1, "write");

    Set<User> recipients =
        policy.findPotentialTargetsFor(MessageType.REQUEST_RECORD_WITNESS, entry, null, u1);
    assertTrue(recipients.contains(pi1));
    Set<User> recipients2 =
        policy.findPotentialTargetsFor(MessageType.REQUEST_RECORD_REVIEW, entry, null, u1);
    assertTrue(recipients2.contains(pi1));

    // but a pi sharing notebook should also see this:
    logoutAndLoginAs(pi2);
    // a regular user can share a notebook, and individual entries will offer witnesses:
    Notebook nb2 = createNotebookWithNEntries(pi2.getRootFolder().getId(), "u1nb", 2, pi2);
    StructuredDocument entry2 = nb2.getChildrens().iterator().next().asStrucDoc();
    shareNotebookWithGroup(pi2, nb2, group1, "write");

    recipients =
        policy.findPotentialTargetsFor(MessageType.REQUEST_RECORD_WITNESS, entry2, null, pi2);
    assertEquals(2, recipients.size());
    assertTrue(recipients.containsAll(java.util.List.of(pi1, u1)));
    recipients2 =
        policy.findPotentialTargetsFor(MessageType.REQUEST_RECORD_REVIEW, entry2, null, pi2);
    assertEquals(3, recipients2.size());
    assertTrue(recipients2.containsAll(java.util.List.of(pi1, pi2, u1)));
  }

  @Test
  public void testFindPotentialTargetsFor() throws Exception {
    // create some users
    User pi = createAndSaveAPi();
    User docOwner = createAndSaveRandomUser();
    User otherGrpMember = createAndSaveRandomUser();
    User notInGroup = createAndSaveRandomUser();
    initUsersWithEmptyContent(pi, docOwner, otherGrpMember, notInGroup);
    // and a group
    Group group1 = createGroup("g1", pi);
    addUsersToGroup(pi, group1, docOwner, otherGrpMember);
    logoutAndLoginAs(docOwner);
    // create a record and share it
    StructuredDocument sd1 = createBasicDocumentInRootFolderWithText(docOwner, "any");
    ShareConfigElement cfg = new ShareConfigElement(sd1.getId(), "READ");
    cfg.setGroupid(group1.getId());

    // before sharing your pi should be on the list:
    Set<User> recipients3 =
        policy.findPotentialTargetsFor(MessageType.REQUEST_RECORD_WITNESS, sd1, null, docOwner);
    assertTrue(recipients3.contains(pi));
    assertEquals(1, recipients3.size());
    sharingMgr.shareRecord(docOwner, sd1.getId(), new ShareConfigElement[] {cfg});

    // just need read permission for witnessing, so expect 2 people in list (self is excluded)
    Set<User> recipients =
        policy.findPotentialTargetsFor(MessageType.REQUEST_RECORD_WITNESS, sd1, null, docOwner);
    assertEquals(2, recipients.size());
    assertTrue(recipients.containsAll(java.util.List.of(pi, otherGrpMember)));
    assertEquals(2, recipients.size());

    // self and pi can review
    Set<User> recipients2 =
        policy.findPotentialTargetsFor(MessageType.REQUEST_RECORD_REVIEW, sd1, null, docOwner);
    assertEquals(2, recipients2.size());
    assertTrue(recipients2.containsAll(java.util.List.of(pi, docOwner)));
    assertEquals(2, recipients2.size());

    // if search term is passed, only matching user is returned
    Set<User> matchingRecipients =
        policy.findPotentialTargetsFor(
            MessageType.REQUEST_RECORD_REVIEW, sd1, pi.getUsername().substring(0, 4), docOwner);
    assertEquals(1, matchingRecipients.size());
    assertEquals(pi, matchingRecipients.toArray()[0]);

    // RSDEV-992: a blank term must behave exactly like no term (and not scan the users table)
    Set<User> blankTermRecipients =
        policy.findPotentialTargetsFor(MessageType.REQUEST_RECORD_REVIEW, sd1, "  ", docOwner);
    assertEquals(recipients2, blankTermRecipients);

    // now we give the document edit permission
    RecordGroupSharing rgs = sharingMgr.getSharedRecordsForUser(docOwner).get(0);
    sharingMgr.updatePermissionForRecord(rgs.getId(), "WRITE", docOwner.getUsername());

    Set<User> recipients4 =
        policy.findPotentialTargetsFor(MessageType.REQUEST_RECORD_REVIEW, sd1, null, docOwner);
    assertFalse(recipients4.contains(notInGroup));
    assertEquals(3, recipients4.size());
    assertEquals(3, recipients4.size());
    assertTrue(recipients4.containsAll(java.util.List.of(pi, docOwner, otherGrpMember)));
    RSpaceTestUtils.logout();

    // now let's disable other - shouldn't appear on potential targets list
    otherGrpMember.setEnabled(false);
    otherGrpMember = userDao.save(otherGrpMember);
    logoutAndLoginAs(docOwner);
    Set<User> recipients5 =
        policy.findPotentialTargetsFor(MessageType.REQUEST_RECORD_REVIEW, sd1, null, docOwner);
    assertEquals(2, recipients5.size());
    assertFalse(recipients5.contains(otherGrpMember));

    // RSPAC-697
    // create another group with pi and add Pis to each others group.
    User pi2 = createAndSaveAPi();
    initialiseContentWithEmptyContent(pi2);
    logoutAndLoginAs(pi2);
    Group grp2 = createGroup("g2", pi2);
    addUsersToGroup(pi2, grp2);

    // and add pi2 to group1 . So both PIs are in each other's group with a user role.
    grpMgr.addUserToGroup(pi2.getUsername(), group1.getId(), RoleInGroup.DEFAULT);
    // ppi2 creates an unshared doc. Should be no proposed witnesses.
    StructuredDocument pi2Doc = createBasicDocumentInRootFolderWithText(pi2, "any");
    // should be no witnesses
    pi2 = userDao.get(pi2.getId());
    Set<User> recipients6 =
        policy.findPotentialTargetsFor(MessageType.REQUEST_RECORD_REVIEW, pi2Doc, null, pi2);
    assertEquals(1, recipients6.size());
    assertIterableEquals(java.util.List.of(pi2), recipients6);
  }

  private void initUsersWithEmptyContent(User... users) throws IllegalAddChildOperation {
    for (User u : users) {
      initialiseContentWithEmptyContent(u);
    }
  }
}
