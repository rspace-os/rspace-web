package com.researchspace.service;

import com.researchspace.comms.CommunicationTargetFinderPolicy;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.comms.Communication;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.comms.Notification;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.comms.data.NotificationData;
import com.researchspace.model.dtos.MessageTypeFilter;
import com.researchspace.model.dtos.NotificationStatus;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Record;
import com.researchspace.model.views.ServiceOperationResult;
import java.util.Date;
import java.util.Set;
import org.apache.shiro.authz.AuthorizationException;

/** Manages creation and update of internal {@link Communication} objects in RSpace */
public interface CommunicationManager {
  /**
   * Retrieves a {@link Communication} by its database id, not restricted by permission
   *
   * @param commId
   * @return A {@link Communication} object
   */
  Communication get(Long commId);

  /**
   * Gets Communication idf <code>subject</code> is the originator or a recipient
   *
   * @param commId
   * @param subject
   * @return A {@link Communication}
   * @throws AuthorizationException if A {@link Communication} with the given id does not exist or
   *     is unauthorised
   */
  Communication getIfOwnerOrTarget(Long commId, User subject);

  /**
   * Calculates who has permission to act on a request. Delegates to a {@link
   * CommunicationTargetFinderPolicy} implementation
   *
   * @param record A record associated with this request; may be <code>null</code>.
   * @param messageType Message Type
   * @param originatorUserName The username of the person sending the message.
   * @param searchTerm Limits recipients to those matching the search term; may be <code>null</code>
   * @param targetFinderPolicy An optional {@link CommunicationTargetFinderPolicy} which will
   *     override the default policy. Can be <code>null</code>
   * @return
   */
  Set<User> getPotentialRecipientsOfRequest(
      Record record,
      MessageType messageType,
      String originatorUserName,
      String searchTerm,
      CommunicationTargetFinderPolicy targetFinderPolicy);

  /**
   * Gets all notifications that are new since were last displayed in the UI and viewed by the user.
   *
   * @param userName
   * @param pgCrit A {@link PaginationCriteria} object to paginate the search.
   * @return
   */
  ISearchResults<Notification> getNewNotificationsForUser(
      String userName, PaginationCriteria<CommunicationTarget> pgCrit);

  /**
   * Marks the Notifications identified by argument notification ids as read, that belong to the
   * specified user.
   *
   * @param notificationIdsToMarkAsRead Set of notification ids
   * @param subject the authenticated user
   */
  void markNotificationsAsRead(Set<Long> notificationIdsToMarkAsRead, String subject);

  /**
   * Gets messages and requests that are not yet completed.
   *
   * @param username
   * @param pgCrit PageCriteria; can be <code>null</code>.
   * @return an {@link ISearchResults}
   */
  ISearchResults<MessageOrRequest> getActiveMessagesAndRequestsForUserTarget(
      String username, PaginationCriteria<CommunicationTarget> pgCrit);

  /**
   * Gets messages and requests that are not yet completed, restricted to a given set of message
   * types
   *
   * @param username
   * @param pgCrit PageCriteria; can be <code>null</code>.
   * @param filter A {@link MessageTypeFilter}
   * @return an {@link ISearchResults}
   */
  ISearchResults<MessageOrRequest> getActiveMessagesAndRequestsForUserTargetByType(
      String username, PaginationCriteria<CommunicationTarget> pgCrit, MessageTypeFilter filter);

  /**
   * Broadcasts communications to external delivery methods (e.g., email, SMS etc)
   *
   * @param communication The message/notification to be breoadcast
   * @param targets The set of targets to broadcast to.
   */
  void broadcast(Communication communication, Set<CommunicationTarget> targets);

  /**
   * MArks all notifications as read, since the supplied date.
   *
   * @param username The username whose notifications will be marked as read.
   * @param since the notification date after which notifications will be marked as read.
   */
  void markAllNotificationsAsRead(String username, Date since);

  /**
   * Get list of sent requests
   *
   * @param originatorUsername the username of the person who sent the requests
   * @param pgCrit
   * @return A Apginate {@link ISearchResults} of {@link MessageOrRequest} objects.
   */
  ISearchResults<MessageOrRequest> getSentRequests(
      String originatorUsername, PaginationCriteria<MessageOrRequest> pgCrit);

  /**
   * Cancels a request.
   *
   * @param userNameCancelling the username of the person cancelling the req
   * @param requestID
   * @param quiet whether to notify recipients that request was cancelled (<code>false</code>) or
   *     cancel silently without telling them (<code>true</code>).
   * @throws AuthorizationException if <code>userNameCancelling</code> is not the originator of the
   *     request.
   */
  void cancelRequest(String userNameCancelling, Long requestID, boolean quiet);

