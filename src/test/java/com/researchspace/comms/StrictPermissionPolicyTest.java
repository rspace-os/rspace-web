package com.researchspace.comms;

import static com.researchspace.model.record.TestFactory.createAnyRecord;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

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
import com.researchspace.model.record.TestFactory;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.Set;
import org.junit.Test;
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
    assertThat(recipients, contains(any));
    recipients = policy.findPotentialTargetsFor(MessageType.REQUEST_RECORD_WITNESS, sd1, null, any);
    assertThat(recipients, is(empty()));
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
    assertThat(recipients, hasItem(pi1));
    Set<User> recipients2 =
        policy.findPotentialTargetsFor(MessageType.REQUEST_RECORD_REVIEW, entry, null, u1);
    assertThat(recipients2, hasItem(pi1));

    // but a pi sharing notebook should also see this:
    logoutAndLoginAs(pi2);
    // a regular user can share a notebook, and individual entries will offer witnesses:
    Notebook nb2 = createNotebookWithNEntries(pi2.getRootFolder().getId(), "u1nb", 2, pi2);
    StructuredDocument entry2 = nb2.getChildrens().iterator().next().asStrucDoc();
    shareNotebookWithGroup(pi2, nb2, group1, "write");

    recipients =
        policy.findPotentialTargetsFor(MessageType.REQUEST_RECORD_WITNESS, entry2, null, pi2);
    assertThat(recipients, containsInAnyOrder(pi1, u1));
    recipients2 =
        policy.findPotentialTargetsFor(MessageType.REQUEST_RECORD_REVIEW, entry2, null, pi2);
    assertThat(recipients2, containsInAnyOrder(pi1, pi2, u1));
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
    assertThat(recipients, containsInAnyOrder(pi, otherGrpMember));
    assertEquals(2, recipients.size());

    // self and pi can review
    Set<User> recipients2 =
        policy.findPotentialTargetsFor(MessageType.REQUEST_RECORD_REVIEW, sd1, null, docOwner);
    assertThat(recipients2, containsInAnyOrder(pi, docOwner));
    assertEquals(2, recipients2.size());

    // if search term is passed, only matching user is returned
    Set<User> matchingRecipients =
        policy.findPotentialTargetsFor(
            MessageType.REQUEST_RECORD_REVIEW, sd1, pi.getUsername().substring(0, 4), docOwner);
    assertEquals(1, matchingRecipients.size());
    assertEquals(pi, matchingRecipients.toArray()[0]);

    // now we give the document edit permission
    RecordGroupSharing rgs = sharingMgr.getSharedRecordsForUser(docOwner).get(0);
    sharingMgr.updatePermissionForRecord(rgs.getId(), "WRITE", docOwner.getUsername());

    Set<User> recipients4 =
        policy.findPotentialTargetsFor(MessageType.REQUEST_RECORD_REVIEW, sd1, null, docOwner);
    assertFalse(recipients4.contains(notInGroup));
    assertEquals(3, recipients4.size());
    assertThat(recipients4, containsInAnyOrder(pi, docOwner, otherGrpMember));
    RSpaceTestUtils.logout();

    // now let's disable other - shouldn't appear on potential targets list
    otherGrpMember.setEnabled(false);
    otherGrpMember = userDao.save(otherGrpMember);
    logoutAndLoginAs(docOwner);
    Set<User> recipients5 =
        policy.findPotentialTargetsFor(MessageType.REQUEST_RECORD_REVIEW, sd1, null, docOwner);
    assertEquals(2, recipients5.size());
    assertThat(recipients5, not(contains(otherGrpMember)));

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
    assertThat(recipients6, contains(pi2));
  }

  private void initUsersWithEmptyContent(User... users) throws IllegalAddChildOperation {
    for (User u : users) {
      initialiseContentWithEmptyContent(u);
    }
  }
}
