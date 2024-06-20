package com.researchspace.service;

import static com.researchspace.core.testutil.CoreTestUtils.assertExceptionThrown;
import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static com.researchspace.core.util.TransformerUtils.toSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.Constants;
import com.researchspace.comms.CommunicationTargetFinderPolicy;
import com.researchspace.core.testutil.StringAppenderForTestLogging;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SortOrder;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.dao.CollaborationGroupTrackerDao;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.UserPreference;
import com.researchspace.model.comms.Communication;
import com.researchspace.model.comms.CommunicationStatus;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.CreateGroupMessageOrRequestCreationConfiguration;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.comms.MsgOrReqstCreationCfg;
import com.researchspace.model.comms.Notification;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.dtos.NotificationStatus;
import com.researchspace.model.dtos.ShareConfigElement;
import com.researchspace.model.preference.Preference;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.impl.DevBroadCaster;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang.time.DateUtils;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.UnexpectedRollbackException;

public class CommunicationManagerIT extends RealTransactionSpringTestBase {

  @Autowired private IMessageAndNotificationTracker tracker;
  @Autowired private CollaborationGroupTrackerDao collabGrpTrackerDao;

  private static final int NAME_LENGTH = 10;

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testCancelRequest() throws Exception {
    StructuredDocument record = setUpLoginAsPIUserAndCreateADocument();
    User other = createAndSaveUser(getRandomName(NAME_LENGTH));
    initUser(other);
    createGroupForUsersWithDefaultPi(piUser, other);
    setUpUserToGetRequestUpdateNotifications(piUser);
    setUpUserToGetRequestUpdateNotifications(other);
    MsgOrReqstCreationCfg config = new MsgOrReqstCreationCfg();
    config.setMessageType(MessageType.REQUEST_RECORD_REVIEW);
    config.setRecordId(record.getId());
    config.setOptionalMessage("msg");
    MessageOrRequest mor =
        reqCreateMgr.createRequest(config, piUser.getUsername(), createUserSet(other), null, null);

    // rspac-2264, unauthorised user can't cancel
    assertExceptionThrown(
        () -> communicationMgr.cancelRequest(other.getUsername(), mor.getId(), false),
        AuthorizationException.class);
    communicationMgr.cancelRequest(piUser.getUsername(), mor.getId(), false);
    // assert is notified that request was cancelled
    assertEquals(1, getNewNotificationCountForUser(other));
    // and now has no active request
    assertEquals(0, getActiveRequestCountForUser(other));
    // the originator should not be notified that they've cancelled their own request
    assertEquals(0, getNewNotificationCountForUser(piUser));
  }

  @Test
  public void testCancelRecipient() {

    User piUser = createAndSaveUser(getRandomName(NAME_LENGTH), Constants.PI_ROLE);
    User existingUser = createAndSaveUser(getRandomName(NAME_LENGTH), Constants.USER_ROLE);
    User existingUser2 = createAndSaveUser(getRandomName(NAME_LENGTH), Constants.USER_ROLE);
    initUsers(piUser, existingUser, existingUser2);
    Group newGroup = createGroupForUsers(piUser, piUser.getUsername(), "", piUser);

    newGroup.setMemberString(
        Arrays.asList(existingUser.getUsername(), existingUser2.getUsername()));

    logoutAndLoginAs(piUser);

    MsgOrReqstCreationCfg cgf = new MsgOrReqstCreationCfg(piUser, permissionUtils);
    cgf.setGroupId(newGroup.getId());
    cgf.setMessageType(MessageType.REQUEST_JOIN_LAB_GROUP);
    MessageOrRequest mor =
        reqCreateMgr.createRequest(
            cgf, piUser.getUsername(), new HashSet<String>(newGroup.getMemberString()), null, null);

    // Assert that request is active
    assertEquals(1, getActiveRequestCountForUser(existingUser));
    assertEquals(1, getActiveRequestCountForUser(existingUser2));

    Long recipientId = null;
    for (CommunicationTarget ct : mor.getRecipients()) {
      if (ct.getRecipient().equals(existingUser)) {
        recipientId = ct.getId();
      }
    }
    communicationMgr.cancelRecipient(piUser.getUsername(), mor.getId(), recipientId);
    // Assert existingUser has no active request, but existingUser2
    assertEquals(0, getActiveRequestCountForUser(existingUser));
    assertEquals(1, getActiveRequestCountForUser(existingUser2));

    // the originator should not be notified that they've cancelled their own request!
    assertEquals(0, getNewNotificationCountForUser(piUser));
  }

  @Test
  public void testCancelSharedRecordRequest() throws Exception {

    User source = createAndSaveUser(getRandomName(NAME_LENGTH), Constants.USER_ROLE);
    User target = createAndSaveUser(getRandomName(NAME_LENGTH), Constants.USER_ROLE);
    initUsers(target);

    Folder rootFolder = initUser(source);
    logoutAndLoginAs(source);
    RSForm t = createAnyForm(source);
    StructuredDocument doc = createDocumentInFolder(rootFolder, t, source);
    MessageOrRequest mor = sendSharedRecordRequest(source, doc, "read", target);

    Communication mor2 = communicationMgr.get(mor.getId());
    assertNotNull(mor2.getCreationTime());
    assertEquals(1, tracker.getSpecialMessageCountFor(target.getId()).intValue());

    communicationMgr.cancelSharedRecordRequest(source.getUsername(), mor.getId());

    // Assert existingUser has no active request, but existingUser2
    assertEquals(0, getActiveRequestCountForUser(target));

    // the originator should not be notified that they've cancelled their own request!
    assertEquals(0, getNewNotificationCountForUser(source));
  }

