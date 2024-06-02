package com.researchspace.dao;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.comms.Communication;
import com.researchspace.model.comms.CommunicationStatus;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.GroupMessageOrRequest;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.Notification;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.comms.RequestCompletionVotingPolicy;
import com.researchspace.model.comms.ShareRecordMessageOrRequest;
import com.researchspace.model.dtos.MessageTypeFilter;
import com.researchspace.model.dtos.NotificationStatus;
import com.researchspace.model.record.BaseRecord;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** DAO interface for creating record from templates */
public interface CommunicationDao extends GenericDao<Communication, Long> {

  /**
   * Gets all the new notifications (type = N) for a specific user, which they are in a
   * communication status = NEW.
   *
   * @param user
   * @param pgCrit
   * @return
   */
  ISearchResults<Notification> getNewNotificationsForUser(
      User user, PaginationCriteria<CommunicationTarget> pgCrit);

  /**
   * Gets all active requests and messages <em>received</em> by the user, that are not in the
   * 'completed' state, using the default MessageType filter.
   *
   * @param user
   * @param pgCrit
   * @return
   */
  ISearchResults<MessageOrRequest> getActiveRequestsAndMessagesForUser(
      User user, PaginationCriteria<CommunicationTarget> pgCrit);

  /**
   * Lists active messages, including only messages of the type included by the supplied <code>
   * MessageTypeFilter</code>
   *
   * @param user
   * @param pgCrit
   * @param filter An optional {@link MessageTypeFilter}; if this is <code>null</code>, all message
   *     types are returned.
   * @return return possibly empty but non-null {@link ISearchResults}
   * @see #getActiveRequestsAndMessagesForUser(User, PaginationCriteria)
   */
  ISearchResults<MessageOrRequest> getActiveRequestsAndMessagesForUser(
      User user, PaginationCriteria<CommunicationTarget> pgCrit, MessageTypeFilter filter);

  /**
   * Updates the target's communication status. If the {@link RequestCompletionVotingPolicy}
   * determines the whole message or request is completed, then the the status of the main {@link
   * MessageOrRequest} object is updated.
   *
   * @param userId
   * @param requestId
   * @param newStatus
   * @param optionalMessage
   * @return The {@link CommunicationTarget} representing the user updating the status. I.e., the
   *     user who was a original recipient of the message.
   */
  CommunicationTarget updateStatus(
      Long userId, Long requestId, CommunicationStatus newStatus, String optionalMessage);

  /**
   * Looks to see if there is an existing notification of the same type.
   *
   * @param originator The user whose operation has generated the notification
   * @param type
   * @param record optional, can be <code>null</code>
   * @param potentialTargets List of potential recipients
   * @return
   */
  boolean findExistingNotificationFor(
      User originator, NotificationType type, BaseRecord record, Set<String> potentialTargets);

  /**
   * @param username
   * @param before
   * @return the number of notifications removed
   */
  int markAllNotificationsAsRead(String username, Date before);

  /**
   * Gets all the messages or request by specific user, which they are not in the terminated states
   * (cancelled, rejected or completed) and they are not simple message.
   *
   * @param originator
   * @param pgCrit
   * @return
   */
  ISearchResults<MessageOrRequest> getSentRequests(
      User originator, PaginationCriteria<MessageOrRequest> pgCrit);

  /**
   * @param olderThan
   * @return
   */
  int deleteReadNotificationsOlderThanDate(Date olderThan);

  /**
   * Gets a list of {@link GroupMessageOrRequest} relating to the specified group id.
   *
   * @param groupId
   * @return
   */
  List<GroupMessageOrRequest> getGroupMessageByGroupId(Long groupId);

  /**
   * This method is used in MessageArchiveDataHandler to archive the messages for a specific user.
   * All the sent and received simple messages (type = M) by the specific user will be retrieved. In
   * this case, we don't check the communication status.
   *
   * @param user
   * @param pgCrit
   * @return
   */
  ISearchResults<MessageOrRequest> getAllSentAndReceivedSimpleMessagesForUser(
      User user, PaginationCriteria<CommunicationTarget> pgCrit);

  /**
   * @param userId
   * @return
   */
  List<ShareRecordMessageOrRequest> getShareRecordRequestsByUserId(Long userId);

  /**
   * Helper method to retrieve the new notifications, active messages and special messages count for
   * a specific user. This method should be call when the user login the first time to initialize
   * the tracker (MessageAndNotificationTracker).
   *
   * @param user
   * @return {@link NotificationStatus}
   */
  NotificationStatus getNotificationStatus(User user);

  /**
   * Gets a communication with eagerly loaded targets
   *
   * @param commId
   * @return
   */
  Optional<Communication> getWithTargets(Long commId);
}
