package com.researchspace.service.impl;

import com.researchspace.comms.CommunicationTargetFinderPolicy;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.dao.CommunicationDao;
import com.researchspace.dao.RecordGroupSharingDao;
import com.researchspace.dao.UserDao;
import com.researchspace.model.AbstractUserOrGroupImpl;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.UserGroup;
import com.researchspace.model.comms.Communication;
import com.researchspace.model.comms.CommunicationStatus;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.GroupMessageOrRequest;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.comms.Notification;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.comms.ShareRecordMessageOrRequest;
import com.researchspace.model.comms.data.NotificationData;
import com.researchspace.model.dtos.MessageTypeFilter;
import com.researchspace.model.dtos.NotificationStatus;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Record;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.Broadcaster;
import com.researchspace.service.CommunicationManager;
import com.researchspace.service.CommunicationNotifyPolicy;
import com.researchspace.service.IMessageAndNotificationTracker;
import com.researchspace.service.NotificationConfig;
import com.researchspace.service.OperationFailedMessageGenerator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.shiro.authz.AuthorizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service("communicationManager")
@Profile(value = {"dev,run,prod"})
public class CommunicationManagerImpl implements CommunicationManager {

  private Logger log = LoggerFactory.getLogger(this.getClass());

  private @Autowired RecordGroupSharingDao groupshareRecordDao;
  private @Autowired CommunicationDao commDao;
  private @Autowired UserDao userDao;
  private @Autowired IMessageAndNotificationTracker notificnTracker;
  private @Autowired OperationFailedMessageGenerator authMsgGen;

  private List<Broadcaster> broadcasters = new ArrayList<>();

  public void setBroadcasters(List<Broadcaster> broadcasters) {
    this.broadcasters = broadcasters;
  }

  @Autowired
  @Qualifier("notifyOnceOnly")
  private CommunicationNotifyPolicy notificationPolicy;

  @Autowired
  @Qualifier("strictTargetFinderPolicy")
  private CommunicationTargetFinderPolicy commTargetFinderPolicy;

  @Override
  public Communication get(Long commId) {
    return commDao.get(commId);
  }

  @Override
  public Communication getIfOwnerOrTarget(Long commId, User subject) {
    Optional<Communication> commOpt = commDao.getWithTargets(commId);
    Communication comm =
        commOpt.orElseThrow(
            () ->
                new AuthorizationException(
                    authMsgGen.getFailedMessage(subject, "view Communication")));
    boolean isRecipient =
        comm.getRecipients().stream().anyMatch(ct -> ct.getRecipient().equals(subject));
    if (!(comm.getOriginator().equals(subject) || isRecipient)) {
      throw new AuthorizationException(authMsgGen.getFailedMessage(subject, "view Communication"));
    }
    return comm;
  }

  private Set<CommunicationTarget> createCommunicationTargetsForUsers(
      Set<User> recipientUsers, Communication mor) {
    Set<CommunicationTarget> targets = new HashSet<CommunicationTarget>();
    for (User u : recipientUsers) {
      CommunicationTarget ct = new CommunicationTarget();
      ct.setCommunication(mor);
      ct.setRecipient(u);
      targets.add(ct);
    }
    return targets;
  }

  public Set<CommunicationTarget> createCommunicationTargets(
      Set<String> recipientUserNames, Communication mor) {
    Set<CommunicationTarget> targets = new HashSet<>();
    for (String uname : recipientUserNames) {
      User trget = userDao.getUserByUsername(uname);
      CommunicationTarget ct = new CommunicationTarget();
      ct.setCommunication(mor);
      ct.setRecipient(trget);
      targets.add(ct);
    }
    return targets;
  }

  @Override
  public Set<User> getPotentialRecipientsOfRequest(
      Record record,
      MessageType messageType,
      String originatorUserName,
      String searchTerm,
      CommunicationTargetFinderPolicy targetFinderPolicy) {
    CommunicationTargetFinderPolicy policyToUse = this.commTargetFinderPolicy;
    if (targetFinderPolicy != null) {
      policyToUse = targetFinderPolicy;
    }
    User subject = userDao.getUserByUsername(originatorUserName);
    return policyToUse.findPotentialTargetsFor(messageType, record, searchTerm, subject);
  }

  @Override
  public ISearchResults<Notification> getNewNotificationsForUser(
      String userName, PaginationCriteria<CommunicationTarget> pgCrit) {
    return commDao.getNewNotificationsForUser(userDao.getUserByUsername(userName), pgCrit);
  }