  @Test
  public void testCreateExternalGroupShareREquest() throws Exception {
    User pi1 = createAndSaveUser("pi1" + getRandomName(NAME_LENGTH), Constants.PI_ROLE);
    User pi2 = createAndSaveUser("pi2" + getRandomName(NAME_LENGTH), Constants.PI_ROLE);
    initUsers(pi1, pi2);

    // two groups
    createGroupForUsers(piUser, pi1.getUsername(), "", pi1);
    createGroupForUsers(piUser, pi2.getUsername(), "", pi2);

    logoutAndLoginAs(pi1);
    // send a request from 1 pi to another to create a CollabGroup
    MsgOrReqstCreationCfg config = new MsgOrReqstCreationCfg();
    config.setMessageType(MessageType.REQUEST_EXTERNAL_SHARE);
    config.setOptionalMessage("any");
    MessageOrRequest mor =
        reqCreateMgr.createRequest(config, pi1.getUsername(), toSet(pi2.getUsername()), null, null);

    doInTransaction(
        () -> {
          assertNotNull(collabGrpTrackerDao.getByRequestId(mor));
        });

    openTransaction();
    Folder collbFolder = folderDao.getCollaborationGroupsSharedFolderForUser(pi2);
    commitTransaction();
    ISearchResults<BaseRecord> res =
        recordMgr.listFolderRecords(collbFolder.getId(), DEFAULT_RECORD_PAGINATION);
    assertEquals(0, res.getTotalHits().intValue());
    // now let's update affirmative:
    logoutAndLoginAs(pi2);
    reqUpdateMgr.updateStatus(
        pi2.getUsername(), CommunicationStatus.COMPLETED, mor.getId(), "updated");
    assertEquals(0, res.getTotalHits().intValue());
    ISearchResults<BaseRecord> res2 =
        recordMgr.listFolderRecords(collbFolder.getId(), DEFAULT_RECORD_PAGINATION);
    assertEquals(1, res2.getTotalHits().intValue());

    User p1updated = userMgr.get(pi1.getId());
    User p2updated = userMgr.get(pi2.getId());
    assertEquals(2, p1updated.getGroups().size());
    assertEquals(2, p2updated.getGroups().size());

    // check that PIs are PIs in their collab groups as well
    Set<Group> allGroups = p1updated.getGroups();
    Long collabGrpId = null;
    for (Group g : allGroups) {
      if (g.isCollaborationGroup()) {
        collabGrpId = g.getId();
      }
      assertTrue(g.getRoleForUser(pi1).equals(RoleInGroup.PI));
    }
    // check ththat PIs are PIs in their collab groups as well
    Set<Group> allGroups2 = p2updated.getGroups();
    for (Group g : allGroups2) {
      assertTrue(g.getRoleForUser(pi2).equals(RoleInGroup.PI));
    }

    // now delete collab group, is now deleted
    User sysadmin = logoutAndLoginAsSysAdmin();
    grpMgr.removeGroup(collabGrpId, sysadmin);
    User p1updated1 = userMgr.get(pi1.getId());
    User p2updated1 = userMgr.get(pi2.getId());
    assertEquals(1, p1updated1.getGroups().size());
    assertEquals(1, p2updated1.getGroups().size());
  }

  @Test
  public void testCreateRequest() throws Exception {
    StructuredDocument record = setUpLoginAsPIUserAndCreateADocument();
    User other = createAndSaveUser(getRandomName(NAME_LENGTH));
    openTransaction();
    MsgOrReqstCreationCfg config = new MsgOrReqstCreationCfg();
    config.setMessageType(MessageType.REQUEST_RECORD_REVIEW);
    config.setOptionalMessage("test Message");
    config.setRecordId(record.getId());
    MessageOrRequest mOr =
        reqCreateMgr.createRequest(config, piUser.getUsername(), createUserSet(other), null, null);
    commitTransaction();

    setUpUserToGetRequestUpdateNotifications(piUser);

    Communication mOr2 = communicationMgr.get(mOr.getId());
    assertNotNull(mOr2.getCreationTime());
    assertTrue(tracker.userHasActiveMessages(other.getId()));

    doInTransaction(
        () -> {
          reqUpdateMgr.updateStatus(
              other.getUsername(), CommunicationStatus.COMPLETED, mOr2.getId(), "completed");
        });

    doInTransaction(
        () -> {
          ISearchResults<Notification> res =
              communicationMgr.getNewNotificationsForUser(
                  piUser.getUsername(),
                  PaginationCriteria.createDefaultForClass(CommunicationTarget.class));
          assertEquals(1, res.getTotalHits().intValue());
        });
  }

  @Test
  public void testCreateAndDeleteNotification() throws Exception {
    StructuredDocument record = setUpLoginAsPIUserAndCreateADocument();
    User other = createAndSaveUser(getRandomName(NAME_LENGTH));

    Notification mOr =
        communicationMgr.doCreateNotification(
            NotificationType.NOTIFICATION_DOCUMENT_SHARED,
            piUser.getUsername(),
            record,
            createUsersSet(other),
            "test Message",
            "systemMessage");

    Communication mOr2 = communicationMgr.get(mOr.getId());
    assertNotNull(mOr2.getCreationTime());
    assertTrue(tracker.userHasNewNotifications(other.getId()));
    assertFalse(tracker.userHasNewNotifications(piUser.getId()));

    Date future = DateUtils.addDays(new Date(), 5);
    communicationMgr.markAllNotificationsAsRead(other.getUsername(), future);
    // at least 1 communication was deleted, possibly more ( depending on
    // test ordering)
    assertTrue(communicationMgr.deleteReadNotificationsOlderThanDate(future) >= 1);
  }

