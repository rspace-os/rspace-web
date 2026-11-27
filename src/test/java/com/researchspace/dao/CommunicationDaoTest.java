package com.researchspace.dao;

import static com.researchspace.core.util.TransformerUtils.toSet;
import static com.researchspace.testutils.CommsTestUtils.createAGroupRequest;
import static com.researchspace.testutils.CommsTestUtils.createARequest;
import static com.researchspace.testutils.CommsTestUtils.createAnyNotification;
import static com.researchspace.testutils.CommsTestUtils.createRequestOfType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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
import com.researchspace.model.dtos.NotificationStatus;
import com.researchspace.model.sort.CommunicationSort;
import jakarta.persistence.PersistenceException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

public class CommunicationDaoTest extends BaseDaoTestCase {

  private @Autowired CommunicationDao dao;

  private User originator;

  @Test
  public void testSOriginatorMustNotBeNull() {
    Notification message = createAnyNotification(originator);
    message.setOriginator(null);
    dao.save(message);
    var session = sessionFactory.getCurrentSession();

    assertThrows(PersistenceException.class, () -> session.flush());
  }

  @Test
  public void testGetMessagesByFilter() throws InterruptedException {
    User sender = createAndSaveUserIfNotExists(getRandomAlphabeticString("source"));
    User target = createAndSaveUserIfNotExists(getRandomAlphabeticString("recipient"));
    /// send some basic message
    saveNMessages(2, sender, MessageType.SIMPLE_MESSAGE, target);
    // this won't be listed by default, it's not in the standard list
    saveNMessages(1, sender, MessageType.REQUEST_CREATE_LAB_GROUP, target);
    assertThat(dao.getActiveRequestsAndMessagesForUser(target, getDefaultPgCrit()).getResults())
        .hasSize(2);

    // now we'llconfigure to get these messages
    MessageTypeFilter filter =
        new MessageTypeFilter(EnumSet.of(MessageType.REQUEST_CREATE_LAB_GROUP));
    assertThat(
            dao.getActiveRequestsAndMessagesForUser(target, getDefaultPgCrit(), filter)
                .getResults())
        .hasSize(1);

    // check that null or empty filter is handled gracefully; if it is we
    // get all results
    assertThat(
            dao.getActiveRequestsAndMessagesForUser(target, getDefaultPgCrit(), null).getResults())
        .hasSize(3);
    filter = new MessageTypeFilter(EnumSet.noneOf(MessageType.class));
    assertThat(
            dao.getActiveRequestsAndMessagesForUser(target, getDefaultPgCrit(), null).getResults())
        .hasSize(3);
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
    assertThat(dao.getActiveRequestsAndMessagesForUser(sender, pgcrit).getResults()).hasSize(5);
    assertThat(dao.getActiveRequestsAndMessagesForUser(target, pgcrit).getResults()).hasSize(7);
    pgcrit.setOrderBy("creationTime");
    pgcrit.setSortOrder(SortOrder.ASC);
    pgcrit.setGetAllResults();
    ISearchResults<MessageOrRequest> results =
        dao.getAllSentAndReceivedSimpleMessagesForUser(sender, pgcrit);

    // Paginated, but get all the results because pgcrit.setGetAllResults();
    assertThat(results.getResults()).hasSize(12);
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
    assertThat(dao.getActiveRequestsAndMessagesForUser(sender, pgcrit).getResults())
        .hasSize(IPagination.DEFAULT_RESULTS_PERPAGE);
    assertThat(dao.getActiveRequestsAndMessagesForUser(target, pgcrit).getResults()).hasSize(7);

    pgcrit.setOrderBy("creationTime");
    pgcrit.setSortOrder(SortOrder.ASC);
    ISearchResults<MessageOrRequest> results =
        dao.getAllSentAndReceivedSimpleMessagesForUser(sender, pgcrit);
    assertThat(results.getResults()).hasSize(IPagination.DEFAULT_RESULTS_PERPAGE);
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
    // longer than a 64 KB TEXT column, and kept in full
    String longMessage = RandomStringUtils.randomAlphabetic(65_536);
    message.setNotificationMessage(longMessage);
    CommunicationTarget ct = new CommunicationTarget();
    ct.setCommunication(message);
    ct.setRecipient(target);

    Set<CommunicationTarget> targets = toSet(ct);
    message.setRecipients(targets);
    dao.save(message);
    // read back from the database rather than the session cache
    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().clear();

    Communication opened = dao.get(message.getId());
    assertNotNull(opened.getCreationTime());
    assertThat(opened.getRecipients()).hasSize(1);
    assertEquals(CommunicationStatus.NEW, opened.getRecipients().iterator().next().getStatus());
    assertEquals(longMessage, ((Notification) opened).getNotificationMessage());
  }

