package com.researchspace.service.cloud.impl;

import static java.util.stream.Collectors.toList;

import com.researchspace.model.Group;
import com.researchspace.model.GroupType;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.CreateGroupMessageOrRequest;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.service.cloud.CloudGroupManager;
import com.researchspace.service.cloud.CloudNotificationManager;
import com.researchspace.service.cloud.CommunityUserManager;
import com.researchspace.service.impl.AbstractRSpaceRequestUpdateHandler;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

/** Handler for requests of type MessageType.REQUEST_CREATE_LAB_GROUP */
@Slf4j
public class CreateLabGroupRequestHandler extends AbstractRSpaceRequestUpdateHandler {

  private @Autowired CloudGroupManager cloudGroupManager;
  private @Autowired CommunityUserManager cloudUserManager;
  private @Autowired CloudNotificationManager cloudNotificationManager;
  private @Autowired IPermissionUtils permissionUtils;

  @Override
  public boolean handleRequest(MessageType messageType) {
    return MessageType.REQUEST_CREATE_LAB_GROUP.equals(messageType);
  }

  @Override
  protected void doHandleMessageOrRequestUpdateOnCompletion(
      CommunicationTarget updatedTarget, User subject) {

    MessageOrRequest mor = (MessageOrRequest) updatedTarget.getCommunication();
    if (!(mor instanceof CreateGroupMessageOrRequest)) {
      return;
    }

    log.debug(
        "Handling request updated for {}  from user {}",
        updatedTarget.toString(),
        subject.getUsername());

    CreateGroupMessageOrRequest createGroupRequest = (CreateGroupMessageOrRequest) mor;
    try {
      User groupCreator = createGroupRequest.getTarget();
      if (!groupCreator.hasRole(Role.PI_ROLE)) {
        cloudGroupManager.promoteUserToPI(groupCreator, subject);
      }

      Group group =
          cloudGroupManager.createAndSaveGroup(
              createGroupRequest.getGroupName(),
              groupCreator,
              groupCreator,
              GroupType.LAB_GROUP,
              groupCreator);
      group = cloudGroupManager.addAdminToGroup(createGroupRequest.getCreator(), group);

      List<String> listEmails = createGroupRequest.getEmails();
      if (!listEmails.isEmpty() && !listEmails.get(0).isEmpty()) {
        List<User> usersList = cloudUserManager.createInvitedUserList(listEmails);
        group.setMemberString(usersList.stream().map(User::getUsername).collect(toList()));
        permissionUtils.refreshCache();
        cloudNotificationManager.sendJoinGroupRequest(groupCreator, group);

        for (User invitedUser : usersList) {
          try {
            cloudNotificationManager.sendJoinGroupInvitationEmail(
                groupCreator, invitedUser, group, null);
          } catch (Exception e) {
            log.warn("RSpace couldn't notify user by email", e);
            return;
          }
        }
      }
      auditService.notify(new GenericEvent(subject, group, AuditAction.WRITE));

    } catch (IllegalAddChildOperation e) {
      log.error("failed to create a labGroup [{}]", createGroupRequest.getGroupName(), e);
    }
  }
}