  @Test
  public void testGetNewMessages() throws Exception {
    StructuredDocument record = setUpLoginAsPIUserAndCreateADocument();
    User other = createAndSaveUser(getRandomName(NAME_LENGTH));
    openTransaction();
    MsgOrReqstCreationCfg config = new MsgOrReqstCreationCfg();
    config.setMessageType(MessageType.SIMPLE_MESSAGE);
    config.setOptionalMessage("test Message");
    config.setRecordId(record.getId());
    // MessageOrRequest mOr =
    reqCreateMgr.createRequest(config, piUser.getUsername(), createUserSet(other), null, null);
    commitTransaction();

    doInTransaction(
        () -> {
          ISearchResults<MessageOrRequest> mOr2 =
              communicationMgr.getActiveMessagesAndRequestsForUserTarget(
                  other.getUsername(),
                  PaginationCriteria.createDefaultForClass(CommunicationTarget.class));
          assertEquals(1, mOr2.getHits().intValue());
        });
  }

  /**
   * Reference: RSPAC-438
   *
   * @throws Exception
   */
  @Test
  public void testGetNewMessagesOrderedByCreationTime() throws Exception {
    User other = createAndSaveUser(getRandomName(NAME_LENGTH));
    initUser(other);
    setUpUserToGetRequestUpdateNotifications(piUser);

    logoutAndLoginAs(piUser);
    for (int i = 0; i < 10; i++) {
      sendSimpleMessage(piUser, "test message", other);
    }

    logoutAndLoginAs(other);
    PaginationCriteria<CommunicationTarget> pg =
        PaginationCriteria.createDefaultForClass(CommunicationTarget.class);
    pg.setOrderBy("communication.creationTime");
    pg.setSortOrder(SortOrder.ASC);
    ISearchResults<MessageOrRequest> mors =
        communicationMgr.getActiveMessagesAndRequestsForUserTarget(other.getUsername(), pg);
    assertEquals(10, mors.getHits().intValue());
    assertResultsOrderedByCreationTimeASC(mors);

    pg.setSortOrder(SortOrder.DESC);
    ISearchResults<MessageOrRequest> mors2 =
        communicationMgr.getActiveMessagesAndRequestsForUserTarget(other.getUsername(), pg);

    assertEquals(10, mors2.getHits().intValue());
    assertResultsOrderedByCreationTimeDESC(mors2);
  }

  @Test
  public void testMessageExchange() throws Exception {

    setUpLoginAsPIUserAndCreateADocument();
    User other = createAndSaveUser(getRandomName(NAME_LENGTH));
    User yetAnother = createAndSaveUser(getRandomName(NAME_LENGTH));
    User maliciousMike = createAndSaveUser(getRandomName(NAME_LENGTH));
    initUsers(other, yetAnother, maliciousMike);

    createGroupForUsersWithDefaultPi(piUser, other, yetAnother);
    setUpUserToGetRequestUpdateNotifications(piUser);

    Communication comm = sendSimpleMessage(piUser, "message", other, yetAnother);
    Thread.sleep(1000);
    // other now replies to User; this creates a new message
    reqUpdateMgr.replyToMessage(other.getUsername(), comm.getId(), "A reply form other");
    // rspac2264:
    assertExceptionThrown(
        () ->
            reqUpdateMgr.replyToMessage(
                maliciousMike.getUsername(), comm.getId(), "Reply from malicious Mike not allowed"),
        AuthorizationException.class);

    Thread.sleep(1000);

    // yet another now replies to User; this creates a new message
    doInTransaction(
        () -> {
          reqUpdateMgr.replyToMessage(
              yetAnother.getUsername(), comm.getId(), "A reply form yetanother");
        });
    // user has been sent a reply and check characteristics of new message
    assertEquals(2, getActiveRequestCountForUser(piUser));
    MessageOrRequest mor = getLatestActiveRequestorMessageForUser(piUser);
    assertNotNull(mor.getPrevious());
    assertEquals(other, mor.getPrevious().getOriginator());
    assertTrue(mor.isLatest());
    assertFalse(mor.getPrevious().isLatest());

    // now yet another loads his active messages:
    ISearchResults<MessageOrRequest> curMsges = searchDBForRequests(yetAnother);

    // check one is marked as replied
    for (MessageOrRequest mor2 : curMsges.getResults()) {
      for (CommunicationTarget ct : mor2.getRecipients()) {
        if (ct.getRecipient().equals(yetAnother) && mor2.getOriginator().equals(piUser)) {
          assertEquals(CommunicationStatus.REPLIED, ct.getStatus());
        }
      }
    }
  }