  @Test
  public void testMarkAllNotificationsAsRead() throws InterruptedException {

    originator = createAndSaveUserIfNotExists("source");
    User target = createAndSaveUserIfNotExists("recipient");
    saveNNotifications(IPagination.DEFAULT_RESULTS_PERPAGE, originator, target);

    // Now listing new notifications. We are using the default pagination
    PaginationCriteria<CommunicationTarget> pgCrit =
        PaginationCriteria.createDefaultForClass(CommunicationTarget.class);
    pgCrit.setOrderBy("creationTime");
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
        CommunicationStatus.COMPLETED,
        globalMsgUpdated.getRecipients().iterator().next().getStatus(),
        "communicationTarget status should be updated");
    assertEquals(
        CommunicationStatus.NEW,
        globalMsgUpdated.getStatus(),
        "communication status shouldn't be updated for global message");
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
    pc.setOrderBy("sender");

    originator = createAndSaveUserIfNotExists("source");
    User target = createAndSaveUserIfNotExists("recipient");
    saveNNotifications(2, originator, target);

    assertEquals(2, dao.getNewNotificationsForUser(target, pc).getTotalHits().intValue());
    pc.setOrderBy("creationTime");
    assertEquals(2, dao.getNewNotificationsForUser(target, pc).getTotalHits().intValue());
    // Test ordering of active requests
    saveNMessages(3, originator, MessageType.REQUEST_RECORD_REVIEW, target);
    assertEquals(3, dao.getActiveRequestsAndMessagesForUser(target, pc).getTotalHits().intValue());
    pc.setOrderBy("sender");
    assertEquals(3, dao.getActiveRequestsAndMessagesForUser(target, pc).getTotalHits().intValue());
    pc.setOrderBy("requestedCompletionDate");
    assertEquals(3, dao.getActiveRequestsAndMessagesForUser(target, pc).getTotalHits().intValue());

    // Test ordering of sent messages
    PaginationCriteria<MessageOrRequest> sentMsgesPC =
        PaginationCriteria.createDefaultForClass(MessageOrRequest.class);
    sentMsgesPC.setOrderBy("creationTime");
    assertEquals(3, dao.getSentRequests(originator, sentMsgesPC).getTotalHits().intValue());
    sentMsgesPC.setOrderBy("sender");
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
    assertThat(res.getResults()).hasSize(3);

    // Now Order ascending so => 1,2,0
    sentMsgesPC.setSortOrder(SortOrder.ASC);
    res = dao.getSentRequests(originator, sentMsgesPC);
    assertEquals(toTest.get(1), res.getResults().get(0));
    assertEquals(toTest.get(2), res.getResults().get(1));
    assertEquals(toTest.get(0), res.getResults().get(2));
    // Return all sent requests (even request with completion date null)
    assertThat(res.getResults()).hasSize(3);
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

    pc.setOrderBy("sender");
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
    assertThat(msg2.getRecipients()).hasSize(1);
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
    assertThat(msg2.getRecipients()).hasSize(1);
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