  @Override
  public void markNotificationsAsRead(Set<Long> notificationIdsToMarkAsRead, String subject) {
    User user = userDao.getUserByUsername(subject);
    for (Long id : notificationIdsToMarkAsRead) {
      Notification notificn = (Notification) commDao.get(id);
      int altered = 0;
      for (CommunicationTarget ct : notificn.getRecipients()) {
        User target = ct.getRecipient();
        if (target.equals(user)) {
          ct.setStatus(CommunicationStatus.COMPLETED);
          ct.setLastStatusUpdate(new Date());
          altered++;
          break;
        }
      }
      // just save if status was really updated
      if (altered > 0) {
        commDao.save(notificn);
        notificnTracker.changeUserNotificationCount(user.getId(), altered * -1);
      }
    }
  }

  @Override
  public void markAllNotificationsAsRead(String username, Date since) {
    int removed = commDao.markAllNotificationsAsRead(username, since);
    User user = userDao.getUserByUsername(username);
    // this may not be strictly true, if new notifications have been generated
    // after the 'since' date, but will be pic
    notificnTracker.changeUserNotificationCount(user.getId(), removed * -1);
  }

  @Override
  public ISearchResults<MessageOrRequest> getActiveMessagesAndRequestsForUserTarget(
      String username, PaginationCriteria<CommunicationTarget> pgCrit) {
    return commDao.getActiveRequestsAndMessagesForUser(
        userDao.getUserByUsername(username), pgCrit, MessageTypeFilter.ALL_TYPES);
  }

  @Override
  public ISearchResults<MessageOrRequest> getActiveMessagesAndRequestsForUserTargetByType(
      String username, PaginationCriteria<CommunicationTarget> pgCrit, MessageTypeFilter filter) {
    return commDao.getActiveRequestsAndMessagesForUser(
        userDao.getUserByUsername(username), pgCrit, filter);
  }

  public void cancelRequest(String userNameCancelling, Long requestID, boolean quiet) {
    MessageOrRequest mor = (MessageOrRequest) get(requestID);
    if (!userNameCancelling.equals(mor.getOriginator().getUsername())) {
      String msg =
          String.format(
              " Attempt made to cancel request from non-originator: %s", userNameCancelling);
      log.warn(msg);
      throw new AuthorizationException(msg);
    }
    mor.setStatus(CommunicationStatus.CANCELLED);
    Date temTime = new Date();
    mor.setTerminationTime(temTime);
    for (CommunicationTarget ct : mor.getRecipients()) {
      ct.setStatus(CommunicationStatus.CANCELLED);
    }
    commDao.save(mor);
    if (!quiet) {
      Set<User> usersToNotify = new HashSet<User>();
      for (CommunicationTarget ct : mor.getRecipients()) {
        notificnTracker.updateMessageCount(ct.getRecipient().getId(), -1, mor.getMessageType());
        if (ct.getRecipient()
            .wantsNotificationFor(NotificationType.NOTIFICATION_REQUEST_STATUS_CHANGE)) {
          usersToNotify.add(ct.getRecipient());
        }
      }
      String sysmsg = "  Request cancelled by " + userNameCancelling;
      // Notification notification = DEAD STORE
      doCreateNotification(
          NotificationType.NOTIFICATION_REQUEST_STATUS_CHANGE,
          userNameCancelling,
          mor.getRecord(),
          usersToNotify,
          null,
          sysmsg);
    }
  }

  public ServiceOperationResult<String> cancelSharedRecordRequest(
      String userNameCancelling, Long requestID) {
    ShareRecordMessageOrRequest mor = (ShareRecordMessageOrRequest) get(requestID);
    String msg = "Request cancelled";
    // User userInSession = userDao.getUserByUserName(userNameCancelling);
    if (!userNameCancelling.equals(mor.getOriginator().getUsername())) {
      msg =
          String.format(
              "Attempt made to cancel recipient from non-authorised user %s", userNameCancelling);
      log.warn(msg);
      throw new AuthorizationException(msg);
    }

    mor.setStatus(CommunicationStatus.CANCELLED);
    Date temTime = new Date();
    mor.setTerminationTime(temTime);
    for (CommunicationTarget ct : mor.getRecipients()) {
      ct.setStatus(CommunicationStatus.CANCELLED);
    }

    commDao.save(mor);
    return new ServiceOperationResult<String>(msg, true);
  }