  @Test
  public void testUpdateStatus() throws Exception {
    StructuredDocument doc = setUpLoginAsPIUserAndCreateADocument();
    User other = createAndSaveUser(getRandomName(NAME_LENGTH));
    User maliciousMike = createAndSaveUser(getRandomName(NAME_LENGTH));
    initUsers(other, maliciousMike);
    createGroupForUsersWithDefaultPi(piUser, other);
    setUpUserToGetRequestUpdateNotifications(piUser);
    openTransaction();
    // create a simple message FROM user To other
    MsgOrReqstCreationCfg config = new MsgOrReqstCreationCfg();
    config.setMessageType(MessageType.SIMPLE_MESSAGE);
    config.setOptionalMessage("msg");
    Communication comm =
        reqCreateMgr.createRequest(config, piUser.getUsername(), createUserSet(other), null, null);

    commitTransaction();
    // other dismisses the message
    try {
      openTransaction();
      assertExceptionThrown(
          () ->
              reqUpdateMgr.updateStatus(
                  maliciousMike.getUsername(),
                  CommunicationStatus.COMPLETED,
                  comm.getId(),
                  "updated"),
          AuthorizationException.class);
      commitTransaction();
    } catch (UnexpectedRollbackException e) {
      // expected
    }
    openTransaction();
    // check originator does not have a message listed as an active request
    assertEquals(Long.valueOf(0), getSentRequestCountForUser(piUser));
    // rspac-2264
    reqUpdateMgr.updateStatus(
        other.getUsername(), CommunicationStatus.COMPLETED, comm.getId(), "updated");
    commitTransaction();
    // assert request Sender is NOT notified that someone dismisses a
    // message
    doInTransaction(
        () -> {
          assertEquals(0, getNewNotificationCountForUser(piUser));
        });

    // now let's make a request FROM user TO other; this is stateful:
    openTransaction();
    MsgOrReqstCreationCfg config2 = new MsgOrReqstCreationCfg();
    config2.setMessageType(MessageType.REQUEST_RECORD_REVIEW);
    config2.setOptionalMessage("msg");
    config2.setRecordId(doc.getId());
    Communication comm2 =
        reqCreateMgr.createRequest(config2, piUser.getUsername(), createUserSet(other), null, null);
    commitTransaction();
    // other accepts the request
    // openTransaction();
    // check originator DOES have a message listed as an active request
    assertEquals(Long.valueOf(1), getSentRequestCountForUser(piUser));
    assertEquals(1, getActiveRequestCountForUser(other));
    reqUpdateMgr.updateStatus(
        other.getUsername(), CommunicationStatus.ACCEPTED, comm2.getId(), "accepted");
    // commitTransaction();
    // check that
    // 1) User gets notification
    assertEquals(1, getNewNotificationCountForUser(piUser));
    // and 2) that other still sees an active request:
    assertEquals(1, getActiveRequestCountForUser(other));
    // assertEquals(CommunicationStatus.ACCEPTED,getStatusOfLatestRequestForUSer(other));

    // now we'll close the request:
    doInTransaction(
        () -> {
          reqUpdateMgr.updateStatus(
              other.getUsername(), CommunicationStatus.COMPLETED, comm2.getId(), "accepted");
        });

    // 1) User gets another notification
    assertEquals(2, getNewNotificationCountForUser(piUser));
    // other no longer sees an active request, since he's just marked it as
    // completed:
    assertEquals(0, getActiveRequestCountForUser(other));

    // check originator DOES NOT have a message listed as an active reques,
    // now that it's been closed
    assertEquals(Long.valueOf(0), getSentRequestCountForUser(piUser));
  }

  @Test
  public void testNotificationsOnlySentOnce() throws Exception {
    StructuredDocument doc = setUpLoginAsPIUserAndCreateADocument();
    User other = createAndSaveUser(getRandomName(NAME_LENGTH));
    initUser(other);
    createRootFolderForUsers(other);
    Group grp1 = createGroupForUsersWithDefaultPi(piUser, other);
    ShareConfigElement gsce = new ShareConfigElement(grp1.getId(), "write");
    ShareConfigElement[] gsceArray = new ShareConfigElement[] {gsce};

    // we need to set preference that we want to get these notifications
    openTransaction();
    // refresh to avoid locking exception
    other = userMgr.getUserByUsername(other.getUsername());
    other.setPreference(
        new UserPreference(
            Preference.NOTIFICATION_DOCUMENT_EDITED_PREF, other, Boolean.TRUE.toString()));
    other.setPreference(
        new UserPreference(
            Preference.NOTIFICATION_DOCUMENT_SHARED_PREF, other, Boolean.TRUE.toString()));
    userMgr.save(other);
    commitTransaction();

    doInTransaction(
        () -> {
          sharingMgr.shareRecord(piUser, doc.getId(), gsceArray);
        });

    doInTransaction(
        () -> {
          recordMgr.requestRecordEdit(doc.getId(), piUser, anySessionTracker());
          recordMgr.saveStructuredDocument(doc.getId(), piUser.getUsername(), true, null);
        });

    openTransaction();
    int notificationCount = getNewNotificationCountForUser(other);
    assertTrue(notificationCount > 0); // ensure opt-in notifications are working
    commitTransaction();

    openTransaction();
    recordMgr.requestRecordEdit(doc.getId(), piUser, anySessionTracker());
    recordMgr.saveStructuredDocument(doc.getId(), piUser.getUsername(), true, null);
    commitTransaction();
    // 2nd save does not send new notification
    assertEquals(notificationCount, getNewNotificationCountForUser(other));

    // marking as read removes all
    Thread.sleep(1000); // 1s added as possible fix to random failing of this test
    communicationMgr.markAllNotificationsAsRead(other.getUsername(), new Date());
    assertFalse(tracker.userHasNewNotifications(other.getId()));
    assertEquals(0, getNewNotificationCountForUser(other));

    // but another save triggers notify
    recordMgr.requestRecordEdit(doc.getId(), piUser, anySessionTracker());
    recordMgr.saveStructuredDocument(doc.getId(), piUser.getUsername(), true, null);
    assertEquals(1, getNewNotificationCountForUser(other));
  }

