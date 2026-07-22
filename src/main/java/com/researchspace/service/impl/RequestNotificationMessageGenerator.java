package com.researchspace.service.impl;

import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.UserGroup;
import com.researchspace.model.comms.CommunicationStatus;
import com.researchspace.model.comms.GroupMessageOrRequest;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageType;
import com.researchspace.service.LocaleBoundMessages;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.UserLocaleService;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.spring.VelocityEngineUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Uses Velocity Templates to generate notification messages */
@Component
public class RequestNotificationMessageGenerator {
  Logger log = LoggerFactory.getLogger(getClass());
  @Autowired VelocityEngine velocity;
  @Autowired private MessageSourceUtils messages;
  @Autowired private UserLocaleService userLocaleService;

  public String generateMsgOnRequestStatusUpdate(
      User user,
      CommunicationStatus newStatus,
      CommunicationStatus originalStatus,
      MessageOrRequest mor) {
    Locale recipientLocale = userLocaleService.getLocaleFor(user);
    if (mor.getMessageType().equals(MessageType.REQUEST_EXTERNAL_SHARE)) {
      Map<String, Object> model = new HashMap<>();
      model.put("user", user);
      model.put("status", newStatus);
      String groupName = "";
      for (UserGroup ug : user.getUserGroups()) {
        if (ug.getGroup().getMemberCount() == 0) { // this is the newly created group
          groupName = ug.getGroup().getDisplayName();
        }
      }
      model.put(
          "groupType",
          messages.getMessage("email.group.type.collaborationGroup", null, recipientLocale));
      model.put("groupName", groupName);
      return processTemplate(model, "collabgroupInvitationAccepted.vm", recipientLocale);
    }
    if (isJoinGroupMessage(mor.getMessageType())) {
      Map<String, Object> model = new HashMap<>();
      model.put("user", user);
      model.put("status", newStatus);
      if (mor instanceof GroupMessageOrRequest) {
        Group newGroup = ((GroupMessageOrRequest) mor).getGroup();
        model.put(
            "groupType",
            messages.getMessage(
                newGroup.isProjectGroup()
                    ? "email.group.type.projectGroup"
                    : "email.group.type.labGroup",
                null,
                recipientLocale));
        model.put("groupName", newGroup.getDisplayName());
      }
      return processTemplate(model, "groupInvitationAccepted.vm", recipientLocale);
    }
    if (mor.getMessageType().equals(MessageType.REQUEST_RECORD_WITNESS)) {
      Map<String, Object> model = new HashMap<>();
      model.put("user", user);
      model.put("status", newStatus);
      model.put("record", mor.getRecord());
      return processTemplate(model, "recordWitnessed.vm", recipientLocale);
    }
    // default
    return messages.getMessage(
        "email.requestStatus.updated",
        new Object[] {user.getDisplayName(), originalStatus, newStatus},
        recipientLocale);
  }

  private String processTemplate(Map<String, Object> model, String templateName, Locale locale) {
    try {
      model.put("msg", new LocaleBoundMessages(messages, locale));
      return VelocityEngineUtils.mergeTemplateIntoString(velocity, templateName, "UTF-8", model);
    } catch (Exception ex) {
      log.warn(ex.getMessage(), ex);
      return messages.getMessage("email.requestStatus.generationFailed", null, locale);
    }
  }

  private boolean isJoinGroupMessage(MessageType messageType) {
    return messageType.equals(MessageType.REQUEST_JOIN_LAB_GROUP)
        || messageType.equals(MessageType.REQUEST_JOIN_PROJECT_GROUP);
  }
}