  public ServiceOperationResult<String> cancelRecipient(
      String sessionUsername, Long requestId, Long recipientId) {
    GroupMessageOrRequest mor = (GroupMessageOrRequest) get(requestId);
    boolean toUpdateRequest = true;
    boolean hasPermission = false;
    String msg = "Request cancelled";

    User userInSession = userDao.getUserByUsername(sessionUsername);
    Group group = mor.getGroup();
    for (UserGroup ug : userInSession.getUserGroups()) {
      if (ug.getGroup().equals(group)) {
        if (ug.isAdminRole() || ug.isPIRole() || ug.isGroupOwnerRole()) {
          hasPermission = true;
        }
      }
    }

    if (!hasPermission) {
      msg = " Unauthorised attempt made to cancel recipient from user" + sessionUsername;
      log.warn(msg);
      throw new AuthorizationException(msg);
    }

    for (CommunicationTarget ct : mor.getRecipients()) {
      if (ct.getId().equals(recipientId)) {
        ct.setStatus(CommunicationStatus.CANCELLED);
      }
      if (ct.getStatus().equals(CommunicationStatus.NEW)) {
        toUpdateRequest = false;
      }
    }

    if (toUpdateRequest) {
      mor.setStatus(CommunicationStatus.COMPLETED);
      Date terminationTime = new Date();
      mor.setTerminationTime(terminationTime);
    }

    commDao.save(mor);
    return new ServiceOperationResult<String>(msg, true);
  }

  @Override
  public void broadcast(Communication communication, Set<CommunicationTarget> targets) {
    for (Broadcaster broadcaster : broadcasters) {
      // run this in try/catch block so exceptions in any one broadcaster do not
      // throw all and exit loop.
      try {
        broadcaster.broadcast(communication);
      } catch (Exception e) {
        String msg =
            "General failure to broadcast from: "
                + broadcaster
                + ", message is ["
                + e.getMessage()
                + "]";
        log.warn(msg);
        systemNotify(
            NotificationType.PROCESS_FAILED,
            msg,
            communication.getOriginator().getUsername(),
            false);
      }
    }
  }

  /*
   * Creates, saves and broadcasts a notification
   */
  public Notification doCreateNotification(
      NotificationType type,
      String originator,
      BaseRecord record,
      Set<User> recipients,
      String optionalhumanmessage,
      String systemMsg) {
    User origUSer = userDao.getUserByUsername(originator);
    Notification notificn =
        createNotificationObject(type, null, record, optionalhumanmessage, origUSer, systemMsg);
    Set<CommunicationTarget> cts = createCommunicationTargetsForUsers(recipients, notificn);
    notificn.setRecipients(cts);
    commDao.save(notificn);
    broadcast(notificn, cts);

    // Notify the request's recipients that the status has changed
    for (CommunicationTarget ct : notificn.getRecipients()) {
      notificnTracker.changeUserNotificationCount(ct.getRecipient().getId(), 1);
    }
    return notificn;
  }

  /**
   * Creates a new notification object
   *
   * @param type
   * @param record - can be null
   * @param optionalmessage
   * @param piUser
   * @return
   */
  private Notification createNotificationObject(
      NotificationType type,
      NotificationData data,
      BaseRecord record,
      String optionalmessage,
      User u,
      String systemMsg) {
    Notification notificn = new Notification();
    notificn.setOriginator(u);
    notificn.setRecord(record);
    notificn.setMessage(optionalmessage);
    notificn.setNotificationType(type);
    notificn.setNotificationMessage(systemMsg);
    notificn.setNotificationDataObject(data);
    return notificn;
  }

  private Notification createNotificationForUser(
      String originatorUserName,
      BaseRecord record,
      Set<User> toNotify,
      String sysMsg,
      NotificationConfig config) {

    User originator = userDao.getUserByUsername(originatorUserName);
    Set<String> unames = new HashSet<>();
    for (User u : toNotify) {
      unames.add(u.getUsername());
    }
    // override with supplied policy
    if (config.getPolicyOverride() != null) {
      log.info("Policy not null");
    } else if (!notificationPolicy.shouldCreateNotificationFor(
        originator, config.getNotificationType(), record, unames)) {
      log.info(
          "Notification policy is preventing {} notification for originator {}",
          config.getNotificationType(),
          originator.getUsername());
      return null;
    }
    Notification notificn =
        createNotificationObject(
            config.getNotificationType(),
            config.getNotificationData(),
            record,
            null,
            originator,
            sysMsg);

    Set<CommunicationTarget> targets = createCommunicationTargetsForUsers(toNotify, notificn);
    notificn.setRecipients(targets);
    commDao.save(notificn);
    if (config.isBroadcast()) {
      broadcast(notificn, targets);
    }
    for (CommunicationTarget ct : targets) {
      notificnTracker.changeUserNotificationCount(ct.getRecipient().getId(), 1);
    }
    return notificn;
  }