  @Test
  public void testCalculateRecipients() throws Exception {

    StructuredDocument record = setUpLoginAsPIUserAndCreateADocument();
    User other = createAndSaveUser("otherA");
    User other2 = createAndSaveUser("otherB");
    initUser(other);
    initUser(other2);

    // test all user policy
    final int expectedUsersCount = userMgr.getAll().size() - 1; // anonymous is excluded
    Set<User> recips =
        communicationMgr.getPotentialRecipientsOfRequest(
            null,
            MessageType.REQUEST_RECORD_REVIEW,
            piUser.getUsername(),
            null,
            (CommunicationTargetFinderPolicy) applicationContext.getBean("allUserPolicy"));
    assertEquals(expectedUsersCount, recips.size());

    createRootFolderForUsers(other, other2);
    Group grp1 = createGroupForUsersWithDefaultPi(piUser, other);
    createGroupForUsersWithDefaultPi(piUser, other2);

    ShareConfigElement gsce = new ShareConfigElement(grp1.getId(), "write");
    ShareConfigElement[] gsceArray = new ShareConfigElement[] {gsce};

    sharingMgr.shareRecord(piUser, record.getId(), gsceArray);

    recips =
        communicationMgr.getPotentialRecipientsOfRequest(
            record, MessageType.REQUEST_RECORD_REVIEW, piUser.getUsername(), null, null);

    // we can send message about this record to other groups menbers of the
    // record's owner + the owner themselves
    assertEquals(2, recips.size());

    // now let's change group permission on record to read only - now none
    // else in the group can actually make changes to a record, so shouldn't
    // be suggested recipients

    sharingMgr.unshareRecord(piUser, record.getId(), gsceArray);

    gsce.setOperation("read");
    sharingMgr.shareRecord(piUser, record.getId(), new ShareConfigElement[] {gsce});

    Set<User> recips3 =
        communicationMgr.getPotentialRecipientsOfRequest(
            record, MessageType.REQUEST_RECORD_REVIEW, piUser.getUsername(), null, null);

    assertEquals(1, recips3.size());

    // this is a general message, we can send it to anyone in any of our groups.
    Set<User> recips2 =
        communicationMgr.getPotentialRecipientsOfRequest(
            null, MessageType.SIMPLE_MESSAGE, piUser.getUsername(), null, null);

    assertEquals(2, recips2.size());
  }

  @Test
  public void testSystemNotifyCanOptionallyBroadcast() {
    User sender = createAndSaveUser(getRandomAlphabeticString("sender"));
    logoutAndLoginAs(sender);
    // check that broadcasting is configuratble by boolean
    communicationMgr.systemNotify(
        NotificationType.PROCESS_FAILED, "msg", sender.getUsername(), false);
    StringAppenderForTestLogging log = configureTestLogger(DevBroadCaster.getLogger());
    assertTrue(log.logContents.isEmpty());

    communicationMgr.systemNotify(
        NotificationType.PROCESS_FAILED, "msg", sender.getUsername(), true);
    assertFalse(log.logContents.isEmpty());
  }

  @Test
  public void sendJoinGroupRequest() {
    User piUser = createAndSaveUser(getRandomName(NAME_LENGTH), Constants.PI_ROLE);
    User existingUser = createAndSaveUser(getRandomName(NAME_LENGTH), Constants.USER_ROLE);
    User existingUser2 = createAndSaveUser(getRandomName(NAME_LENGTH), Constants.USER_ROLE);
    initUsers(piUser, existingUser, existingUser2);

    Group newGroup = createGroupForUsers(piUser, piUser.getUsername(), "", piUser);
    newGroup.setMemberString(
        Arrays.asList(existingUser.getUsername(), existingUser2.getUsername()));

    logoutAndLoginAs(piUser);

    MsgOrReqstCreationCfg cgf = new MsgOrReqstCreationCfg(piUser, permissionUtils);
    cgf.setGroupId(newGroup.getId());
    cgf.setMessageType(MessageType.REQUEST_JOIN_LAB_GROUP);
    MessageOrRequest mor =
        reqCreateMgr.createRequest(
            cgf, piUser.getUsername(), new HashSet<String>(newGroup.getMemberString()), null, null);

    Communication mor2 = communicationMgr.get(mor.getId());
    assertNotNull(mor2.getCreationTime());
    assertEquals(1, tracker.getSpecialMessageCountFor(existingUser.getId()).intValue());
    assertEquals(1, tracker.getSpecialMessageCountFor(existingUser2.getId()).intValue());

    // assert sysadmin can also invite new people RSPAC-374
    User newInvitee = createAndSaveUser(getRandomName(NAME_LENGTH));
    initUser(newInvitee);
    User sysadmin = logoutAndLoginAsSysAdmin();
    MsgOrReqstCreationCfg cgf2 = new MsgOrReqstCreationCfg(sysadmin, permissionUtils);
    cgf2.setGroupId(newGroup.getId());
    cgf2.setMessageType(MessageType.REQUEST_JOIN_LAB_GROUP);
    MessageOrRequest mor3 =
        reqCreateMgr.createRequest(
            cgf2,
            sysadmin.getUsername(),
            TransformerUtils.toSet(newInvitee.getUsername()),
            null,
            null);
    assertNotNull(mor3);
  }

  @Test
  public void sendCreateGroupRequest() {

    User source = createAndSaveUser(getRandomName(NAME_LENGTH), Constants.PI_ROLE);
    User target = createAndSaveUser(getRandomName(NAME_LENGTH), Constants.USER_ROLE);
    User existingUser = createAndSaveUser(getRandomName(NAME_LENGTH), Constants.USER_ROLE);
    initUsers(source, target, existingUser);

    logoutAndLoginAs(source);

    CreateGroupMessageOrRequestCreationConfiguration cgf =
        new CreateGroupMessageOrRequestCreationConfiguration(source, permissionUtils);
    cgf.setCreator(source);
    cgf.setTarget(target);
    cgf.setEmails(Arrays.asList(existingUser.getEmail()));
    cgf.setGroupName("NewGroup");
    cgf.setMessageType(MessageType.REQUEST_CREATE_LAB_GROUP);
    MessageOrRequest mor =
        reqCreateMgr.createRequest(
            cgf,
            source.getUsername(),
            new HashSet<String>(Arrays.asList(target.getUsername())),
            null,
            null);

    Communication mor2 = communicationMgr.get(mor.getId());
    assertNotNull(mor2.getCreationTime());
    assertEquals(1, tracker.getSpecialMessageCountFor(target.getId()).intValue());
  }

