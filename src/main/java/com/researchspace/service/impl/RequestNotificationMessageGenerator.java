package com.researchspace.service.impl;

import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.UserGroup;
import com.researchspace.model.comms.CommunicationStatus;
import com.researchspace.model.comms.GroupMessageOrRequest;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageType;
import java.util.HashMap;
import java.util.Map;
import org.apache.velocity.app.VelocityEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.ui.velocity.VelocityEngineUtils;

/** Uses Velocity Templates to generate notification messages */
@Component
public class RequestNotificationMessageGenerator {
  Logger log = LoggerFactory.getLogger(getClass());
  @Autowired VelocityEngine velocity;

  public String generateMsgOnRequestStatusUpdate(
      User user,
      CommunicationStatus newStatus,
      CommunicationStatus originalStatus,
      MessageOrRequest mor) {
    if (mor.getMessageType().equals(MessageType.REQUEST_EXTERNAL_SHARE)) {
      Map<String, Object> model = new HashMap<String, Object>();
      model.put("user", user);
      model.put("status", newStatus);
      String groupName = "";
      for (UserGroup ug : user.getUserGroups()) {
        if (ug.getGroup().getMemberCount() == 0) { // this is the newly created group
          groupName = ug.getGroup().getDisplayName();
        }
      }
      model.put("groupType", "collaboration group");
      model.put("groupName", groupName);
      return processTemplate(model, "collabgroupInvitationAccepted.vm");
    }
    if (isJoinGroupMessage(mor.getMessageType())) {
      Map<String, Object> model = new HashMap<String, Object>();
      model.put("user", user);
      model.put("status", newStatus);
      if (mor instanceof GroupMessageOrRequest) {
        Group newGroup = ((GroupMessageOrRequest) mor).getGroup();
        model.put("groupType", newGroup.isProjectGroup() ? "project group" : "lab group");
        model.put("groupName", newGroup.getDisplayName());
      }
      return processTemplate(model, "groupInvitationAccepted.vm");
    }
    if (mor.getMessageType().equals(MessageType.REQUEST_RECORD_WITNESS)) {
      Map<String, Object> model = new HashMap<String, Object>();
      model.put("user", user);
      model.put("status", newStatus);
      model.put("record", mor.getRecord());
      return processTemplate(model, "recordWitnessed.vm");
    }
    // default
    return user.getDisplayName()
        + " updated request status, altered from "
        + originalStatus
        + " to "
        + newStatus;
  }

  private String processTemplate(Map<String, Object> model, String templateName) {
    try {
      String text =
          VelocityEngineUtils.mergeTemplateIntoString(velocity, templateName, "UTF-8", model);
      return text;
    } catch (Exception ex) {
      log.warn(ex.getMessage(), ex);
      return ex.getMessage();
    }
  }

  private boolean isJoinGroupMessage(MessageType messageType) {
    return messageType.equals(MessageType.REQUEST_JOIN_LAB_GROUP)
        || messageType.equals(MessageType.REQUEST_JOIN_PROJECT_GROUP);
  }
}
