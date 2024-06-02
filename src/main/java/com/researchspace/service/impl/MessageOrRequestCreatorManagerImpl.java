package com.researchspace.service.impl;

import com.researchspace.core.util.TransformerUtils;
import com.researchspace.dao.CommunicationDao;
import com.researchspace.dao.GroupDao;
import com.researchspace.dao.RecordDao;
import com.researchspace.dao.UserDao;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.comms.MsgOrReqstCreationCfg;
import com.researchspace.model.comms.RequestFactory;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.views.RSpaceDocView;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.BaseRecordManager;
import com.researchspace.service.CommunicationManager;
import com.researchspace.service.IMessageAndNotificationTracker;
import com.researchspace.service.MessageOrRequestCreatorManager;
import com.researchspace.service.OperationFailedMessageGenerator;
import com.researchspace.service.RSpaceRequestOnCreateHandler;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class MessageOrRequestCreatorManagerImpl implements MessageOrRequestCreatorManager {

  private @Autowired CommunicationDao commDao;
  private @Autowired IPermissionUtils permUtils;
  private @Autowired GroupDao grpDao;
  private @Autowired RecordDao recordDao;
  private @Autowired UserDao userDao;
  private @Autowired CommunicationManager commMgr;
  private @Autowired IMessageAndNotificationTracker notificnTracker;
  private @Autowired RequestFactory reqFactory;
  private @Autowired OperationFailedMessageGenerator authMsgGenerator;
  private @Autowired IPropertyHolder properties;
  private @Autowired BaseRecordManager baseRecordManager;

  private List<RSpaceRequestOnCreateHandler> requestCreateHandlers =
      new ArrayList<RSpaceRequestOnCreateHandler>();

  public void setRequestHandlers(List<RSpaceRequestOnCreateHandler> requestHandlers) {
    this.requestCreateHandlers = requestHandlers;
  }

  @Override
  public MessageOrRequest createRequest(
      MsgOrReqstCreationCfg reqCmnd, String originatorUserName, Set<String> recipientUserNames) {
    return createRequest(reqCmnd, originatorUserName, recipientUserNames, null, null);
  }

  /**
   * Creates and persists a {@link MessageOrRequest}, and broadcasts it to recipients.
   *
   * @param reqCmnd Configuration
   * @param originatorUserName The username of the person sending the message.
   * @param recipientUserNames . This is assumed to be a set of valid user names of people who
   *     currently have access to a record, if the record is not null.
   * @param prevMessageId An optional identifier for a previous request, can be null.
   * @param requestedCompletionDate An optional completion date
   * @return the newly created, persisted {@link MessageOrRequest}.
   */
  public MessageOrRequest createRequest(
      MsgOrReqstCreationCfg reqCmnd,
      String originatorUserName,
      Set<String> recipientUserNames,
      Long prevMessageId,
      Date requestedCompletionDate) {

    User user = userDao.getUserByUserName(originatorUserName);
    checkPerms(reqCmnd, user);
    MessageOrRequest mor = createMessageOrRequestObject(reqCmnd, originatorUserName);
    mor.setRequestedCompletionDate(requestedCompletionDate);
    if (prevMessageId != null) {
      MessageOrRequest previous = (MessageOrRequest) commDao.get(prevMessageId);
      MessageOrRequest currentMostRecent = previous;
      // there may have been other replies before this one..
      // need to ensure this message is added as latest
      while ((currentMostRecent.getNext()) != null) {
        currentMostRecent = currentMostRecent.getNext();
      }
      mor.setPreviousMessage(currentMostRecent);
      mor.setLatest(true);
      currentMostRecent.setLatest(false);
      currentMostRecent.setNextMessage(mor);
      commDao.save(currentMostRecent);
    }

    commDao.save(mor);

    Set<CommunicationTarget> targets = commMgr.createCommunicationTargets(recipientUserNames, mor);
    mor.setRecipients(targets);
    processRequestSetup(mor, user);
    commDao.save(mor);
    commMgr.broadcast(mor, targets);

    for (CommunicationTarget ct : targets) {
      notificnTracker.updateMessageCount(ct.getRecipient().getId(), 1, mor.getMessageType());
    }

    return mor;
  }

  // package scoped for testing
  void checkPerms(MsgOrReqstCreationCfg reqCmnd, User user) {
    boolean permitted = permUtils.isPermitted(reqCmnd.getMessageType(), PermissionType.READ, user);
    if (isJoinGroupRequest(reqCmnd)) {
      Group toJoin = grpDao.get(reqCmnd.getGroupId());
      permitted = permitted && permUtils.isPermitted(toJoin, PermissionType.WRITE, user);
      // RSPAC-1999 enable any member to invite another user
      if (!permitted && properties.isCloud()) {
        permitted = toJoin.hasMember(user);
      }
    }

    if (!permitted) {
      throw new AuthorizationException(
          authMsgGenerator.getFailedMessage(
              user, String.format("create a request of type %s", reqCmnd.getMessageType())));
    }
  }

  private boolean isJoinGroupRequest(MsgOrReqstCreationCfg reqCmnd) {
    return reqCmnd.getGroupId() != null
        && (MessageType.REQUEST_JOIN_LAB_GROUP.equals(reqCmnd.getMessageType())
            || MessageType.REQUEST_JOIN_PROJECT_GROUP.equals(reqCmnd.getMessageType()));
  }

  MessageOrRequest createMessageOrRequestObject(
      MsgOrReqstCreationCfg command, String originatorUserName) {

    User originator = userDao.getUserByUserName(originatorUserName);
    RSpaceDocView record = null;
    if (command.getRecordId() != null) {
      record = isRecord(originator, command.getRecordId());
      // don't share folder
      if (MessageType.REQUEST_SHARE_RECORD.equals(command.getMessageType())
          && isFolderButNotNotebook(record)) {
        throw new AuthorizationException(
            authMsgGenerator.getFailedMessage(
                originator, String.format("share folder %s", record.getName())));
      }
    }
    Group grp = null;
    if (command.getGroupId() != null) {
      grp = grpDao.get(command.getGroupId());
    }

    BaseRecord recordProxyOrNull =
        (command.getRecordId() != null)
            ? baseRecordManager.get(command.getRecordId(), originator)
            : null;
    return reqFactory.createMessageOrRequestObject(command, recordProxyOrNull, grp, originator);
  }

  private boolean isFolderButNotNotebook(RSpaceDocView record) {
    return record.getType().contains(RecordType.FOLDER.name())
        && !record.getType().contains(RecordType.NOTEBOOK.name());
  }

  // convenience method to ascertain if we're looking for a record or a folder
  private RSpaceDocView isRecord(User user, Long id) {
    List<RSpaceDocView> docView = recordDao.getRecordViewsById(TransformerUtils.toSet(id));
    return docView.get(0);
  }

  // delegate to handler for request-type s-e
  private void processRequestSetup(MessageOrRequest mor, User subject) {
    for (RSpaceRequestOnCreateHandler handler : requestCreateHandlers) {
      if (handler.handleRequest(mor.getMessageType())) {
        handler.handleMessageOrRequestSetUp(mor, subject);
      }
    }
  }
}