  @Test
  public void sendShareRecordRequest() throws Exception {

    User source = createAndSaveUser(getRandomName(NAME_LENGTH), Constants.PI_ROLE);
    User target = createAndSaveUser(getRandomName(NAME_LENGTH), Constants.USER_ROLE);
    initUsers(target);

    Folder rootFolder = initUser(source);
    logoutAndLoginAs(source);
    RSForm t = createAnyForm(source);
    StructuredDocument doc = createDocumentInFolder(rootFolder, t, source);
    MessageOrRequest mor = sendSharedRecordRequest(source, doc, "read", target);

    Communication mor2 = communicationMgr.get(mor.getId());
    assertNotNull(mor2.getCreationTime());
    assertEquals(1, tracker.getSpecialMessageCountFor(target.getId()).intValue());
  }

  @Test
  public void sendShareRecordRequestAndAccept() throws Exception {

    User source = createAndSaveUser(getRandomName(NAME_LENGTH), Constants.PI_ROLE);
    User target = createAndSaveUser(getRandomName(NAME_LENGTH), Constants.USER_ROLE);
    initUsers(target);

    Folder rootFolder = initUser(source);
    logoutAndLoginAs(source);
    setUpUserToGetRequestUpdateNotifications(source);

    RSForm t = createAnyForm(source);
    StructuredDocument doc = createDocumentInFolder(rootFolder, t, source);
    MessageOrRequest mor = sendSharedRecordRequest(source, doc, "read", target);

    Communication mor2 = communicationMgr.get(mor.getId());
    assertNotNull(mor2.getCreationTime());
    assertEquals(1, tracker.getSpecialMessageCountFor(target.getId()).intValue());

    openTransaction();
    assertEquals(0, getNewNotificationCountForUser(source));
    commitTransaction();

    openTransaction();
    assertEquals(1, getActiveRequestCountForUser(target));
    reqUpdateMgr.updateStatus(
        target.getUsername(), CommunicationStatus.ACCEPTED, mor2.getId(), "accepted");
    commitTransaction();

    assertEquals(1, getNewNotificationCountForUser(source));
  }

  @Test
  public void sendShareRecordRequestAndReject() throws Exception {

    User source = createAndSaveUser(getRandomName(NAME_LENGTH), Constants.PI_ROLE);
    User target = createAndSaveUser(getRandomName(NAME_LENGTH), Constants.USER_ROLE);
    initUsers(source, target);

    logoutAndLoginAs(source);
    setUpUserToGetRequestUpdateNotifications(source);
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(source, "any");
    MessageOrRequest mor = sendSharedRecordRequest(source, doc, "read", target);

    Communication mor2 = communicationMgr.get(mor.getId());
    assertNotNull(mor2.getCreationTime());
    assertEquals(1, tracker.getSpecialMessageCountFor(target.getId()).intValue());

    assertEquals(0, getNewNotificationCountForUser(source));
    assertEquals(1, getActiveRequestCountForUser(target));
    logoutAndLoginAs(target);
    reqUpdateMgr.updateStatus(
        target.getUsername(), CommunicationStatus.REJECTED, mor2.getId(), "rejected");
    assertEquals(1, getNewNotificationCountForUser(source));
  }

  /**
   * Reference: RSPAC-438
   *
   * @throws Exception
   */
  @Test
  public void testNewNotifications() throws Exception {

    User source = createAndSaveUser(getRandomName(NAME_LENGTH), Constants.PI_ROLE);
    User target = createAndSaveUser(getRandomName(NAME_LENGTH), Constants.USER_ROLE);
    initUsers(target);

    Folder rootFolder = initUser(source);
    logoutAndLoginAs(source);
    setUpUserToGetRequestUpdateNotifications(source);

    List<Communication> mors = new ArrayList<Communication>();
    for (int i = 0; i < 10; i++) {
      RSForm t = createAnyForm(source);
      StructuredDocument doc = createDocumentInFolder(rootFolder, t, source);
      MessageOrRequest mor = sendSharedRecordRequest(source, doc, "read", target);
      Communication sharedMOR = communicationMgr.get(mor.getId());
      assertNotNull(sharedMOR.getCreationTime());
      assertEquals(i + 1, tracker.getSpecialMessageCountFor(target.getId()).intValue());
      mors.add(sharedMOR);
    }

    openTransaction();
    assertEquals(0, getNewNotificationCountForUser(source));
    commitTransaction();

    openTransaction();
    assertEquals(10, getActiveRequestCountForUser(target));
    commitTransaction();

    logoutAndLoginAs(target);
    setUpUserToGetRequestUpdateNotifications(target);

    for (Communication mor : mors) {
      reqUpdateMgr.updateStatus(
          target.getUsername(), CommunicationStatus.ACCEPTED, mor.getId(), "accepted");
    }

    logoutAndLoginAs(source);
    assertEquals(10, getNewNotificationCountForUser(source));

    ISearchResults<Notification> notifications =
        communicationMgr.getNewNotificationsForUser(
            source.getUsername(),
            PaginationCriteria.createDefaultForClass(CommunicationTarget.class));
    assertEquals(10, notifications.getTotalHits().intValue());
    assertEquals(10, notifications.getHits().intValue());
  }

