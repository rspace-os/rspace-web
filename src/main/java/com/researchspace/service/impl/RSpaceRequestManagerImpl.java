package com.researchspace.service.impl;

import com.researchspace.dao.CommunicationDao;
import com.researchspace.dao.UserDao;
import com.researchspace.model.User;
import com.researchspace.model.comms.CommunicationStatus;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.comms.MsgOrReqstCreationCfg;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.service.CommunicationManager;
import com.researchspace.service.IMessageAndNotificationTracker;
import com.researchspace.service.MessageOrRequestCreatorManager;
import com.researchspace.service.RSpaceRequestManager;
import com.researchspace.service.RSpaceRequestUpdateHandler;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.shiro.authz.AuthorizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service("requestStatusUpdateManager")
@Profile(value = {"dev,run,prod"})
public class RSpaceRequestManagerImpl implements RSpaceRequestManager {

  private Logger log = LoggerFactory.getLogger(RSpaceRequestManagerImpl.class);

  @Autowired private IMessageAndNotificationTracker notificnTracker;

  @Autowired private IPermissionUtils permUtils;

  @Autowired private RequestNotificationMessageGenerator msgGenerator;

  @Autowired private CommunicationDao commDao;

  @Autowired private MessageOrRequestCreatorManager messageCreator;

  @Autowired private CommunicationManager commMgr;

  @Autowired private UserDao userDao;

  private List<RSpaceRequestUpdateHandler> requestHandlers =
      new ArrayList<RSpaceRequestUpdateHandler>();

  public void setRequestHandlers(List<RSpaceRequestUpdateHandler> requestHandlers) {
    this.requestHandlers = requestHandlers;
  }

  @Override
  public MessageOrRequest updateStatus(
      String username, CommunicationStatus newStatus, Long requestID, String optionalMessage) {
    User authUser = userDao.getUserByUserName(username);
    MessageOrRequest mor = (MessageOrRequest) commDao.get(requestID);
    boolean enabled =
        mor.getRecipients().stream().anyMatch(ct -> ct.getRecipient().equals(authUser));
    // rspac2264
    if (!enabled && !authUser.equals(mor.getOriginator())) {
      String msg = String.format("%s is not authorised to update message %d", username, requestID);
      throw new AuthorizationException(msg);
    }
    CommunicationStatus originalStatus = mor.getStatus();
    if (mor.isCompleted()) {
      return mor; // it's already completed, return to avoid processing again
    }

    CommunicationTarget updatedTarget =
        commDao.updateStatus(authUser.getId(), requestID, newStatus, optionalMessage);
    // delegate to handlers for request-specific actions
    processRequestStatusUpdateActions(updatedTarget, authUser);

    // remove from recipient notification list if cancelled or completed.
    if (newStatus.isTerminated()) {
      notificnTracker.updateMessageCount(authUser.getId(), -1, mor.getMessageType());
    }
    Set<User> usersToNotify = new HashSet<User>();
    if (shouldNotifyOriginator(mor)) {
      usersToNotify.add(mor.getOriginator());
    }
    if (usersToNotify.isEmpty() || mor.isSimpleMessage()) {
      // original requestor does not want notification updates or there
      // is no-one listening or is someone just closing a message
      return mor;
    }

    BaseRecord concerning = mor.getRecord();
    // Notification notification = DEAD STORE
    String generatedMsg = generateNotificationOfUpdateMsg(authUser, newStatus, originalStatus, mor);
    commMgr.doCreateNotification(
        NotificationType.NOTIFICATION_REQUEST_STATUS_CHANGE,
        username,
        concerning,
        usersToNotify,
        optionalMessage,
        generatedMsg);
    return mor;
  }

  private String generateNotificationOfUpdateMsg(
      User user,
      CommunicationStatus newStatus,
      CommunicationStatus originalStatus,
      MessageOrRequest mor) {
    return msgGenerator.generateMsgOnRequestStatusUpdate(user, newStatus, originalStatus, mor);
  }

  /**
   * If originator has said preference should notified, or in certain fixed cases e.g., after
   * creation of a collaboration group.
   *
   * @param mor
   * @return
   */
  private boolean shouldNotifyOriginator(MessageOrRequest mor) {
    return mor.getOriginator()
            .wantsNotificationFor(NotificationType.NOTIFICATION_REQUEST_STATUS_CHANGE)
        || mor.getMessageType().equals(MessageType.REQUEST_EXTERNAL_SHARE)
        || mor.getMessageType().equals(MessageType.REQUEST_JOIN_LAB_GROUP)
        || mor.getMessageType().equals(MessageType.REQUEST_CREATE_LAB_GROUP)
        || mor.getMessageType().equals(MessageType.REQUEST_JOIN_PROJECT_GROUP);
  }

  private void processRequestStatusUpdateActions(CommunicationTarget updatedTarget, User subject) {
    if (!updatedTarget.getCommunication().isMessageOrRequest()) {
      return;
    }
    MessageOrRequest mor = (MessageOrRequest) updatedTarget.getCommunication();
    // delegate to the appropriate handler for the type.
    for (RSpaceRequestUpdateHandler handler : requestHandlers) {
      if (handler.handleRequest(mor.getMessageType())) {
        // run in try catch block so that loop continues even if
        // a single handler throws an exception.
        try {
          handler.handleMessageOrRequestUpdate(updatedTarget, subject);
        } catch (Exception e) {
          log.warn("Couldn't handle request processing: " + e.getMessage(), e);
        }
      }
    }
  }

  public MessageOrRequest replyToMessage(String responderUserName, Long originalMsgId, String msg) {
    MessageOrRequest originalMsg = (MessageOrRequest) commDao.get(originalMsgId);
    if (!originalMsg.isSimpleMessage()) {
      throw new IllegalStateException("ID was not a message ID");
    }
    if (MessageType.GLOBAL_MESSAGE.equals(originalMsg.getMessageType())) {
      throw new IllegalStateException("replying to Global Message is not allowed");
    }

    Set<String> respondents = new HashSet<String>();
    CommunicationTarget toMarkAsReplied = null;
    for (CommunicationTarget ct : originalMsg.getRecipients()) {
      respondents.add(ct.getRecipient().getUsername());
      if (ct.getRecipient().getUsername().equals(responderUserName)) {
        toMarkAsReplied = ct;
      }
    }
    // rspac2264
    if (!respondents.contains(responderUserName)) {
      String errorMsg =
          String.format(
              "User %s can't respond to message id %d  as they weren't a recipient.",
              responderUserName, originalMsgId);
      throw new AuthorizationException(errorMsg);
    }
    if (toMarkAsReplied != null) {
      toMarkAsReplied.setStatus(CommunicationStatus.REPLIED);
      toMarkAsReplied.setLastStatusUpdate(new Date());
      commDao.save(originalMsg);
    }

    respondents.remove(responderUserName); // don't reply to self
    respondents.add(originalMsg.getOriginator().getUsername()); // add original requestor
    MsgOrReqstCreationCfg config = new MsgOrReqstCreationCfg();
    config.setMessageType(MessageType.SIMPLE_MESSAGE);
    config.setPermUtils(permUtils);
    if (originalMsg.getRecord() != null) {
      config.setRecordId(originalMsg.getRecord().getId());
    }
    config.setOptionalMessage(msg);
    MessageOrRequest reply =
        messageCreator.createRequest(
            config, responderUserName, respondents, originalMsg.getId(), null);
    return reply;
  }

  /*
   * ==== for testing ====
   */
  public void setCommDao(CommunicationDao commDao) {
    this.commDao = commDao;
  }
}