  private List<MessageOrRequest> saveNMessages(int n, User from, MessageType type, User... to) {
    List<MessageOrRequest> saved = new ArrayList<>();
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
      saved.add(mor);
    }
    return saved;
  }

  private List<Notification> saveNNotifications(int n, User from, User... to)
      throws InterruptedException {
    List<Notification> saved = new ArrayList<>();
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
      saved.add(not);
    }
    return saved;
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

  @Test
  public void notificationPagingIsStableWhenCreationTimesTie() throws InterruptedException {
    originator = createAndSaveUserIfNotExists("source");
    User target = createAndSaveUserIfNotExists("recipient");
    // creationTime is a second-granularity datetime, so these all tie
    saveNNotifications(6, originator, target);

    PaginationCriteria<CommunicationTarget> pc = getDefaultPgCrit();
    pc.setResultsPerPage(2);
    List<Long> paged = new ArrayList<>();
    for (long page = 0; page < 3; page++) {
      pc.setPageNumber(page);
      dao.getNewNotificationsForUser(target, pc).getResults().stream()
          .map(Notification::getId)
          .forEach(paged::add);
    }

    assertEquals(6, new HashSet<>(paged).size(), "paging repeated or skipped a notification");
    List<Long> descending = new ArrayList<>(paged);
    descending.sort(Comparator.reverseOrder());
    assertEquals(descending, paged, "tied creationTimes must fall back to descending id");
  }

  @Test
  public void listingMessagesDoesNotLoadGlobalMessageRecipients() {
    User sender = createAndSaveRandomUser();
    User recipient = createAndSaveRandomUser();
    MessageOrRequest global =
        saveMessageWithPrevious(MessageType.GLOBAL_MESSAGE, sender, recipient, null);
    MessageOrRequest original =
        saveMessageWithPrevious(MessageType.SIMPLE_MESSAGE, recipient, sender, null);
    MessageOrRequest reply =
        saveMessageWithPrevious(MessageType.SIMPLE_MESSAGE, sender, recipient, original);
    flushAndClear();

    Map<Long, MessageOrRequest> listed =
        listMessagesFor(recipient, 10).stream()
            .collect(Collectors.toMap(MessageOrRequest::getId, Function.identity()));

    assertTrue(Hibernate.isInitialized(listed.get(reply.getId()).getRecipients()));
    assertTrue(Hibernate.isInitialized(listed.get(reply.getId()).getPrevious()));
    assertFalse(Hibernate.isInitialized(listed.get(global.getId()).getRecipients()));
  }

  @Test
  public void listingResolvesAGlobalPreviousMessageWithoutItsRecipients() {
    User sender = createAndSaveRandomUser();
    User recipient = createAndSaveRandomUser();
    // sent by the listing user, so the global message itself is not listed
    MessageOrRequest global =
        saveMessageWithPrevious(MessageType.GLOBAL_MESSAGE, recipient, sender, null);
    MessageOrRequest reply =
        saveMessageWithPrevious(MessageType.SIMPLE_MESSAGE, sender, recipient, global);
    flushAndClear();

    List<MessageOrRequest> listed = listMessagesFor(recipient, 10);

    assertEquals(List.of(reply.getId()), listed.stream().map(MessageOrRequest::getId).toList());
    MessageOrRequest previous = listed.get(0).getPrevious();
    assertTrue(Hibernate.isInitialized(previous));
    assertFalse(Hibernate.isInitialized(previous.getRecipients()));
  }

  @Test
  public void listingsDoNotQueryPerMessage() {
    User sender = createAndSaveRandomUser();
    User recipient = createAndSaveRandomUser();
    User bystander = createAndSaveRandomUser();
    // each listed message has a previous message outside every listing, left as a proxy
    Supplier<MessageOrRequest> unlisted =
        () -> saveMessageWithPrevious(MessageType.SIMPLE_MESSAGE, bystander, bystander, null);
    saveMessageWithPrevious(MessageType.GLOBAL_MESSAGE, sender, recipient, unlisted.get());
    for (int i = 0; i < 7; i++) {
      saveMessageWithPrevious(MessageType.SIMPLE_MESSAGE, sender, recipient, unlisted.get());
      saveMessageWithPrevious(MessageType.REQUEST_RECORD_REVIEW, sender, recipient, unlisted.get());
    }
    flushAndClear();

    assertAll(
        () ->
            assertStatementCountIndependentOfPageSize(
                this::getDefaultPgCrit,
                pc ->
                    dao.getActiveRequestsAndMessagesForUser(
                        recipient, pc, MessageTypeFilter.ALL_TYPES)),
        () ->
            assertStatementCountIndependentOfPageSize(
                this::getDefaultPgCrit,
                pc -> dao.getAllSentAndReceivedSimpleMessagesForUser(recipient, pc)),
        () ->
            assertStatementCountIndependentOfPageSize(
                () -> PaginationCriteria.createDefaultForClass(MessageOrRequest.class),
                pc -> dao.getSentRequests(sender, pc)));
  }

  private <P> void assertStatementCountIndependentOfPageSize(
      Supplier<PaginationCriteria<P>> pgCritFactory,
      Function<PaginationCriteria<P>, ISearchResults<MessageOrRequest>> listing) {
    long twoPerPage = countStatementsListing(2, pgCritFactory, listing);
    long sevenPerPage = countStatementsListing(7, pgCritFactory, listing);
    assertEquals(twoPerPage, sevenPerPage, "statement count must not grow with page size");
  }

  private <P> long countStatementsListing(
      int pageSize,
      Supplier<PaginationCriteria<P>> pgCritFactory,
      Function<PaginationCriteria<P>, ISearchResults<MessageOrRequest>> listing) {
    PaginationCriteria<P> pc = pgCritFactory.get();
    pc.setResultsPerPage(pageSize);
    Statistics stats = sessionFactory.getStatistics();
    boolean wasEnabled = stats.isStatisticsEnabled();
    stats.setStatisticsEnabled(true);
    try {
      sessionFactory.getCurrentSession().clear();
      stats.clear();
      assertThat(listing.apply(pc).getResults()).hasSize(pageSize);
      return stats.getPrepareStatementCount();
    } finally {
      stats.setStatisticsEnabled(wasEnabled);
    }
  }

  @Test
  public void listingPagesAreSlicesOfTheFullListingForEverySort() throws InterruptedException {
    ListingFixture fx = saveListingFixture();
    EnumSet<CommunicationSort> allSorts = EnumSet.allOf(CommunicationSort.class);

    assertPagesAreSlicesOfFullListing(
        5, allSorts, this::getDefaultPgCrit, pc -> dao.getNewNotificationsForUser(fx.target(), pc));
    assertPagesAreSlicesOfFullListing(
        6,
        allSorts,
        this::getDefaultPgCrit,
        pc ->
            dao.getActiveRequestsAndMessagesForUser(fx.target(), pc, MessageTypeFilter.ALL_TYPES));
    assertPagesAreSlicesOfFullListing(
        5,
        allSorts,
        this::getDefaultPgCrit,
        pc -> dao.getAllSentAndReceivedSimpleMessagesForUser(fx.target(), pc));
    assertPagesAreSlicesOfFullListing(
        5,
        allSorts,
        () -> PaginationCriteria.createDefaultForClass(MessageOrRequest.class),
        pc -> dao.getSentRequests(fx.senderA(), pc));
  }

  @Test
  public void listingsCountInTheDatabaseAndFetchOnlyOnePageOfIds() throws InterruptedException {
    ListingFixture fx = saveListingFixture();

    assertCountsAndFetchesOnePage(
        this::getDefaultPgCrit, pc -> dao.getNewNotificationsForUser(fx.target(), pc));
    assertCountsAndFetchesOnePage(
        this::getDefaultPgCrit,
        pc ->
            dao.getActiveRequestsAndMessagesForUser(fx.target(), pc, MessageTypeFilter.ALL_TYPES));
    assertCountsAndFetchesOnePage(
        this::getDefaultPgCrit,
        pc -> dao.getAllSentAndReceivedSimpleMessagesForUser(fx.target(), pc));
    assertCountsAndFetchesOnePage(
        () -> PaginationCriteria.createDefaultForClass(MessageOrRequest.class),
        pc -> dao.getSentRequests(fx.senderA(), pc));
  }

  @Test
  public void maxValuePageSizeReturnsEveryActiveRequest() {
    User oldPi = createAndSaveUserIfNotExists(getRandomAlphabeticString("oldPi"));
    User requester = createAndSaveUserIfNotExists(getRandomAlphabeticString("requester"));
    int numRequests = IPagination.DEFAULT_RESULTS_PERPAGE + 3;
    saveNMessages(numRequests, requester, MessageType.REQUEST_RECORD_WITNESS, oldPi);
    MessageTypeFilter witnessRequests =
        new MessageTypeFilter(EnumSet.of(MessageType.REQUEST_RECORD_WITNESS));
    // as requested by PiChangeHandlerImpl when transferring witness requests
    PaginationCriteria<CommunicationTarget> pc = getDefaultPgCrit();
    pc.setResultsPerPage(Integer.MAX_VALUE);

    ISearchResults<MessageOrRequest> all =
        dao.getActiveRequestsAndMessagesForUser(oldPi, pc, witnessRequests);
    assertThat(all.getResults()).hasSize(numRequests);
    assertEquals(numRequests, all.getTotalHits().intValue());

    // page 2 starts at index 2 * Integer.MAX_VALUE, which wraps to -2 if computed as an int
    pc.setPageNumber(2L);
    ISearchResults<MessageOrRequest> pastTheEnd =
        dao.getActiveRequestsAndMessagesForUser(oldPi, pc, witnessRequests);
    assertThat(pastTheEnd.getResults()).isEmpty();
    assertEquals(numRequests, pastTheEnd.getTotalHits().intValue());

    // 2^62 * 4 wraps to 0 in a long, which would return the first page
    pc.setResultsPerPage(4);
    pc.setPageNumber(1L << 62);
    ISearchResults<MessageOrRequest> hugePageNumber =
        dao.getActiveRequestsAndMessagesForUser(oldPi, pc, witnessRequests);
    assertThat(hugePageNumber.getResults()).isEmpty();
    assertEquals(numRequests, hugePageNumber.getTotalHits().intValue());
  }

  @Test
  public void lastStatusUpdateSortUsesEachMessagesLatestTargetUpdate() {
    User sender = createAndSaveUserIfNotExists(getRandomAlphabeticString("sender"));
    User user = createAndSaveUserIfNotExists(getRandomAlphabeticString("user"));
    User other = createAndSaveUserIfNotExists(getRandomAlphabeticString("other"));
    // the sent message's two target updates straddle the received message's update
    MessageOrRequest sent =
        saveNMessages(1, user, MessageType.SIMPLE_MESSAGE, sender, other).get(0);
    MessageOrRequest received = saveNMessages(1, sender, MessageType.SIMPLE_MESSAGE, user).get(0);
    Date base = getDateFor("2024-01-01");
    List<CommunicationTarget> sentTargets = new ArrayList<>(sent.getRecipients());
    sentTargets.get(0).setLastStatusUpdate(DateUtils.addDays(base, 1));
    sentTargets.get(1).setLastStatusUpdate(DateUtils.addDays(base, 5));
    received.getRecipients().forEach(ct -> ct.setLastStatusUpdate(DateUtils.addDays(base, 3)));
    sessionFactory.getCurrentSession().flush();

    PaginationCriteria<CommunicationTarget> pc = getDefaultPgCrit();
    pc.setOrderBy(CommunicationSort.LAST_STATUS_UPDATE.key());
    pc.setResultsPerPage(1);
    for (SortOrder order : SortOrder.values()) {
      pc.setSortOrder(order);
      List<Long> paged = new ArrayList<>();
      for (long page = 0; page < 2; page++) {
        pc.setPageNumber(page);
        paged.addAll(idsOf(dao.getAllSentAndReceivedSimpleMessagesForUser(user, pc)));
      }
      List<Long> expected =
          SortOrder.DESC.equals(order)
              ? List.of(sent.getId(), received.getId())
              : List.of(received.getId(), sent.getId());
      assertEquals(expected, paged, order.toString());
    }
  }

  private record ListingFixture(User senderA, User target) {}

  /**
   * Saves communications matching each listing more than one page of 2 deep, with creation times,
   * requested completion dates (some null) and status update times that do not follow id order.
   */
  private ListingFixture saveListingFixture() throws InterruptedException {
    User senderA = createAndSaveUserIfNotExists(getRandomAlphabeticString("senderA"));
    User senderB = createAndSaveUserIfNotExists(getRandomAlphabeticString("senderB"));
    User target = createAndSaveUserIfNotExists(getRandomAlphabeticString("recipient"));
    List<Communication> saved = new ArrayList<>();
    saved.addAll(saveNNotifications(3, senderA, target));
    saved.addAll(saveNNotifications(2, senderB, target));
    saved.addAll(saveNMessages(3, senderA, MessageType.REQUEST_RECORD_REVIEW, target));
    saved.addAll(saveNMessages(2, senderA, MessageType.REQUEST_RECORD_REVIEW, senderB));
    saved.addAll(saveNMessages(3, senderB, MessageType.SIMPLE_MESSAGE, target));
    // two recipients, so the id queries join two target rows per message
    saved.addAll(saveNMessages(2, target, MessageType.SIMPLE_MESSAGE, senderA, senderB));

    Date base = getDateFor("2024-01-01");
    for (int i = 0; i < saved.size(); i++) {
      Communication comm = saved.get(i);
      ReflectionTestUtils.setField(comm, "creationTime", DateUtils.addDays(base, (i * 5) % 7));
      if (comm instanceof MessageOrRequest mor) {
        mor.setRequestedCompletionDate(
            i % 3 == 0 ? null : DateUtils.addDays(base, 30 + (i * 4) % 5));
      }
      Date lastStatusUpdate = DateUtils.addDays(base, 60 + (i * 3) % 5);
      comm.getRecipients().forEach(ct -> ct.setLastStatusUpdate(lastStatusUpdate));
    }
    sessionFactory.getCurrentSession().flush();
    return new ListingFixture(senderA, target);
  }

  private <P> void assertPagesAreSlicesOfFullListing(
      int expectedTotal,
      Set<CommunicationSort> sorts,
      Supplier<PaginationCriteria<P>> pgCritFactory,
      Function<PaginationCriteria<P>, ISearchResults<? extends Communication>> listing) {
    final int pageSize = 2;
    for (CommunicationSort sort : sorts) {
      for (SortOrder order : SortOrder.values()) {
        String context = sort.key() + " " + order;
        PaginationCriteria<P> pc = pgCritFactory.get();
        pc.setOrderBy(sort.key());
        pc.setSortOrder(order);
        pc.setGetAllResults();
        List<Long> fullListing = idsOf(listing.apply(pc));
        assertEquals(expectedTotal, fullListing.size(), context);
        assertEquals(expectedTotal, new HashSet<>(fullListing).size(), context);

        pc.setResultsPerPage(pageSize);
        long pageCount = (expectedTotal + pageSize - 1) / pageSize;
        for (long page = 0; page <= pageCount; page++) {
          pc.setPageNumber(page);
          ISearchResults<? extends Communication> results = listing.apply(pc);
          int from = (int) Math.min(page * pageSize, expectedTotal);
          int to = Math.min(from + pageSize, expectedTotal);
          assertEquals(fullListing.subList(from, to), idsOf(results), context + " page " + page);
          assertEquals(expectedTotal, results.getTotalHits().intValue(), context + " page " + page);
        }
      }
    }
  }

  /**
   * Runs one page-of-2 request with Hibernate statistics and asserts that the total comes from a
   * count query and that no query returned more rows than the page holds.
   */
  private <P> void assertCountsAndFetchesOnePage(
      Supplier<PaginationCriteria<P>> pgCritFactory,
      Function<PaginationCriteria<P>, ISearchResults<? extends Communication>> listing) {
    final int pageSize = 2;
    PaginationCriteria<P> pc = pgCritFactory.get();
    pc.setResultsPerPage(pageSize);
    sessionFactory.getCurrentSession().clear();
    Statistics stats = sessionFactory.getStatistics();
    boolean wasEnabled = stats.isStatisticsEnabled();
    stats.setStatisticsEnabled(true);
    stats.clear();
    try {
      assertThat(listing.apply(pc).getResults()).hasSize(pageSize);
      List<String> queries = List.of(stats.getQueries());
      assertThat(queries).anyMatch(hql -> hql.toLowerCase().contains("count(distinct"));
      for (String hql : queries) {
        assertThat(stats.getQueryStatistics(hql).getExecutionRowCount())
            .as(hql)
            .isLessThanOrEqualTo(pageSize);
      }
    } finally {
      stats.clear();
      stats.setStatisticsEnabled(wasEnabled);
    }
  }

  private List<MessageOrRequest> listMessagesFor(User user, int pageSize) {
    PaginationCriteria<CommunicationTarget> pgCrit = getDefaultPgCrit();
    pgCrit.setResultsPerPage(pageSize);
    return dao.getActiveRequestsAndMessagesForUser(user, pgCrit, MessageTypeFilter.ALL_TYPES)
        .getResults();
  }

  private MessageOrRequest saveMessageWithPrevious(
      MessageType type, User sender, User recipient, MessageOrRequest previous) {
    MessageOrRequest message = createRequestOfType(sender, type);
    message.setPreviousMessage(previous);
    message.setRecipients(toSet(createCommTarget(recipient, message)));
    return (MessageOrRequest) dao.save(message);
  }

  private void flushAndClear() {
    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().clear();
  }

  private static List<Long> idsOf(ISearchResults<? extends Communication> results) {
    return results.getResults().stream().map(Communication::getId).toList();
  }

  @Test
  public void notificationStatusCountsMatchListingTotals() throws InterruptedException {
    User sender = createAndSaveUserIfNotExists(getRandomAlphabeticString("source"));
    User target = createAndSaveUserIfNotExists(getRandomAlphabeticString("recipient"));
    User other = createAndSaveUserIfNotExists(getRandomAlphabeticString("other"));

    // standard messages: 2 shared with another user, 1 request, 1 targeting the user twice
    saveNMessages(2, sender, MessageType.SIMPLE_MESSAGE, target, other);
    saveNMessages(1, sender, MessageType.REQUEST_RECORD_REVIEW, target);
    MessageOrRequest twiceTargeted =
        saveMessage(sender, MessageType.SIMPLE_MESSAGE, CommunicationStatus.NEW, target);
    addTarget(twiceTargeted, target, CommunicationStatus.NEW);
    // not counted: terminated for the user, or sent by the user
    saveMessage(sender, MessageType.SIMPLE_MESSAGE, CommunicationStatus.COMPLETED, target);
    saveNMessages(1, target, MessageType.SIMPLE_MESSAGE, sender);

    // special messages: 1 share request, 1 global, 1 active through only one of its two targets
    saveNMessages(1, sender, MessageType.REQUEST_SHARE_RECORD, target);
    saveNMessages(1, sender, MessageType.GLOBAL_MESSAGE, target, other);
    MessageOrRequest partlyTerminated =
        saveMessage(sender, MessageType.REQUEST_JOIN_LAB_GROUP, CommunicationStatus.NEW, target);
    addTarget(partlyTerminated, target, CommunicationStatus.REJECTED);

    // notifications: 2 new, 1 targeting the user twice, 1 read (not counted)
    saveNNotifications(2, sender, target);
    Notification twiceNotified = saveNotification(sender, CommunicationStatus.NEW, target);
    addTarget(twiceNotified, target, CommunicationStatus.NEW);
    saveNotification(sender, CommunicationStatus.COMPLETED, target);

    Session session = sessionFactory.getCurrentSession();
    session.flush();
    session.clear();

    // the login counters must stay count queries (one for notifications, one grouped by message
    // type), not ID-list loads
    Statistics stats = sessionFactory.getStatistics();
    boolean statsWereEnabled = stats.isStatisticsEnabled();
    stats.setStatisticsEnabled(true);
    stats.clear();
    NotificationStatus status;
    try {
      status = dao.getNotificationStatus(target);
      assertEquals(2, stats.getPrepareStatementCount());
      assertEquals(2, stats.getQueryExecutionCount());
      assertThat(stats.getQueries())
          .isNotEmpty()
          .allMatch(hql -> hql.contains("count(distinct "))
          .noneMatch(hql -> hql.startsWith("select distinct "));
    } finally {
      stats.setStatisticsEnabled(statsWereEnabled);
    }

    assertEquals(3, status.getNotificationCount());
    assertEquals(4, status.getMessageCount());
    assertEquals(3, status.getSpecialMessageCount());
    PaginationCriteria<CommunicationTarget> pgCrit = getDefaultPgCrit();
    assertEquals(
        dao.getNewNotificationsForUser(target, pgCrit).getTotalHits().intValue(),
        status.getNotificationCount());
    assertEquals(
        dao.getActiveRequestsAndMessagesForUser(
                target, pgCrit, MessageTypeFilter.DEFAULT_MESSAGE_LISTING)
            .getTotalHits()
            .intValue(),
        status.getMessageCount());
    assertEquals(
        dao.getActiveRequestsAndMessagesForUser(
                target, pgCrit, MessageTypeFilter.SPECIAL_MESSAGE_LISTING)
            .getTotalHits()
            .intValue(),
        status.getSpecialMessageCount());
  }

  private MessageOrRequest saveMessage(
      User from, MessageType type, CommunicationStatus status, User to) {
    MessageOrRequest mor = createRequestOfType(from, type);
    CommunicationTarget ct = createCommTarget(to, mor);
    ct.setStatus(status);
    mor.setRecipients(toSet(ct));
    dao.save(mor);
    return mor;
  }

  private Notification saveNotification(User from, CommunicationStatus status, User to) {
    Notification notification = createAnyNotification(from);
    CommunicationTarget ct = new CommunicationTarget();
    ct.setCommunication(notification);
    ct.setRecipient(to);
    ct.setStatus(status);
    notification.setRecipients(toSet(ct));
    dao.save(notification);
    return notification;
  }

  /** Persists a target row directly, as the entity's recipient set would merge a duplicate. */
  private void addTarget(Communication comm, User to, CommunicationStatus status) {
    CommunicationTarget ct = new CommunicationTarget();
    ct.setCommunication(comm);
    ct.setRecipient(to);
    ct.setStatus(status);
    sessionFactory.getCurrentSession().persist(ct);
  }

  private PaginationCriteria<CommunicationTarget> getDefaultPgCrit() {
    return PaginationCriteria.createDefaultForClass(CommunicationTarget.class);
  }
}