  /**
   * Reference: RSPAC-438
   *
   * @throws Exception
   */
  @Test
  public void testNewNotificationsOrdered() throws Exception {

    User source = createAndSaveUser(getRandomName(NAME_LENGTH), Constants.PI_ROLE);
    User target = createAndSaveUser(getRandomName(NAME_LENGTH), Constants.USER_ROLE);
    initUsers(target);

    Folder rootFolder = initUser(source);
    logoutAndLoginAs(source);
    setUpUserToGetRequestUpdateNotifications(source);

    List<Communication> mors = new ArrayList<Communication>();
    for (int i = 0; i < 10; i++) {
      RSForm t = createAnyForm(source);
      StructuredDocument doc = createDocumentInFolder(rootFolder, t, source);
      MessageOrRequest mor = sendSharedRecordRequest(source, doc, "read", target);
      Communication sharedMOR = communicationMgr.get(mor.getId());
      assertNotNull(sharedMOR.getCreationTime());
      assertEquals(i + 1, tracker.getSpecialMessageCountFor(target.getId()).intValue());
      mors.add(sharedMOR);
    }

    // The target will accept every share record request generating new notifications in source
    // account.
    logoutAndLoginAs(target);
    setUpUserToGetRequestUpdateNotifications(target);
    for (Communication mor : mors) {
      reqUpdateMgr.updateStatus(
          target.getUsername(), CommunicationStatus.ACCEPTED, mor.getId(), "accepted");
    }

    logoutAndLoginAs(source);
    PaginationCriteria<CommunicationTarget> pg =
        PaginationCriteria.createDefaultForClass(CommunicationTarget.class);
    pg.setOrderBy("communication.creationTime");
    pg.setSortOrder(SortOrder.ASC);
    ISearchResults<Notification> notifications =
        communicationMgr.getNewNotificationsForUser(source.getUsername(), pg);
    assertEquals(10, notifications.getTotalHits().intValue());
    assertEquals(10, notifications.getHits().intValue());
    assertResultsOrderedByCreationTimeASC(notifications);

    pg.setSortOrder(SortOrder.DESC);
    notifications = communicationMgr.getNewNotificationsForUser(source.getUsername(), pg);
    assertEquals(10, notifications.getTotalHits().intValue());
    assertEquals(10, notifications.getHits().intValue());
    assertResultsOrderedByCreationTimeDESC(notifications);
  }

  /**
   * Reference RSPAC-438
   *
   * @throws Exception
   */
  @Test
  public void testSentRequests() throws Exception {

    User source = createAndSaveUser(getRandomName(NAME_LENGTH), Constants.PI_ROLE);
    User target = createAndSaveUser(getRandomName(NAME_LENGTH), Constants.USER_ROLE);
    initUsers(target);

    Folder rootFolder = initUser(source);
    logoutAndLoginAs(source);
    setUpUserToGetRequestUpdateNotifications(source);
    RSForm t = createAnyForm(source);
    for (int i = 0; i < 10; i++) {
      StructuredDocument doc = createDocumentInFolder(rootFolder, t, source);
      sendSharedRecordRequest(source, doc, "read", target);
    }

    assertEquals(10, getSentRequestCountForUser(source).intValue());
  }

  /**
   * Reference RSPAC-438
   *
   * @throws Exception
   */
  @Test
  public void testSentRequestsOrdered() throws Exception {

    User source = createAndSaveUser(getRandomName(NAME_LENGTH), Constants.PI_ROLE);
    User target = createAndSaveUser(getRandomName(NAME_LENGTH), Constants.USER_ROLE);
    initUsers(target);

    Folder rootFolder = initUser(source);
    logoutAndLoginAs(source);
    setUpUserToGetRequestUpdateNotifications(source);
    RSForm t = createAnyForm(source);
    for (int i = 0; i < 10; i++) {
      StructuredDocument doc = createDocumentInFolder(rootFolder, t, source);
      sendSharedRecordRequest(source, doc, "read", target);
    }

    PaginationCriteria<MessageOrRequest> pg =
        PaginationCriteria.createDefaultForClass(MessageOrRequest.class);
    pg.setOrderBy("creationTime");
    pg.setSortOrder(SortOrder.ASC);
    ISearchResults<MessageOrRequest> sentRequests =
        communicationMgr.getSentRequests(source.getUsername(), pg);
    assertEquals(10, sentRequests.getTotalHits().intValue());
    assertEquals(10, sentRequests.getHits().intValue());
    assertResultsOrderedByCreationTimeASC(sentRequests);

    pg.setSortOrder(SortOrder.DESC);
    sentRequests = communicationMgr.getSentRequests(source.getUsername(), pg);
    assertEquals(10, sentRequests.getTotalHits().intValue());
    assertEquals(10, sentRequests.getHits().intValue());
    assertResultsOrderedByCreationTimeDESC(sentRequests);
  }

  /**
   * Reference RSPAC-438
   *
   * @throws Exception
   */
  @Test
  public void testSentAndReceivedSimpleMessagesOrdered() throws Exception {

    User source = createAndSaveUser(getRandomName(NAME_LENGTH), Constants.PI_ROLE);
    User target = createAndSaveUser(getRandomName(NAME_LENGTH), Constants.USER_ROLE);
    initUsers(target, source);

    logoutAndLoginAs(source);
    setUpUserToGetRequestUpdateNotifications(source);
    sendSimpleMessage(source, "This is a message from source", target);
    sendSimpleMessage(target, "This is a message from target", source);

    PaginationCriteria<CommunicationTarget> pg =
        PaginationCriteria.createDefaultForClass(CommunicationTarget.class);
    pg.setOrderBy("communication.creationTime");
    pg.setSortOrder(SortOrder.ASC);
    ISearchResults<MessageOrRequest> sentRequests =
        communicationMgr.getAllSentAndReceivedSimpleMessagesForUser(source.getUsername(), pg);
    assertEquals(2, sentRequests.getTotalHits().intValue());
    assertEquals(2, sentRequests.getHits().intValue());
    assertResultsOrderedByCreationTimeASC(sentRequests);

    pg.setSortOrder(SortOrder.DESC);
    sentRequests =
        communicationMgr.getAllSentAndReceivedSimpleMessagesForUser(source.getUsername(), pg);
    assertEquals(2, sentRequests.getTotalHits().intValue());
    assertEquals(2, sentRequests.getHits().intValue());
    assertResultsOrderedByCreationTimeDESC(sentRequests);
  }

