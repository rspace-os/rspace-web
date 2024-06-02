package com.researchspace.service.impl;

import com.researchspace.model.Group;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.GroupMessageOrRequest;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.service.GroupManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/** Handler for requests of type MessageType.REQUEST_JOIN_EXISTING_COLLAB_GROUP */
public class JoinExistingCollGroupRequestUpdateHandler extends AbstractRSpaceRequestUpdateHandler {
  private Logger logger = LoggerFactory.getLogger(JoinExistingCollGroupRequestUpdateHandler.class);
  private GroupManager groupMgr;

  @Autowired
  public void setGroupMgr(GroupManager groupMgr) {
    this.groupMgr = groupMgr;
  }

  @Override
  public boolean handleRequest(MessageType messageType) {
    return MessageType.REQUEST_JOIN_EXISTING_COLLAB_GROUP.equals(messageType);
  }

  @Override
  protected void doHandleMessageOrRequestUpdateOnCompletion(
      CommunicationTarget updatedTarget, User subject) {
    MessageOrRequest mor = (MessageOrRequest) updatedTarget.getCommunication();
    if (!(mor instanceof GroupMessageOrRequest)) {
      return;
    }
    logger.debug(
        "Handling request updated for {} from user }{}",
        updatedTarget.toString(),
        subject.getUsername());

    GroupMessageOrRequest grReq = (GroupMessageOrRequest) mor;
    if (grReq.getGroup() != null) {
      try {
        Group group =
            groupMgr.addUserToGroup(
                subject.getUsername(), grReq.getGroup().getId(), RoleInGroup.PI);
        auditService.notify(new GenericEvent(subject, group, AuditAction.WRITE));
      } catch (IllegalAddChildOperation e) {
        logger.error(
            "failed to add [{}] to group [{}]",
            subject.getUsername(),
            grReq.getGroup().getUniqueName(),
            e);
      }
    }
  }
}