  /**
   * @param userNameCancelling
   * @param requestID
   * @return
   */
  ServiceOperationResult<String> cancelSharedRecordRequest(
      String userNameCancelling, Long requestID);

  /**
   * It cancels a recipient from the recipients set (Request). And update the request status if
   * there isn't any recipient with the status NEW (Pending). <strong>Request must be a
   * groupMessageRequest </strong>
   *
   * @param sessionUsername
   * @param requestId
   * @param recipientId
   * @throws AuthorizationException if <code>userNameCancelling</code> is not the originator of the
   *     request.
   */
  ServiceOperationResult<String> cancelRecipient(
      String sessionUsername, Long requestId, Long recipientId);

  /**
   * Deletes all notifications and their CommunicationTarget object from the DB. Notifications must
   * have been marked as read by all recipients before becoming eligible for deletion. This method
   * is not intended for being invoked from a controller but is for scheduled jobs. Old
   * notifications will still be retrievable from the audit table.
   *
   * @param olderThan A date after which read notifications will not be deleted.
   * @return The number of notifications deleted.
   */
  int deleteReadNotificationsOlderThanDate(Date olderThan);

  /**
   * Deletes all notifications and their CommunicationTarget object from the DB, using a hard-coded
   * delay ( 2 weeks ). Notifications must have been marked as read by all recipients before
   * becoming eligible for deletion. This method is not intended for being invoked from a controller
   * but is for scheduled jobs.
   *
   * @return The number of notifications deleted.
   * @see applicationContext-service.xml for configuration of scheduled jobs.
   */
  int deleteReadNotifications();

  /**
   * Notifies users who have permission and have chosen to receive notifications about a record.
   * <br>
   * The originator is excluded from the set of people to notify.
   *
   * @param originator
   * @param record
   * @param notificationType
   * @param sysMsg
   */
  void notify(User originator, BaseRecord record, NotificationConfig config, String sysMsg);

  /**
   * Notification from system to a user about non-record related issues. For example: System
   * messages, problems, completion of background processes.
   *
   * @param notificationType
   * @param msg
   * @param recipientName
   * @param broadcast Whether to broadcast externally - generally this should be true, but if you
   *     want to send an internal notification that a broadcast failed, this should be <code>false
   *     </code>, to avoid an endless loop.
   * @return
   */
  Notification systemNotify(
      NotificationType notificationType, String msg, String recipientName, boolean broadcast);

  /**
   * Notification from system to a user, same as {@link CommunicationManager.systemNotify}, but with
   * NotificationData parameter.
   */
  Notification systemNotify(
      NotificationType notificationType,
      NotificationData data,
      String msg,
      String recipientName,
      boolean broadcast);

  /**
   * Notification from originator to recipients.
   *
   * @param type The type of notification.
   * @param originator The user name of the person originating the notification.
   * @param record A Record that is associated with the notification; can be <code>null</code>.
   * @param recipients This is assumed to be a set of valid user (User) of people who currently have
   *     access to a record.
   * @param optionalhumanmessage An optional message of human origin
   * @param systemMsg An optional message generated by the notification generator.
   * @return the newly created, persisted {@link MessageOrRequest}.
   */
  Notification doCreateNotification(
      NotificationType type,
      String originator,
      BaseRecord record,
      Set<User> recipients,
      String optionalhumanmessage,
      String systemMsg);

  /**
   * When a message is sent, it may be sent to multiple people. We need to track each persons
   * handling of the message, which is performed by CommunicationTarget objects.
   *
   * <p>This method creates <em> but does not persist </em> a collection of {@link
   * CommunicationTarget} objects for a set of recipient user names.
   *
   * @param recipientUserNames
   * @param mor
   * @return A possibly empty but non-null Set of {@link CommunicationTarget}s
   */
  Set<CommunicationTarget> createCommunicationTargets(
      Set<String> recipientUserNames, Communication mor);

  /** Allows updating notification message. */
  Notification updateNotificationMessage(Long notificationId, String newMsg);

  /**
   * Gets all the sent and received simple messages for a specific user.
   *
   * @param username
   * @param pgCrit
   * @return
   */
  ISearchResults<MessageOrRequest> getAllSentAndReceivedSimpleMessagesForUser(
      String username, PaginationCriteria<CommunicationTarget> pgCrit);

  /**
   * Helper method to retrieve the new notifications, active messages and special messages count for
   * a specific user. This method should be call when the user login the first time to initialize
   * the tracker (MessageAndNotificationTracker).
   *
   * @param user
   * @return {@link NotificationStatus}
   */
  NotificationStatus getNotificationStatus(User user);
}
