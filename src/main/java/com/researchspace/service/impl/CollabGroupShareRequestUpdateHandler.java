package com.researchspace.service.impl;

import com.researchspace.dao.CollaborationGroupTrackerDao;
import com.researchspace.model.CollabGroupCreationTracker;
import com.researchspace.model.Group;
import com.researchspace.model.GroupType;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.comms.CommunicationStatus;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.service.GroupManager;
import com.researchspace.service.IGroupCreationStrategy;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Manages the behaviour associated with the use of CollaborationGroup creation requests. This can
 * only handle {@link MessageType}s of type {@link MessageType#REQUEST_EXTERNAL_SHARE}
 */
public class CollabGroupShareRequestUpdateHandler extends AbstractRSpaceRequestUpdateHandler {

  @Autowired private CollaborationGroupTrackerDao groupTrackerDao;

  private IGroupCreationStrategy groupCreationStrategy;

  @Autowired
  public void setGroupCreationStrategy(IGroupCreationStrategy groupCreationStrategy) {
    this.groupCreationStrategy = groupCreationStrategy;
  }

  private GroupManager groupMgr;

  @Autowired
  public void setGroupMgr(GroupManager groupMgr) {
    this.groupMgr = groupMgr;
  }

  @Override
  public boolean handleRequest(MessageType messageType) {
    return MessageType.REQUEST_EXTERNAL_SHARE.equals(messageType);
  }

  @Override
  public void doHandleMessageOrRequestUpdateOnCompletion(
      CommunicationTarget updatedTarget, User subject) {
    MessageOrRequest mor = (MessageOrRequest) updatedTarget.getCommunication();
    CollabGroupCreationTracker tracker = groupTrackerDao.getByRequestId(mor);
    tracker.incrementReplies();
    if (isFirstAcceptanceReply(updatedTarget, tracker)) {
      Group collabGroup =
          groupCreationStrategy.createAndSaveGroup(
              tracker.getInitialGrpName(),
              updatedTarget.getRecipient(),
              subject,
              GroupType.COLLABORATION_GROUP,
              // the initial group members
              updatedTarget.getRecipient(),
              mor.getOriginator());
      tracker.setGroup(collabGroup);
      groupTrackerDao.save(tracker);
      //
      groupMgr.setRoleForUser(
          collabGroup.getId(), mor.getOriginator().getId(), RoleInGroup.PI.name(), subject);

    } else if (isSubsequentAcceptanceReply(updatedTarget, tracker)) {
      Group toAdd = tracker.getGroup();
      try {
        toAdd =
            groupMgr.addUserToGroup(
                updatedTarget.getRecipient().getUsername(), toAdd.getId(), RoleInGroup.PI);
        auditService.notify(new GenericEvent(subject, toAdd, AuditAction.WRITE));
      } catch (IllegalAddChildOperation e) {
        log.error(
            "Error adding user {} to group {}.",
            updatedTarget.getRecipient().getUsername(),
            toAdd,
            e);
      }
    }
    if (tracker.allReplied()) {
      // handle this extra functionality if need be..
      log.info("All replied");
    }
  }

  private boolean isSubsequentAcceptanceReply(
      CommunicationTarget updatedTarget, CollabGroupCreationTracker tracker) {
    return tracker.getNumReplies() >= 1
        && tracker.getGroup() != null
        && updatedTarget.getStatus().equals(CommunicationStatus.COMPLETED);
  }

  private boolean isFirstAcceptanceReply(
      CommunicationTarget updatedTarget, CollabGroupCreationTracker tracker) {
    return tracker.getNumReplies() == 1
        && tracker.getGroup() == null
        && updatedTarget.getStatus().equals(CommunicationStatus.COMPLETED);
  }
}
