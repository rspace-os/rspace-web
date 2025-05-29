package com.researchspace.dao.hibernate;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.dao.CommunicationDao;
import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.comms.*;
import com.researchspace.model.dtos.MessageTypeFilter;
import com.researchspace.model.dtos.NotificationStatus;
import com.researchspace.model.record.BaseRecord;
import java.util.*;
import org.hibernate.Criteria;
import org.hibernate.FetchMode;
import org.hibernate.Session;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.query.Query;
import org.springframework.stereotype.Repository;

/** CommunicationDAO (Data Access Object) implementation to retrieve/update communications. */
@Repository("communicationDao")
public class CommunicationDaoHibernateImpl extends GenericDaoHibernate<Communication, Long>
    implements CommunicationDao {
  public CommunicationDaoHibernateImpl() {
    super(Communication.class);
  }

  // COMUNICATION_TARGET table not aliased by client code, the CT alias will be prepended to any
  // order-by criteria passed in by client code
  private static final String COMMUNICATION_TARGET_ALIAS = "ct";

  static final Set<CommunicationStatus> TERMINATED_STATES =
      EnumSet.of(
          CommunicationStatus.CANCELLED,
          CommunicationStatus.REJECTED,
          CommunicationStatus.COMPLETED);

  @SuppressWarnings("unchecked")
  public ISearchResults<MessageOrRequest> getSentRequests(
      User user, PaginationCriteria<MessageOrRequest> pgCrit) {

    // 1st query - retrieve ids, correct ordered
    Session session = getSession();
    Criteria criteria =
        session
            .createCriteria(MessageOrRequest.class, "mor")
            .createAlias("mor.originator", "originator");
    criteria.add(Restrictions.not(Restrictions.in("status", TERMINATED_STATES)));
    criteria.add(
        Restrictions.not(
            Restrictions.in("messageType", new MessageType[] {MessageType.SIMPLE_MESSAGE})));
    criteria.add(Restrictions.eq("originator.id", user.getId()));
    criteria.setProjection(Projections.distinct(Projections.property("id")));
    criteria.setFetchMode("record", FetchMode.SELECT);
    criteria.setFetchMode("recipients", FetchMode.SELECT);
    criteria.setFetchMode("originator", FetchMode.SELECT);
    criteria.setFetchMode("previous", FetchMode.SELECT);
    criteria.setFetchMode("next", FetchMode.SELECT);
    applyOrderCriteria(pgCrit, criteria);
    List<Long> ids = criteria.list();

    return findPageOfMessageOrRequestForIds(ids, pgCrit);
  }

  @SuppressWarnings("unchecked")
  @Override
  public ISearchResults<MessageOrRequest> getActiveRequestsAndMessagesForUser(
      User user, PaginationCriteria<CommunicationTarget> pgCrit, MessageTypeFilter filter) {

    if (filter == null) {
      filter = MessageTypeFilter.ALL_TYPES;
    }

    // 1st query (applies order criteria)
    List<Long> ids = getActiveMessageOrRequestDistinctCTIds(user, filter, pgCrit);
    return findPageOfMessageOrRequestForIds(ids, pgCrit);
  }

  private ISearchResults<MessageOrRequest> findPageOfMessageOrRequestForIds(
      List<Long> ids, PaginationCriteria pgCrit) {

    // no need to search if there are no results
    if (ids.isEmpty()) {
      return createEmptyResults(pgCrit);
    }

    // retrieve page of requests
    List<Long> pageOfIds = getPageFromIdList(ids, pgCrit);
    List<MessageOrRequest> pageOfMessageOrRequests =
        getSession()
            .createQuery("from MessageOrRequest where id in :ids")
            .setParameterList("ids", pageOfIds)
            .list();
    // re-apply ids ordering
    pageOfMessageOrRequests.sort(Comparator.comparing(mor -> pageOfIds.indexOf(mor.getId())));
    initMessageOrRequestProperties(pageOfMessageOrRequests);

    return new SearchResultsImpl<>(pageOfMessageOrRequests, pgCrit, ids.size());
  }

  @Override
  public ISearchResults<MessageOrRequest> getActiveRequestsAndMessagesForUser(
      User user, PaginationCriteria<CommunicationTarget> pgCrit) {
    return getActiveRequestsAndMessagesForUser(
        user, pgCrit, MessageTypeFilter.DEFAULT_MESSAGE_LISTING);
  }

  @SuppressWarnings("unchecked")
  @Override
  public ISearchResults<Notification> getNewNotificationsForUser(
      User user, PaginationCriteria<CommunicationTarget> pgCrit) {

    // 1st query (applies order criteria)
    List<Long> ids = getNewNotificationDistinctCTIds(user, pgCrit);

    // No need to retrieve results if there are no results
    if (ids.isEmpty()) {
      return createEmptyResults(pgCrit);
    }

    // 2nd query, retrieve the notifications
    List<Long> pageOfIds = getPageFromIdList(ids, pgCrit);
    List<Notification> notifications =
        getSession()
            .createQuery("from Notification where id in :ids")
            .setParameterList("ids", pageOfIds)
            .list();
    // re-apply ids ordering
    notifications.sort(Comparator.comparing(n -> pageOfIds.indexOf(n.getId())));
    return new SearchResultsImpl<>(notifications, pgCrit, ids.size());
  }

  @Override
  public NotificationStatus getNotificationStatus(User user) {
    int notificationCount = getNewNotificationDistinctCTIds(user, null).size();
    int messageCount =
        getActiveMessageOrRequestDistinctCTIds(
                user, MessageTypeFilter.DEFAULT_MESSAGE_LISTING, null)
            .size();
    int specialMessageCount =
        getActiveMessageOrRequestDistinctCTIds(
                user, MessageTypeFilter.SPECIAL_MESSAGE_LISTING, null)
            .size();
    return new NotificationStatus(notificationCount, messageCount, specialMessageCount);
  }

  @SuppressWarnings("unchecked")
  @Override
  public CommunicationTarget updateStatus(
      Long userId, Long requestId, CommunicationStatus newStatus, String optionalMessage) {

    Query<CommunicationTarget> query =
        getSession()
            .createQuery(
                "from CommunicationTarget where recipient_id=:recipientId and"
                    + " communication_id=:requestId");
    query.setParameter("recipientId", userId);
    query.setParameter("requestId", requestId);

    CommunicationTarget ct = (CommunicationTarget) query.uniqueResult();
    ct.setStatus(newStatus);
    ct.setLastStatusUpdate(new Date());
    // this just overwrites, may need to extend
    ct.setLastStatusUpdateMessage(optionalMessage);

    // update request status anyway
    MessageOrRequest mor =
        (MessageOrRequest)
            getSession()
                .createQuery("from MessageOrRequest where id = :id")
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
    Criteria query =
        session
            .createCriteria(Notification.class)
            .createAlias("recipients", COMMUNICATION_TARGET_ALIAS);
    query.add(Restrictions.eq("ct.status", CommunicationStatus.NEW));
    if (record != null) {
      query.add(Restrictions.eq("record.id", record.getId()));
    }
    query.add(Restrictions.eq("notificationType", type));
    query.add(Restrictions.eq("originator.id", originator.getId()));
    query.setMaxResults(1);
    return !query.list().isEmpty();
  }

  @Override
  public int markAllNotificationsAsRead(String subjectUserName, Date before) {
    String jpql =
        "UPDATE CommunicationTarget ct SET ct.status = :status, ct.lastStatusUpdate ="
            + " :lastStatusUpdate WHERE ct.id IN (SELECT ct.id FROM CommunicationTarget ct JOIN"
            + " ct.recipient r JOIN ct.communication c WHERE r.username = :subjectUserName AND"
            + " c.creationTime <= :before AND TYPE(c) = :notificationType)";

    return getSession()
        .createQuery(jpql)
        .setParameter("status", CommunicationStatus.COMPLETED)
        .setParameter("lastStatusUpdate", new Date())
        .setParameter("subjectUserName", subjectUserName)
        .setParameter("before", before)
        .setParameter("notificationType", Notification.class)
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

  @SuppressWarnings("unchecked")
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
        .createQuery(jpql)
        .setParameter("completedStatus", CommunicationStatus.COMPLETED)
        .setParameter("olderThan", olderThan)
        .getResultList();
  }

  @SuppressWarnings({"unchecked"})
  public ISearchResults<MessageOrRequest> getAllSentAndReceivedSimpleMessagesForUser(
      User user, PaginationCriteria<CommunicationTarget> pgCrit) {

    // 1st query - retrieve ids, correctly ordered
    Criteria criteria = createMessageorRequestCriteria(getSession());
    criteria
        .add(
            Restrictions.disjunction()
                .add(Restrictions.eq("recipient.id", user.getId()))
                .add(Restrictions.eq("originator.id", user.getId())))
        .add(Restrictions.eq("communication.messageType", MessageType.SIMPLE_MESSAGE));
    applyOrderCriteriaForOrderByCommTargetWhenAliased(pgCrit, criteria);
    criteria.setProjection(Projections.distinct(Projections.property("id")));
    List<Long> ids = criteria.list();

    // no need to retrieve results if there are no results
    if (ids.isEmpty()) {
      return createEmptyResults(pgCrit);
    }

    // 2nd query
    List<Long> pageIds = getPageFromIdList(ids, pgCrit);
    List<MessageOrRequest> allSimpleMessages =
        getSession()
            .createQuery("from MessageOrRequest where messageType = :messageType and id in :ids")
            .setParameter("messageType", MessageType.SIMPLE_MESSAGE)
            .setParameterList("ids", pageIds)
            .list();
    // re-apply ordering from ids page
    allSimpleMessages.sort(Comparator.comparing(mor -> pageIds.indexOf(mor.getId())));
    initMessageOrRequestProperties(allSimpleMessages);

    return new SearchResultsImpl<>(allSimpleMessages, pgCrit, ids.size());
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
        (Notification)
            getSession()
                .createQuery(
                    "from Notification comm left join fetch comm.recipients where comm.id=:id")
                .setParameter("id", commId)
                .uniqueResult();
    return comm;
  }

  private RequestCompletionVotingPolicy getCompletionPolicy() {
    return new UnanimousVotingRequestCompletionUpdatePolicy(); // default
  }

  // Returns empty list so generics not needed
  @SuppressWarnings({"rawtypes", "unchecked"})
  private ISearchResults createEmptyResults(PaginationCriteria<?> pgCrit) {
    return new SearchResultsImpl<MessageOrRequest>(Collections.EMPTY_LIST, pgCrit, 0);
  }

  private void initMessageOrRequestProperties(List<MessageOrRequest> mors) {
    for (MessageOrRequest mor : mors) {
      if (mor.getPrevious() != null) {
        mor.getPrevious().getPriority(); // initialize
      }
      // RSPAC-1455. global messages have all users as recipients; initializing this collection
      // loads all users, to be avoided!
      if (!MessageType.GLOBAL_MESSAGE.equals(mor.getMessageType())) {
        mor.getRecipients().size(); // initialize
      }
    }
  }

  private Criteria createNotificationCriteria(Session session) {
    return session
        .createCriteria(Notification.class, "communication")
        .createAlias("communication.recipients", COMMUNICATION_TARGET_ALIAS)
        .createAlias("ct.recipient", "recipient")
        .createAlias("communication.originator", "originator");
  }

  @SuppressWarnings("unchecked")
  private List<Long> getNewNotificationDistinctCTIds(
      User user, PaginationCriteria<CommunicationTarget> pgCrit) {

    Criteria criteria = createNotificationCriteria(getSession());
    criteria.add(Restrictions.eq("ct.status", CommunicationStatus.NEW));
    criteria.add(Restrictions.eq("recipient.id", user.getId()));
    applyOrderCriteriaForOrderByCommTargetWhenAliased(pgCrit, criteria);
    criteria.setProjection(Projections.id());

    return criteria.list();
  }

  private Criteria createMessageorRequestCriteria(Session session) {
    return session
        .createCriteria(MessageOrRequest.class, "communication")
        .createAlias("communication.recipients", COMMUNICATION_TARGET_ALIAS)
        .createAlias("ct.recipient", "recipient")
        .createAlias("communication.originator", "originator");
  }

  @SuppressWarnings("unchecked")
  private List<Long> getActiveMessageOrRequestDistinctCTIds(
      User user, MessageTypeFilter filter, PaginationCriteria<CommunicationTarget> pgCrit) {

    Criteria criteria = createMessageorRequestCriteria(getSession());
    criteria.add(Restrictions.not(Restrictions.in("ct.status", TERMINATED_STATES)));
    criteria.add(Restrictions.eq("recipient.id", user.getId()));
    if (filter != null) {
      criteria.add(Restrictions.in("communication.messageType", filter.getWantedTypes()));
    }
    applyOrderCriteriaForOrderByCommTargetWhenAliased(pgCrit, criteria);
    criteria.setProjection(Projections.id());

    return criteria.list();
  }

  private void applyOrderCriteria(PaginationCriteria<?> pgCrit, Criteria criteria) {
    if (pgCrit != null) {
      DatabasePaginationUtils.addOrderToHibernateCriteria(pgCrit, criteria);
    }
  }

  private void applyOrderCriteriaForOrderByCommTargetWhenAliased(
      PaginationCriteria<?> pgCrit, Criteria criteria) {
    if (pgCrit != null && pgCrit.getOrderBy() != null) {
      if (pgCrit.getOrderBy().indexOf(".") == -1) {
        pgCrit.setOrderBy(COMMUNICATION_TARGET_ALIAS + "." + pgCrit.getOrderBy());
      }
      DatabasePaginationUtils.addOrderToHibernateCriteria(pgCrit, criteria);
    }
  }

  private List<Long> getPageFromIdList(List<Long> ids, PaginationCriteria<?> pgCrit) {
    int pageSize = pgCrit.getResultsPerPage();
    int fromIndex = (int) (pgCrit.getPageNumber() * pageSize);
    if (fromIndex >= ids.size()) {
      return Collections.emptyList();
    }
    return ids.subList(fromIndex, Math.min(fromIndex + pageSize, ids.size()));
  }
}