  /**
   * Reference RSPAC-438
   *
   * @throws Exception
   */
  @Test
  public void testSentAndReceivedSimpleMessagesPaginated() throws Exception {

    User source = createAndSaveUser(getRandomName(NAME_LENGTH), Constants.PI_ROLE);
    User target = createAndSaveUser(getRandomName(NAME_LENGTH), Constants.USER_ROLE);
    initUsers(target, source);

    logoutAndLoginAs(source);
    setUpUserToGetRequestUpdateNotifications(source);
    for (int i = 0; i <= 5; i++) {
      sendSimpleMessage(source, "This is a message from source", target);
      sendSimpleMessage(target, "This is a message from target", source);
    }

    PaginationCriteria<CommunicationTarget> pg =
        PaginationCriteria.createDefaultForClass(CommunicationTarget.class);
    ISearchResults<MessageOrRequest> sentRequests =
        communicationMgr.getAllSentAndReceivedSimpleMessagesForUser(source.getUsername(), pg);
    assertEquals(12, sentRequests.getTotalHits().intValue());
    assertEquals(10, sentRequests.getHits().intValue());
  }

  /**
   * Reference RSPAC-349
   *
   * @throws Exception
   */
  @Test
  public void testNotificationStatus() throws Exception {

    User source = createAndSaveUser(getRandomName(NAME_LENGTH), Constants.PI_ROLE);
    User target = createAndSaveUser(getRandomName(NAME_LENGTH), Constants.USER_ROLE);
    initUsers(target, source);

    logoutAndLoginAs(source);
    setUpUserToGetRequestUpdateNotifications(source);
    // It will be treated as simple messages
    sendSimpleMessage(source, "This is a message from source", target);

    Folder rootFolder = folderMgr.getRootFolderForUser(source);
    RSForm t = createAnyForm(source);
    StructuredDocument doc = createDocumentInFolder(rootFolder, t, source);
    // It will be treated as special messages
    MessageOrRequest mor = sendSharedRecordRequest(source, doc, "read", target);

    logoutAndLoginAs(target);
    NotificationStatus notificationStatusTarget = communicationMgr.getNotificationStatus(target);
    assertEquals(0, notificationStatusTarget.getNotificationCount());
    assertEquals(1, notificationStatusTarget.getMessageCount());
    assertEquals(1, notificationStatusTarget.getSpecialMessageCount());

    // After accepting the share record request, the system will send a
    // notification to the source.
    reqUpdateMgr.updateStatus(
        target.getUsername(), CommunicationStatus.ACCEPTED, mor.getId(), "accepted");

    logoutAndLoginAs(source);
    NotificationStatus notificationStatusSource = communicationMgr.getNotificationStatus(source);
    assertEquals(1, notificationStatusSource.getNotificationCount());
    assertEquals(0, notificationStatusSource.getMessageCount());
    assertEquals(0, notificationStatusSource.getSpecialMessageCount());
  }

  private Long getSentRequestCountForUser(User u) {
    return communicationMgr
        .getSentRequests(
            u.getUsername(), PaginationCriteria.createDefaultForClass(MessageOrRequest.class))
        .getTotalHits();
  }

  private MessageOrRequest getLatestActiveRequestorMessageForUser(User u) {
    ISearchResults<MessageOrRequest> mors = searchDBForRequests(u);
    return mors.getResults().get(0);
  }

  private int getNewNotificationCountForUser(User u) {
    PaginationCriteria<CommunicationTarget> paginationCriteria =
        PaginationCriteria.createDefaultForClass(CommunicationTarget.class);
    ISearchResults<Notification> newNots =
        communicationMgr.getNewNotificationsForUser(u.getUsername(), paginationCriteria);
    return newNots.getTotalHits().intValue();
  }

  private void setUpUserToGetRequestUpdateNotifications(User u) {
    openTransaction();
    // refresh to avoid locking exception
    u = userDao.getUserByUsername(u.getUsername());
    u.setPreference(
        new UserPreference(
            Preference.NOTIFICATION_REQUEST_STATUS_CHANGE_PREF, u, Boolean.TRUE.toString()));
    userDao.save(u);
    commitTransaction();
  }

  /**
   * Check the list of messages or requests are ordered by creation time DESC.
   *
   * @param mors
   */
  private void assertResultsOrderedByCreationTimeDESC(
      ISearchResults<? extends Communication> mors) {
    for (int i = 0; i < mors.getResults().size() - 1; i++) {
      assertTrue(
          mors.getResults()
                  .get(i)
                  .getCreationTime()
                  .compareTo(mors.getResults().get(i + 1).getCreationTime())
              >= 0);
    }
  }

  /**
   * Check the list of messages or requests are ordered by creation time ASC.
   *
   * @param mors
   */
  private void assertResultsOrderedByCreationTimeASC(ISearchResults<? extends Communication> mors) {
    for (int i = 0; i < mors.getResults().size() - 1; i++) {
      assertTrue(
          mors.getResults()
                  .get(i)
                  .getCreationTime()
                  .compareTo(mors.getResults().get(i + 1).getCreationTime())
              <= 0);
    }
  }
}
