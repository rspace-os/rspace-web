package com.researchspace.dao.hibernate;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.core.util.SortOrder;
import com.researchspace.dao.CommunicationDao;
import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.comms.*;
import com.researchspace.model.dtos.MessageTypeFilter;
import com.researchspace.model.dtos.NotificationStatus;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.sort.CommunicationSort;
import java.util.*;
import java.util.function.Consumer;
import org.apache.commons.collections4.ListUtils;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.springframework.stereotype.Repository;

/** CommunicationDAO (Data Access Object) implementation to retrieve/update communications. */
@Repository("communicationDao")
public class CommunicationDaoHibernateImpl extends GenericDaoHibernate<Communication, Long>
    implements CommunicationDao {
  public CommunicationDaoHibernateImpl() {
    super(Communication.class);
  }

  // COMMUNICATION_TARGET table not aliased by client code, the CT alias will be prepended to any
  // order-by criteria passed in by client code
  private static final String COMMUNICATION_TARGET_ALIAS = "ct";

  /**
   * Messages per recipient fetch. The fetch join repeats each message row once per recipient, so
   * listings that request every result (archive export) fetch recipients in batches of this size.
   */
  public static final int RECIPIENT_FETCH_BATCH_SIZE = 50;

  static final Set<CommunicationStatus> TERMINATED_STATES =
      EnumSet.of(
          CommunicationStatus.CANCELLED,
          CommunicationStatus.REJECTED,
          CommunicationStatus.COMPLETED);

  public ISearchResults<MessageOrRequest> getSentRequests(
      User user, PaginationCriteria<MessageOrRequest> pgCrit) {

    String fromWhere =
        "from MessageOrRequest mor "
            + "join mor.originator originator "
            + "where mor.status not in :terminatedStates "
            + "and mor.messageType <> :simpleMessage "
            + "and originator.id = :originatorId";
    IdPage ids =
        findPageOfIds(
            "mor.id",
            fromWhere,
            "mor",
            pgCrit,
            query ->
                query
                    .setParameterList("terminatedStates", TERMINATED_STATES)
                    .setParameter("simpleMessage", MessageType.SIMPLE_MESSAGE)
                    .setParameter("originatorId", user.getId()));
    return findPageOfMessageOrRequestForIds(ids, pgCrit);
  }

  @Override
  public ISearchResults<MessageOrRequest> getActiveRequestsAndMessagesForUser(
      User user, PaginationCriteria<CommunicationTarget> pgCrit, MessageTypeFilter filter) {

    if (filter == null) {
      filter = MessageTypeFilter.ALL_TYPES;
    }

    IdPage ids =
        findPageOfIds(
            "mor.id",
            activeMessageOrRequestFromWhere(filter),
            COMMUNICATION_TARGET_ALIAS,
            pgCrit,
            activeMessageOrRequestParams(user, filter));
    return findPageOfMessageOrRequestForIds(ids, pgCrit);
  }

  private ISearchResults<MessageOrRequest> findPageOfMessageOrRequestForIds(
      IdPage ids, PaginationCriteria<?> pgCrit) {
    List<Long> pageOfIds = ids.pageOfIds();
    if (pageOfIds.isEmpty()) {
      return new SearchResultsImpl<>(Collections.emptyList(), pgCrit, ids.total());
    }

    List<MessageOrRequest> pageOfMessageOrRequests =
        getSession()
            .createQuery("from MessageOrRequest where id in :ids", MessageOrRequest.class)
            .setParameterList("ids", pageOfIds)
            .list();
    // re-apply ids ordering
    pageOfMessageOrRequests.sort(Comparator.comparing(mor -> pageOfIds.indexOf(mor.getId())));
    initMessageOrRequestProperties(pageOfMessageOrRequests);

    return new SearchResultsImpl<>(pageOfMessageOrRequests, pgCrit, ids.total());
  }

  @Override
  public ISearchResults<MessageOrRequest> getActiveRequestsAndMessagesForUser(
      User user, PaginationCriteria<CommunicationTarget> pgCrit) {
    return getActiveRequestsAndMessagesForUser(
        user, pgCrit, MessageTypeFilter.DEFAULT_MESSAGE_LISTING);
  }

  @Override
  public ISearchResults<Notification> getNewNotificationsForUser(
      User user, PaginationCriteria<CommunicationTarget> pgCrit) {

    IdPage ids =
        findPageOfIds(
            "n.id",
            NEW_NOTIFICATION_FROM_WHERE,
            COMMUNICATION_TARGET_ALIAS,
            pgCrit,
            query ->
                query
                    .setParameter("status", CommunicationStatus.NEW)
                    .setParameter("userId", user.getId()));
    List<Long> pageOfIds = ids.pageOfIds();
    if (pageOfIds.isEmpty()) {
      return new SearchResultsImpl<>(Collections.emptyList(), pgCrit, ids.total());
    }

    List<Notification> notifications =
        getSession()
            .createQuery("from Notification where id in :ids", Notification.class)
            .setParameterList("ids", pageOfIds)
            .list();
    // re-apply ids ordering
    notifications.sort(Comparator.comparing(n -> pageOfIds.indexOf(n.getId())));
    return new SearchResultsImpl<>(notifications, pgCrit, ids.total());
  }

  @Override
  public int getUnreadNotificationCount(Long userId) {
    return Math.toIntExact(
        getSession()
            .createQuery("select count(distinct n.id) " + NEW_NOTIFICATION_FROM_WHERE, Long.class)
            .setParameter("status", CommunicationStatus.NEW)
            .setParameter("userId", userId)
            .uniqueResult());
  }

  @Override
  public NotificationStatus getNotificationStatus(User user) {
    int notificationCount = getUnreadNotificationCount(user.getId());
    Map<MessageType, Long> activeCounts = countActiveMessageOrRequestsByType(user);
    int messageCount = sumCounts(activeCounts, MessageTypeFilter.DEFAULT_MESSAGE_LISTING);
    int specialMessageCount = sumCounts(activeCounts, MessageTypeFilter.SPECIAL_MESSAGE_LISTING);
    return new NotificationStatus(notificationCount, messageCount, specialMessageCount);
  }

  /**
   * Counts the user's active messages and requests per message type. Each message has one type, so
   * the standard and special totals are sums of these counts and need only this one query.
   */
  private Map<MessageType, Long> countActiveMessageOrRequestsByType(User user) {
    Query<Object[]> query =
        getSession()
            .createQuery(
                "select mor.messageType, count(distinct mor.id) "
                    + activeMessageOrRequestFromWhere(null)
                    + " group by mor.messageType",
                Object[].class);
    activeMessageOrRequestParams(user, null).accept(query);
    List<Object[]> rows = query.getResultList();
    Map<MessageType, Long> counts = new EnumMap<>(MessageType.class);
    for (Object[] row : rows) {
      counts.put((MessageType) row[0], (Long) row[1]);
    }
    return counts;
  }

  private static int sumCounts(Map<MessageType, Long> counts, MessageTypeFilter filter) {
    return Math.toIntExact(
        filter.getWantedTypes().stream().mapToLong(type -> counts.getOrDefault(type, 0L)).sum());
  }

  @Override
  public CommunicationTarget updateStatus(
      Long userId, Long requestId, CommunicationStatus newStatus, String optionalMessage) {

    Query<CommunicationTarget> query =
        getSession()
            .createQuery(
                "from CommunicationTarget where recipient.id=:recipientId and"
                    + " communication.id=:requestId",
                CommunicationTarget.class);
    query.setParameter("recipientId", userId);
    query.setParameter("requestId", requestId);

    CommunicationTarget ct = query.uniqueResult();
    ct.setStatus(newStatus);
    ct.setLastStatusUpdate(new Date());
    // this just overwrites, may need to extend
    ct.setLastStatusUpdateMessage(optionalMessage);

    // update request status anyway
    MessageOrRequest mor =
        getSession()
            .createQuery("from MessageOrRequest where id = :id", MessageOrRequest.class)
            .setParameter("id", ct.getCommunication().getId())
            .uniqueResult();
    if (mor != null) {
      if (newStatus.isTerminated()) {
        RequestCompletionVotingPolicy completionPolicy = getCompletionPolicy();
        completionPolicy.voteCompleted(ct);
      } else {
        mor.setStatus(newStatus);
      }
    }
    return ct;
  }

  public boolean findExistingNotificationFor(
      User originator, NotificationType type, BaseRecord record, Set<String> potentialTargets) {
    Session session = getSession();
    StringBuilder hql =
        new StringBuilder(
            "select ct.id from Notification n join n.recipients ct "
                + "where ct.status = :status "
                + "and n.notificationType = :type "
                + "and n.originator.id = :originatorId");
    if (record != null) {
      hql.append(" and n.record.id = :recordId");
    }
    Query<Long> query =
        session
            .createQuery(hql.toString(), Long.class)
            .setParameter("status", CommunicationStatus.NEW)
            .setParameter("type", type)
            .setParameter("originatorId", originator.getId())
            .setMaxResults(1);
    if (record != null) {
      query.setParameter("recordId", record.getId());
    }
    return !query.list().isEmpty();
  }

  @Override
  public int markAllNotificationsAsRead(String subjectUserName, Date before) {
    String jpql =
        "UPDATE CommunicationTarget ct SET ct.status = :status, ct.lastStatusUpdate ="
            + " :lastStatusUpdate WHERE ct.status <> :completedStatus AND ct.id IN (SELECT ct.id"
            + " FROM CommunicationTarget ct JOIN ct.recipient r JOIN ct.communication c WHERE"
            + " r.username = :subjectUserName AND c.creationTime <= :before AND TYPE(c) ="
            + " :notificationType)";

    return getSession()
        .createMutationQuery(jpql)
        .setParameter("status", CommunicationStatus.COMPLETED)
        .setParameter("lastStatusUpdate", new Date())
        .setParameter("subjectUserName", subjectUserName)
        .setParameter("before", before)
        .setParameter("notificationType", Notification.class)
        .setParameter("completedStatus", CommunicationStatus.COMPLETED)
        .executeUpdate();
  }

  public int deleteReadNotificationsOlderThanDate(Date olderThan) {
    List<Long> notificationIds = findNotificationsForDeletion(olderThan);

    if (notificationIds.isEmpty()) {
      return 0;
    }

    // delete all CommunicationTargets associated with deletable Notifications
    String deleteTargetsJpql =
        "DELETE FROM CommunicationTarget ct WHERE ct.communication.id IN :notificationIds";
    getSession()
        .createQuery(deleteTargetsJpql)
        .setParameter("notificationIds", notificationIds)
        .executeUpdate();

    // delete the Notifications themselves
    String deleteNotificationsJpql = "DELETE FROM Notification n WHERE n.id IN :notificationIds";
    return getSession()
        .createQuery(deleteNotificationsJpql)
        .setParameter("notificationIds", notificationIds)
        .executeUpdate();
  }

  private List<Long> findNotificationsForDeletion(Date olderThan) {
    // notifications can be deleted when all recipients (CommunicationTargets) have marked as read
    String jpql =
        "SELECT n.id FROM Notification n "
            + "WHERE NOT EXISTS (SELECT ct FROM CommunicationTarget ct "
            + "                 WHERE ct.communication = n "
            + "                 AND ct.status <> :completedStatus) "
            + "AND EXISTS (SELECT ct FROM CommunicationTarget ct "
            + "           WHERE ct.communication = n "
            + "           AND ct.status = :completedStatus "
            + "           AND ct.lastStatusUpdate < :olderThan)";

    return getSession()
        .createQuery(jpql, Long.class)
        .setParameter("completedStatus", CommunicationStatus.COMPLETED)
        .setParameter("olderThan", olderThan)
        .getResultList();
  }

  public ISearchResults<MessageOrRequest> getAllSentAndReceivedSimpleMessagesForUser(
      User user, PaginationCriteria<CommunicationTarget> pgCrit) {

    String fromWhere =
        "from MessageOrRequest mor "
            + "join mor.recipients ct "
            + "join ct.recipient recipient "
            + "join mor.originator originator "
            + "where (recipient.id = :userId or originator.id = :userId) "
            + "and mor.messageType = :messageType";
    IdPage ids =
        findPageOfIds(
            "mor.id",
            fromWhere,
            COMMUNICATION_TARGET_ALIAS,
            pgCrit,
            query ->
                query
                    .setParameter("userId", user.getId())
                    .setParameter("messageType", MessageType.SIMPLE_MESSAGE));
    return findPageOfMessageOrRequestForIds(ids, pgCrit);
  }

  @Override
  public List<GroupMessageOrRequest> getGroupMessageByGroupId(Long groupId) {
    return getSession()
        .createQuery(
            "from GroupMessageOrRequest req where req.group.id=:id", GroupMessageOrRequest.class)
        .setParameter("id", groupId)
        .list();
  }

  @Override
  public List<ShareRecordMessageOrRequest> getShareRecordRequestsByUserId(Long userId) {
    return getSession()
        .createQuery(
            "from ShareRecordMessageOrRequest req where req.originator.id=:id",
            ShareRecordMessageOrRequest.class)
        .setParameter("id", userId)
        .list();
  }

  public Optional<Communication> getWithTargets(Long commId) {
    MessageOrRequest comm =
        (MessageOrRequest)
            getSession()
                .createQuery(
                    "from MessageOrRequest comm left join fetch comm.recipients where comm.id=:id")
                .setParameter("id", commId)
                .uniqueResult();
    if (comm == null) {
      return Optional.ofNullable(getNotificationWithTargets(commId));
    }
    return Optional.ofNullable(comm);
  }

  private Notification getNotificationWithTargets(Long commId) {
    Notification comm =
        getSession()
            .createQuery(
                "from Notification comm left join fetch comm.recipients where comm.id=:id",
                Notification.class)
            .setParameter("id", commId)
            .uniqueResult();
    return comm;
  }

  private RequestCompletionVotingPolicy getCompletionPolicy() {
    return new UnanimousVotingRequestCompletionUpdatePolicy(); // default
  }

  /**
   * Initialises the recipients and previous message of already loaded messages with one query each
   * (recipients in batches of {@link #RECIPIENT_FETCH_BATCH_SIZE} messages), relying on the
   * first-level cache to attach the fetched state to the loaded instances.
   */
  private void initMessageOrRequestProperties(List<MessageOrRequest> mors) {
    // RSPAC-1455. global messages have all users as recipients; initializing this collection
    // loads all users, to be avoided!
    List<Long> nonGlobalIds =
        mors.stream()
            .filter(mor -> !MessageType.GLOBAL_MESSAGE.equals(mor.getMessageType()))
            .map(MessageOrRequest::getId)
            .toList();
    for (List<Long> batch : ListUtils.partition(nonGlobalIds, RECIPIENT_FETCH_BATCH_SIZE)) {
      getSession()
          .createQuery(
              "select mor from MessageOrRequest mor"
                  + " left join fetch mor.recipients ct left join fetch ct.recipient"
                  + " where mor.id in :ids",
              MessageOrRequest.class)
          .setParameterList("ids", batch)
          .list();
    }

    // reading the id of an uninitialised proxy does not initialise it
    List<Long> previousIds =
        mors.stream()
            .map(MessageOrRequest::getPrevious)
            .filter(previous -> previous != null && !Hibernate.isInitialized(previous))
            .map(MessageOrRequest::getId)
            .distinct()
            .toList();
    if (!previousIds.isEmpty()) {
      getSession()
          .createQuery("from MessageOrRequest where id in :previousIds", MessageOrRequest.class)
          .setParameterList("previousIds", previousIds)
          .list();
    }
  }

  private static final String NEW_NOTIFICATION_FROM_WHERE =
      "from Notification n "
          + "join n.recipients ct "
          + "join ct.recipient recipient "
          + "join n.originator originator "
          + "where ct.status = :status and recipient.id = :userId";

  private static String activeMessageOrRequestFromWhere(MessageTypeFilter filter) {
    String fromWhere =
        "from MessageOrRequest mor "
            + "join mor.recipients ct "
            + "join ct.recipient recipient "
            + "join mor.originator originator "
            + "where ct.status not in :terminatedStates "
            + "and recipient.id = :userId";
    return filter == null ? fromWhere : fromWhere + " and mor.messageType in :messageTypes";
  }

  private static Consumer<Query<?>> activeMessageOrRequestParams(
      User user, MessageTypeFilter filter) {
    return query -> {
      query
          .setParameterList("terminatedStates", TERMINATED_STATES)
          .setParameter("userId", user.getId());
      if (filter != null) {
        query.setParameterList("messageTypes", filter.getWantedTypes());
      }
    };
  }

  /** One page of ordered communication ids and the total number of matching communications. */
  private record IdPage(List<Long> pageOfIds, long total) {}

  /**
   * Counts the distinct ids matching {@code fromWhere}, then fetches only the requested page of
   * them, sorted as {@link #buildOrderBy} describes for {@code sortAlias}. The page query is
   * skipped when the page starts past the last match, which also covers callers asking for every
   * result with an {@code Integer.MAX_VALUE} page size or an out-of-range page number.
   */
  private IdPage findPageOfIds(
      String idPath,
      String fromWhere,
      String sortAlias,
      PaginationCriteria<?> pgCrit,
      Consumer<Query<?>> bindParameters) {
    Query<Long> countQuery =
        getSession().createQuery("select count(distinct " + idPath + ") " + fromWhere, Long.class);
    bindParameters.accept(countQuery);
    long total = countQuery.uniqueResult();

    long pageSize = pgCrit.getResultsPerPage();
    long pageCount = pageSize == 0 ? 0 : (total + pageSize - 1) / pageSize;
    if (pgCrit.getPageNumber() >= pageCount) {
      return new IdPage(Collections.emptyList(), total);
    }
    long firstResult = pgCrit.getPageNumber() * pageSize;
    String hql =
        "select %s %s group by %s".formatted(idPath, fromWhere, idPath)
            + buildOrderBy(pgCrit, sortAlias, idPath);
    Query<Long> pageQuery =
        getSession()
            .createQuery(hql, Long.class)
            .setFirstResult((int) firstResult)
            .setMaxResults(pgCrit.getResultsPerPage());
    bindParameters.accept(pageQuery);
    return new IdPage(pageQuery.list(), total);
  }

  /**
   * Order clause for the id page query above, which groups the joined rows by {@code idPath}. The
   * {@code mor} alias is the MessageOrRequest itself; the {@code ct} alias is a CommunicationTarget
   * whose {@code communication} is the message. A communication can join several target rows (one
   * per recipient, or duplicates for one recipient) with different status update times, so each
   * communication sorts by its latest one, then by id.
   */
  private String buildOrderBy(PaginationCriteria<?> pgCrit, String alias, String idPath) {
    if (pgCrit == null) {
      return "";
    }
    CommunicationSort sort = CommunicationSort.fromRequest(pgCrit.getOrderBy());
    boolean viaTarget = COMMUNICATION_TARGET_ALIAS.equals(alias);
    String messagePath = viaTarget ? alias + ".communication" : alias;
    String path;
    switch (sort) {
      case SENDER:
        path = "originator.username";
        break;
      case REQUESTED_COMPLETION_DATE:
        path = messagePath + ".requestedCompletionDate";
        break;
      case LAST_STATUS_UPDATE:
        path = viaTarget ? alias + ".lastStatusUpdate" : messagePath + ".creationTime";
        break;
      case CREATION_TIME:
      default:
        path = messagePath + ".creationTime";
        break;
    }
    String direction = SortOrder.ASC.equals(pgCrit.getSortOrder()) ? "asc" : "desc";
    return " order by max(" + path + ") " + direction + ", " + idPath + " " + direction;
  }
}