  public int deleteReadNotificationsOlderThanDate(Date olderThan) {
    return commDao.deleteReadNotificationsOlderThanDate(olderThan);
  }

  public int deleteReadNotifications() {
    final int amount = 14;
    Date twoWeeksAgo = new Date(Instant.now().minus(amount, ChronoUnit.DAYS).toEpochMilli());

    int deleted = commDao.deleteReadNotificationsOlderThanDate(twoWeeksAgo);
    log.info("Deleting {} read notifications", deleted + "");
    return deleted;
  }

  @Override
  public ISearchResults<MessageOrRequest> getSentRequests(
      String originatorUserName, PaginationCriteria<MessageOrRequest> pgCrit) {
    User originator = userDao.getUserByUsername(originatorUserName);
    return commDao.getSentRequests(originator, pgCrit);
  }

  public void notify(User originator, BaseRecord record, NotificationConfig config, String sysMsg) {
    Set<User> toNotify = getPotentialListOfNotificationTargets(record, config);

    toNotify.remove(originator);
    Set<User> toRemove = new HashSet<>();
    for (User u : toNotify) {
      if (!u.wantsNotificationFor(config.getNotificationType())) {
        toRemove.add(u);
      }
    }
    toNotify.removeAll(toRemove);
    createNotificationForUser(originator.getUsername(), record, toNotify, sysMsg, config);
  }

  private Set<User> getPotentialListOfNotificationTargets(
      BaseRecord record, NotificationConfig cfg) {
    if (!cfg.getNotificationTargetsOverride().isEmpty()) {
      return cfg.getNotificationTargetsOverride();
    }
    Set<User> toNotify = new HashSet<User>();

    List<AbstractUserOrGroupImpl> grps =
        groupshareRecordDao.getUsersOrGroupsWithRecordAccess(record.getId());
    if (grps.isEmpty()) {
      log.info(
          "There are no sharees for  document {}, not sending any notifications", record.getId());
      return toNotify;
    }

    for (AbstractUserOrGroupImpl g : grps) {
      if (g.isGroup()) {
        toNotify.addAll(((Group) g).getMembers());
      } else {
        toNotify.add((User) g);
      }
    }
    return toNotify;
  }

  @Override
  public Notification systemNotify(
      NotificationType notificationType, String msg, String recipientName, boolean broadcast) {
    return systemNotify(notificationType, null, msg, recipientName, broadcast);
  }

  @Override
  public Notification systemNotify(
      NotificationType notificationType,
      NotificationData data,
      String msg,
      String recipientName,
      boolean broadcast) {
    User recipient = userDao.getUserByUsername(recipientName);
    if (!recipient.wantsNotificationFor(notificationType)) {
      return null;
    }

    NotificationConfig cfg =
        NotificationConfig.builder()
            .notificationType(notificationType)
            .notificationData(data)
            .broadcast(broadcast)
            .policyOverride(CommunicationNotifyPolicy.ALWAYS_NOTIFY)
            .recordAuthorisationRequired(false)
            .build();
    return createNotificationForUser(
        "sysadmin1", null, TransformerUtils.toSet(recipient), msg, cfg);
  }

  @Override
  public ISearchResults<MessageOrRequest> getAllSentAndReceivedSimpleMessagesForUser(
      String username, PaginationCriteria<CommunicationTarget> pgCrit) {
    User user = userDao.getUserByUsername(username);
    return commDao.getAllSentAndReceivedSimpleMessagesForUser(user, pgCrit);
  }

  @Override
  public NotificationStatus getNotificationStatus(User user) {
    return commDao.getNotificationStatus(user);
  }

  @Override
  public Notification updateNotificationMessage(Long notificationId, String newMsg) {
    Notification notification = (Notification) commDao.get(notificationId);
    notification.setNotificationMessage(newMsg);
    return (Notification) commDao.save(notification);
  }
}
