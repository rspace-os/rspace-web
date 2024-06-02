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
import com.researchspace.model.record.Folder;
import com.researchspace.service.AutoshareManager;
import com.researchspace.service.GroupManager;
import com.researchspace.service.UserManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Handler for requests of type MessageType.REQUEST_JOIN_LAB_GROUP & REQUEST_JOIN_PROJECT_GROUP */
public class JoinGroupRequestUpdateHandler extends AbstractRSpaceRequestUpdateHandler {
  private final Logger logger = LoggerFactory.getLogger(JoinGroupRequestUpdateHandler.class);

  @Autowired private GroupManager groupMgr;
  @Autowired private UserManager userManager;
  @Autowired private AutoshareManager autoshareManager;

  @Override
  public boolean handleRequest(MessageType messageType) {
    return MessageType.REQUEST_JOIN_LAB_GROUP.equals(messageType)
        || MessageType.REQUEST_JOIN_PROJECT_GROUP.equals(messageType);
  }

  @Override
  protected void doHandleMessageOrRequestUpdateOnCompletion(
      CommunicationTarget updatedTarget, User subject) {
    MessageOrRequest mor = (MessageOrRequest) updatedTarget.getCommunication();

    if (!(mor instanceof GroupMessageOrRequest)) {
      return;
    }
    logger.debug(
        "Handling request updated for {} from user {}",
        updatedTarget.toString(),
        subject.getUsername());

    GroupMessageOrRequest grrRqst = (GroupMessageOrRequest) mor;
    if (grrRqst.getGroup() != null) {
      try {
        Group group =
            groupMgr.addUserToGroup(
                subject.getUsername(), grrRqst.getGroup().getId(), RoleInGroup.DEFAULT);
        auditService.notify(new GenericEvent(subject, group, AuditAction.WRITE));

        if (!subject.isPI() && group.isAutoshareEnabled()) {
          subject = userManager.getUserByUsername(subject.getUsername(), true);
          Group updatedGroup = groupMgr.enableAutoshareForUser(subject, group.getId());
          Folder autoshareFolder = groupMgr.createAutoshareFolder(subject, updatedGroup);
          User updatedSubject = userManager.getUserByUsername(subject.getUsername(), true);

          // We are in a transaction. asyncBulkShareAllRecord is an Async task,
          // that needs all previous changes to be committed
          executeAfterTransactionCommits(
              () ->
                  autoshareManager.asyncBulkShareAllRecords(
                      updatedSubject, updatedGroup, autoshareFolder));
        }
      } catch (Exception e) {
        logger.error(
            "failed to add [{}] to {} [{}].",
            subject.getUsername(),
            grrRqst.getGroup().getGroupType().name(),
            grrRqst.getGroup().getUniqueName(),
            e);
      }
    }
  }

  /*
  https://stackoverflow.com/questions/51833306/using-async-inside-a-transaction-in-spring-application
   */
  private void executeAfterTransactionCommits(Runnable task) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void suspend() {}

          @Override
          public void resume() {}

          @Override
          public void flush() {}

          @Override
          public void beforeCommit(boolean b) {}

          @Override
          public void beforeCompletion() {}

          @Override
          public void afterCommit() {
            task.run();
          }

          @Override
          public void afterCompletion(int i) {}
        });
  }
}
