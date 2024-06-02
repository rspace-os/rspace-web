package com.researchspace.dao;

import static com.researchspace.core.util.TransformerUtils.toSet;
import static com.researchspace.model.comms.CommsTestUtils.createAGroupRequest;
import static com.researchspace.model.comms.CommsTestUtils.createARequest;
import static com.researchspace.model.comms.CommsTestUtils.createAnyNotification;
import static com.researchspace.model.comms.CommsTestUtils.createRequestOfType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.researchspace.Constants;
import com.researchspace.core.util.IPagination;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SortOrder;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.comms.Communication;
import com.researchspace.model.comms.CommunicationStatus;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.GroupMessageOrRequest;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.comms.Notification;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.dtos.MessageTypeFilter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.persistence.PersistenceException;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.time.DateUtils;
import org.hibernate.Session;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class CommunicationDaoTest extends BaseDaoTestCase {

  private @Autowired CommunicationDao dao;

  private User originator;

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {}

  @Test(expected = PersistenceException.class)
  public void testSOriginatorMustNotBeNull() {
    Notification message = createAnyNotification(originator);
    message.setOriginator(null);
    dao.save(message);
    // needed to test FK constraints
    sessionFactory.getCurrentSession().flush();
  }

  @Test
  public void testGetMessagesByFilter() throws InterruptedException {
    User sender = createAndSaveUserIfNotExists(getRandomAlphabeticString("source"));
    User target = createAndSaveUserIfNotExists(getRandomAlphabeticString("recipient"));
    /// send some basic message
    saveNMessages(2, sender, MessageType.SIMPLE_MESSAGE, target);
    // this won't be listed by default, it's not in the standard list
    saveNMessages(1, sender, MessageType.REQUEST_CREATE_LAB_GROUP, target);
    assertEquals(
        2, dao.getActiveRequestsAndMessagesForUser(target, getDefaultPgCrit()).getResults().size());

    // now we'llconfigure to get these messages
    MessageTypeFilter filter =
        new MessageTypeFilter(EnumSet.of(MessageType.REQUEST_CREATE_LAB_GROUP));
    assertEquals(
        1,
        dao.getActiveRequestsAndMessagesForUser(target, getDefaultPgCrit(), filter)
            .getResults()
            .size());

    // check that null or empty filter is handled gracefully; if it is we
    // get all results
    assertEquals(
        3,
        dao.getActiveRequestsAndMessagesForUser(target, getDefaultPgCrit(), null)
            .getResults()
            .size());
    filter = new MessageTypeFilter(EnumSet.noneOf(MessageType.class));
    assertEquals(
        3,
        dao.getActiveRequestsAndMessagesForUser(target, getDefaultPgCrit(), null)
            .getResults()
            .size());
  }

  @Test
  public void testGetAllSentAndReceivedMessages() throws InterruptedException {
    User sender = createAndSaveUserIfNotExists(getRandomAlphabeticString("source"));
    User target = createAndSaveUserIfNotExists(getRandomAlphabeticString("recipient"));
    User target2 = createAndSaveUserIfNotExists(getRandomAlphabeticString("recipient2"));
    PaginationCriteria<CommunicationTarget> pgcrit = getDefaultPgCrit();

    // total 12 messages from sender
    saveNMessages(7, sender, MessageType.SIMPLE_MESSAGE, target, target2);
    Thread.sleep(1000);
    // total 5 messages to sender
    saveNMessages(5, target, MessageType.SIMPLE_MESSAGE, sender, target2);

    // sanity check
    assertEquals(5, dao.getActiveRequestsAndMessagesForUser(sender, pgcrit).getResults().size());
    assertEquals(7, dao.getActiveRequestsAndMessagesForUser(target, pgcrit).getResults().size());
    pgcrit.setOrderBy("communication.creationTime");
    pgcrit.setSortOrder(SortOrder.ASC);
    pgcrit.setGetAllResults();
    ISearchResults<MessageOrRequest> results =
        dao.getAllSentAndReceivedSimpleMessagesForUser(sender, pgcrit);

    // Paginated, but get all the results because pgcrit.setGetAllResults();
    assertEquals(12, results.getResults().size());
    // Check the total hits (count) is 12;
    assertEquals(12, results.getTotalHits().intValue());
    assertSearchResultsAreDistinct(results);

    // check sort order
    for (int i = 0; i < results.getResults().size() - 1; i++) {
      Date time1 =
          DateUtils.truncate(results.getResults().get(i).getCreationTime(), Calendar.SECOND);
      Date time2 =
          DateUtils.truncate(results.getResults().get(i + 1).getCreationTime(), Calendar.SECOND);
      assertFalse(time1.after(time2));
    }
  }

  @Test
  public void testGetAllSentAndReceivedMessagesWithDefaultPagination() throws InterruptedException {
    User sender = createAndSaveUserIfNotExists(getRandomAlphabeticString("source"));
    User target = createAndSaveUserIfNotExists(getRandomAlphabeticString("recipient"));

    // Create IPagination.DEFAULT_RESULTS_PERPAGE + 7 messages
    saveNMessages(7, sender, MessageType.SIMPLE_MESSAGE, target);
    Thread.sleep(1000);
    saveNMessages(IPagination.DEFAULT_RESULTS_PERPAGE, target, MessageType.SIMPLE_MESSAGE, sender);

    PaginationCriteria<CommunicationTarget> pgcrit = getDefaultPgCrit();
    // sanity check
    assertEquals(
        IPagination.DEFAULT_RESULTS_PERPAGE,
        dao.getActiveRequestsAndMessagesForUser(sender, pgcrit).getResults().size());
    assertEquals(7, dao.getActiveRequestsAndMessagesForUser(target, pgcrit).getResults().size());

    pgcrit.setOrderBy("communication.creationTime");
    pgcrit.setSortOrder(SortOrder.ASC);
    ISearchResults<MessageOrRequest> results =
        dao.getAllSentAndReceivedSimpleMessagesForUser(sender, pgcrit);
    assertEquals(IPagination.DEFAULT_RESULTS_PERPAGE, results.getResults().size());
    assertEquals(IPagination.DEFAULT_RESULTS_PERPAGE + 7, results.getTotalHits().intValue());
    assertSearchResultsAreDistinct(results);

    // Check sort order
    for (int i = 0; i < results.getResults().size() - 1; i++) {
      Date time1 =
          DateUtils.truncate(results.getResults().get(i).getCreationTime(), Calendar.SECOND);
      Date time2 =
          DateUtils.truncate(results.getResults().get(i + 1).getCreationTime(), Calendar.SECOND);
      assertFalse(time1.after(time2));
    }
  }

  @Test
  public void testSaveNotification() {

    originator = createAndSaveUserIfNotExists("source");
    User target = createAndSaveUserIfNotExists("recipient");
    Notification message = createAnyNotification(originator);
    // should be permitted and will be truncated if too long.
    message.setNotificationMessage(
        RandomStringUtils.randomAlphabetic(Notification.MAX_MESSAGE_LENGTH + 1));
    CommunicationTarget ct = new CommunicationTarget();
    ct.setCommunication(message);
    ct.setRecipient(target);

    Set<CommunicationTarget> targets = toSet(ct);
    message.setRecipients(targets);
    dao.save(message);

    Communication opened = dao.get(message.getId());
    assertNotNull(opened.getCreationTime());
    assertEquals(1, opened.getRecipients().size());
    assertEquals(CommunicationStatus.NEW, opened.getRecipients().iterator().next().getStatus());
    assertEquals(
        Notification.MAX_MESSAGE_LENGTH, ((Notification) opened).getNotificationMessage().length());
  }

  @Test
  public void testMarkAllNotificationsAsRead() throws InterruptedException {

    originator = createAndSaveUserIfNotExists("source");
    User target = createAndSaveUserIfNotExists("recipient");
    saveNNotifications(IPagination.DEFAULT_RESULTS_PERPAGE, originator, target);

    // Now listing new notifications. We are using the default pagination
    PaginationCriteria<CommunicationTarget> pgCrit =
        PaginationCriteria.createDefaultForClass(CommunicationTarget.class);
    pgCrit.setOrderBy("communication.creationTime");
    List<Notification> notifications = dao.getNewNotificationsForUser(target, pgCrit).getResults();

    Date lastNotificationDate =
        notifications.get(IPagination.DEFAULT_RESULTS_PERPAGE - 1).getCreationTime();
    Date secondAfterLast = DateUtils.addSeconds(lastNotificationDate, 1);

    // Ensure next notification is new time using sleep(2000)
    Thread.sleep(2000);
    saveNNotifications(1, originator, target);

    // Now deleting earlier notifications
    dao.markAllNotificationsAsRead(target.getUsername(), secondAfterLast);

    // Check new notification since last listing is not deleted.
    assertEquals(1, dao.getNewNotificationsForUser(target, pgCrit).getTotalHits().intValue());
  }

  @Test
  public void testMarkNotificationsAsReadAndKeepMessages() throws InterruptedException {

    User sender = createAndSaveUserIfNotExists("source");
    User target = createAndSaveUserIfNotExists("recipient");
    saveNMessages(1, sender, MessageType.SIMPLE_MESSAGE, target);
    saveNNotifications(10, sender, target);

    Date earlierThan =
        dao.getNewNotificationsForUser(
                target, PaginationCriteria.createDefaultForClass(CommunicationTarget.class))
            .getResults()
            .get(9)
            .getCreationTime();
    dao.markAllNotificationsAsRead(target.getUsername(), earlierThan);

    // Here we assert that simple message is still active and it has been
    // marked as read.
    assertEquals(
        1,
        dao.getActiveRequestsAndMessagesForUser(
                target, PaginationCriteria.createDefaultForClass(CommunicationTarget.class))
            .getTotalHits()
            .intValue());
  }

  @Test
  public void testUpdateStatus() throws ParseException {
    originator = createAndSaveUserIfNotExists("source");
    User target = createAndSaveUserIfNotExists("recipient");

    // join lab message
    MessageOrRequest msg = createARequest(originator);
    CommunicationTarget ct = createCommTarget(target, msg);
    msg.setRecipients(toSet(ct));
    dao.save(msg);

    dao.updateStatus(target.getId(), msg.getId(), CommunicationStatus.COMPLETED, " a message");

    // assert communication target and the message is updated in DB
    MessageOrRequest msgUpdated = (MessageOrRequest) dao.get(msg.getId());
    assertEquals(
        CommunicationStatus.COMPLETED, msgUpdated.getRecipients().iterator().next().getStatus());
    assertEquals(CommunicationStatus.COMPLETED, msgUpdated.getStatus());

    // global message
    MessageOrRequest globalMsg = createRequestOfType(originator, MessageType.GLOBAL_MESSAGE);
    CommunicationTarget globalMsgCt = createCommTarget(target, globalMsg);
    globalMsg.setRecipients(toSet(globalMsgCt));
    dao.save(globalMsg);

    dao.updateStatus(
        target.getId(), globalMsg.getId(), CommunicationStatus.COMPLETED, " a message");

    // recipient status change shouldn't trigger message status change
    MessageOrRequest globalMsgUpdated = (MessageOrRequest) dao.get(globalMsg.getId());
    assertEquals(
        "communicationTarget status should be updated",
        CommunicationStatus.COMPLETED,
        globalMsgUpdated.getRecipients().iterator().next().getStatus());
    assertEquals(
        "communication status shouldn't be updated for global message",
        CommunicationStatus.NEW,
        globalMsgUpdated.getStatus());
  }

  @Test
  public void testNewMessages() {
    int NUM_MGES_TO_CREATE = IPagination.DEFAULT_RESULTS_PERPAGE + 2;
    originator = createAndSaveUserIfNotExists("source");
    User target = createAndSaveUserIfNotExists("recipient");
    saveNMessages(
        NUM_MGES_TO_CREATE, originator, MessageType.REQUEST_JOIN_EXISTING_COLLAB_GROUP, target);

    // no pagination
    assertEquals(new Long(NUM_MGES_TO_CREATE), getActiveRequestsAndMessagesForUser(target));
    assertEquals(new Long(0), getActiveRequestsAndMessagesForUser(originator));

    // Default pagination
    ISearchResults<MessageOrRequest> msges =
        dao.getActiveRequestsAndMessagesForUser(target, getDefaultPgCrit());
    assertEquals(PaginationCriteria.getDefaultResultsPerPage(), msges.getHits().intValue());

    // now let's make some requests completed
    final int NUM_REQUESTS_COMPLETED = 5;
    for (int i = 0; i < 5; i++) {
      MessageOrRequest mor = msges.getResults().get(i);
      for (CommunicationTarget ct : mor.getRecipients()) {
        ct.setStatus(CommunicationStatus.COMPLETED);
      }
      dao.save(mor); // changes will be cascaded.
    }
    // should be less messages
    ISearchResults<MessageOrRequest> msges2 =
        dao.getActiveRequestsAndMessagesForUser(
            target, PaginationCriteria.createDefaultForClass(CommunicationTarget.class));
    assertEquals(NUM_MGES_TO_CREATE - NUM_REQUESTS_COMPLETED, msges2.getTotalHits().intValue());
  }

  @Test
  public void testSortOrderForSearches() throws InterruptedException {
    // Test for search test
    PaginationCriteria<CommunicationTarget> pc = getDefaultPgCrit();
    pc.setOrderBy("originator.username");

    originator = createAndSaveUserIfNotExists("source");
    User target = createAndSaveUserIfNotExists("recipient");
    saveNNotifications(2, originator, target);

    assertEquals(2, dao.getNewNotificationsForUser(target, pc).getTotalHits().intValue());
    pc.setOrderBy("communication.creationTime");
    assertEquals(2, dao.getNewNotificationsForUser(target, pc).getTotalHits().intValue());
    // Test ordering of active requests
    saveNMessages(3, originator, MessageType.REQUEST_RECORD_REVIEW, target);
    assertEquals(3, dao.getActiveRequestsAndMessagesForUser(target, pc).getTotalHits().intValue());
    pc.setOrderBy("originator.username");
    assertEquals(3, dao.getActiveRequestsAndMessagesForUser(target, pc).getTotalHits().intValue());
    pc.setOrderBy("communication.requestedCompletionDate");
    assertEquals(3, dao.getActiveRequestsAndMessagesForUser(target, pc).getTotalHits().intValue());

    // Test ordering of sent messages
    PaginationCriteria<MessageOrRequest> sentMsgesPC =
        PaginationCriteria.createDefaultForClass(MessageOrRequest.class);
    sentMsgesPC.setOrderBy("creationTime");
    assertEquals(3, dao.getSentRequests(originator, sentMsgesPC).getTotalHits().intValue());
    sentMsgesPC.setOrderBy("originator.username");
    assertEquals(3, dao.getSentRequests(originator, sentMsgesPC).getTotalHits().intValue());
    sentMsgesPC.setOrderBy("requestedCompletionDate");
    assertEquals(3, dao.getSentRequests(originator, sentMsgesPC).getTotalHits().intValue());
  }

  @Test
  public void testSortOrderForRequestedCompletionDate() throws InterruptedException {
    originator = createAndSaveUserIfNotExists("source");
    User target = createAndSaveUserIfNotExists("recipient");

    List<MessageOrRequest> toTest = createNMessages(3, originator, target);
    toTest.get(0).setMessage("least priority");
    toTest.get(0).setRequestedCompletionDate(getDateFor("2020-05-30"));
    toTest.get(2).setMessage("highest");
    toTest.get(2).setRequestedCompletionDate(getDateFor("2015-05-30"));
    toTest.get(1).setMessage("no date set");
    for (MessageOrRequest mor : toTest) {
      dao.save(mor);
    }

    // Now we'll test the ordering by completion date.
    // Ordering should be descending by default so => 0,2,1
    PaginationCriteria<MessageOrRequest> sentMsgesPC =
        PaginationCriteria.createDefaultForClass(MessageOrRequest.class);
    sentMsgesPC.setOrderBy("requestedCompletionDate");
    ISearchResults<MessageOrRequest> res = dao.getSentRequests(originator, sentMsgesPC);
    assertEquals(toTest.get(0), res.getResults().get(0));
    assertEquals(toTest.get(2), res.getResults().get(1));
    assertEquals(toTest.get(1), res.getResults().get(2));
    // Return all sent requests (even request with completion date null)
    assertEquals(3, res.getResults().size());

    // Now Order ascending so => 1,2,0
    sentMsgesPC.setSortOrder(SortOrder.ASC);
    res = dao.getSentRequests(originator, sentMsgesPC);
    assertEquals(toTest.get(1), res.getResults().get(0));
    assertEquals(toTest.get(2), res.getResults().get(1));
    assertEquals(toTest.get(0), res.getResults().get(2));
    // Return all sent requests (even request with completion date null)
    assertEquals(3, res.getResults().size());
  }

  @Test
  public void testNewNotifications() throws InterruptedException {
    originator = createAndSaveUserIfNotExists("source");
    User target = createAndSaveUserIfNotExists("recipient");
    saveNNotifications(IPagination.DEFAULT_RESULTS_PERPAGE + 2, originator, target);

    // no pagination
    assertEquals(IPagination.DEFAULT_RESULTS_PERPAGE + 2, getNewNotificationCountForUser(target));
    assertEquals(0, getNewNotificationCountForUser(originator));
    // default pagination

    PaginationCriteria<CommunicationTarget> pc = getDefaultPgCrit();
    assertEquals(
        IPagination.DEFAULT_RESULTS_PERPAGE,
        dao.getNewNotificationsForUser(target, pc).getHits().intValue());

    pc.setOrderBy("originator.username");
    assertEquals(
        IPagination.DEFAULT_RESULTS_PERPAGE,
        dao.getNewNotificationsForUser(target, pc).getHits().intValue());
  }

  @Test
  public void testFindExistingNotificationsFor() throws InterruptedException {
    originator = createAndSaveUserIfNotExists("source");
    User target = createAndSaveUserIfNotExists("recipient");
    assertFalse(
        dao.findExistingNotificationFor(
            originator,
            NotificationType.NOTIFICATION_DOCUMENT_EDITED,
            null,
            createUserNameSet(target.getUsername())));
    saveNNotifications(1, originator, target);

    assertTrue(
        dao.findExistingNotificationFor(
            originator,
            NotificationType.NOTIFICATION_DOCUMENT_EDITED,
            null,
            createUserNameSet(target.getUsername())));
  }

  @Test
  public void testSaveRequest() throws ParseException {
    originator = createAndSaveUserIfNotExists("source");
    User target = createAndSaveUserIfNotExists("recipient");
    MessageOrRequest msg = createARequest(originator);

    CommunicationTarget ct = createCommTarget(target, msg);
    msg.setRecipients(toSet(ct));
    dao.save(msg);

    Communication msg2 = dao.get(msg.getId());
    assertEquals(1, msg2.getRecipients().size());
    assertEquals(CommunicationStatus.NEW, msg2.getRecipients().iterator().next().getStatus());
  }

  @Test
  public void testSaveGroupRequest() throws ParseException {
    originator = createAndSaveUserIfNotExists("source");
    User target = createAndSaveUserIfNotExists("recipient");
    GroupMessageOrRequest msg = createAGroupRequest(originator, null);

    CommunicationTarget ct = createCommTarget(target, msg);
    msg.setRecipients(toSet(ct));
    dao.save(msg);

    Communication msg2 = dao.get(msg.getId());
    assertTrue(msg2 instanceof GroupMessageOrRequest);
    assertEquals(1, msg2.getRecipients().size());
    assertEquals(CommunicationStatus.NEW, msg2.getRecipients().iterator().next().getStatus());
  }

  @Test
  public void tetsGetGroupRequestByGroupId() throws ParseException {
    User originator =
        createAndSaveUserIfNotExists(getRandomAlphabeticString("user"), Constants.PI_ROLE);
    Group grp = createGroup("any", originator);

    GroupMessageOrRequest msg = createAGroupRequest(originator, grp);
    dao.save(msg);
    assertEquals(msg, dao.getGroupMessageByGroupId(grp.getId()).get(0));
  }

  @Test
  public void testDeleteOldNotifications() throws ParseException, InterruptedException {
    originator = createAndSaveUserIfNotExists("source");
    User target1 = createAndSaveUserIfNotExists("recipient");
    User target2 = createAndSaveUserIfNotExists("recipient2");
    final int initialCommTargetCount = (int) getCommTargetCount();
    final int initialNotificanCount = (int) getNotificationCount();

    final int NUM_NOTIFICATIONS = 4;
    final int NUM_RECIPIENTS = 2;
    saveNNotifications(NUM_NOTIFICATIONS, originator, target1, target2);

    assertEquals(NUM_NOTIFICATIONS * NUM_RECIPIENTS, getCommTargetCount() - initialCommTargetCount);
    Date now = new Date();
    Date future = DateUtils.addHours(now, 4); // a future date for testing
    // notifications not marked as read by anyone, so all are new
    assertEquals(0, dao.deleteReadNotificationsOlderThanDate(future));

    dao.markAllNotificationsAsRead(target1.getUsername(), future);
    // still 0, not everyone has deleted
    assertEquals(0, dao.deleteReadNotificationsOlderThanDate(future));

    dao.markAllNotificationsAsRead(target2.getUsername(), future);

    Date past = DateUtils.addHours(now, -4); //
    // now all are flagged as read, but the test date is before the creation
    // date, so
    // they won't be deleted
    assertEquals(0, dao.deleteReadNotificationsOlderThanDate(past));

    // now, they are all deleted, and deletions are cascaded
    assertEquals(NUM_NOTIFICATIONS, dao.deleteReadNotificationsOlderThanDate(future));
    assertEquals(0, getNotificationCount() - initialNotificanCount);
    assertEquals(0, getCommTargetCount() - initialCommTargetCount);
  }

  private Date getDateFor(String date) {
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    try {
      return sdf.parse(date);
    } catch (ParseException e) {
      e.printStackTrace();
      return null;
    }
  }

  /**
   * Creates a list of requests without saving them, so they can be further modified by the test if
   * need be.
   *
   * @param n
   * @param from
   * @param to
   * @return
   * @throws InterruptedException
   */
  private List<MessageOrRequest> createNMessages(int n, User from, User to)
      throws InterruptedException {
    List<MessageOrRequest> rc = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      Thread.sleep(1); // ensure are distinct by equals() method
      try {
        MessageOrRequest mor;
        mor = createARequest(from);
        CommunicationTarget ct = new CommunicationTarget();
        ct.setCommunication(mor);
        ct.setRecipient(to);
        mor.setRecipients(toSet(ct));
        rc.add(mor);
      } catch (ParseException e) {
        e.printStackTrace();
        fail("could not parse date");
      }
    }
    return rc;
  }

  private void saveNMessages(int n, User from, MessageType type, User... to) {
    for (int i = 0; i < n; i++) {
      MessageOrRequest mor;
      mor = createRequestOfType(from, type);
      Set<CommunicationTarget> cts = new HashSet<>();
      for (User target : to) {
        CommunicationTarget ct = new CommunicationTarget();
        ct.setCommunication(mor);
        ct.setRecipient(target);
        cts.add(ct);
      }
      mor.setRecipients(cts);
      mor.setMessage(getRandomAlphabeticString("any"));
      dao.save(mor);
    }
  }

  private void saveNNotifications(int n, User from, User... to) throws InterruptedException {
    for (int i = 0; i < n; i++) {
      Thread.sleep(1); // ensure all are distinct by equals()
      Notification not = createAnyNotification(from);
      Set<CommunicationTarget> cts = new HashSet<CommunicationTarget>();
      for (User t : to) {
        CommunicationTarget ct = new CommunicationTarget();
        ct.setCommunication(not);
        ct.setRecipient(t);
        cts.add(ct);
      }
      not.setRecipients(cts);
      dao.save(not);
    }
  }

  private long getCommTargetCount() {
    Session s = sessionFactory.getCurrentSession();
    return (Long) s.createQuery("select count(id) from CommunicationTarget").uniqueResult();
  }

  private long getNotificationCount() {
    Session s = sessionFactory.getCurrentSession();
    return (Long) s.createQuery("select count(id) from Notification").uniqueResult();
  }

  private CommunicationTarget createCommTarget(User target, MessageOrRequest msg) {
    CommunicationTarget ct = new CommunicationTarget();
    ct.setCommunication(msg);
    ct.setRecipient(target);
    ct.setStatus(CommunicationStatus.NEW);
    return ct;
  }

  private Set<String> createUserNameSet(String... unames) {
    Set<String> rc = new HashSet<>();
    for (String ct : unames) {
      rc.add(ct);
    }
    return rc;
  }

  private int getNewNotificationCountForUser(User u) {
    PaginationCriteria<CommunicationTarget> paginationCriteria =
        PaginationCriteria.createDefaultForClass(CommunicationTarget.class);
    ISearchResults<Notification> newNots =
        communicationMgr.getNewNotificationsForUser(u.getUsername(), paginationCriteria);
    return newNots.getTotalHits().intValue();
  }

  private Long getActiveRequestsAndMessagesForUser(User u) {
    PaginationCriteria<CommunicationTarget> paginationCriteria =
        PaginationCriteria.createDefaultForClass(CommunicationTarget.class);
    ISearchResults<MessageOrRequest> mor =
        dao.getActiveRequestsAndMessagesForUser(u, paginationCriteria);
    return mor.getTotalHits();
  }

  private PaginationCriteria<CommunicationTarget> getDefaultPgCrit() {
    return PaginationCriteria.createDefaultForClass(CommunicationTarget.class);
  }
}
