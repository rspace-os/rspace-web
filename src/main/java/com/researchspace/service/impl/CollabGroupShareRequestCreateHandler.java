package com.researchspace.service.impl;

import com.researchspace.dao.CollaborationGroupTrackerDao;
import com.researchspace.model.CollabGroupCreationTracker;
import com.researchspace.model.User;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageType;
import com.researchspace.service.RSpaceRequestOnCreateHandler;
import org.springframework.beans.factory.annotation.Autowired;

public class CollabGroupShareRequestCreateHandler implements RSpaceRequestOnCreateHandler {

  @Autowired private CollaborationGroupTrackerDao groupTrackerDao;

  @Override
  public void handleMessageOrRequestSetUp(MessageOrRequest mor, User subject) {
    CollabGroupCreationTracker tracker = new CollabGroupCreationTracker();
    tracker.setMor(mor);
    String initialName = getInitialGrpName(mor);
    tracker.setInitialGrpName(initialName);
    groupTrackerDao.save(tracker);
  }

  @Override
  public boolean handleRequest(MessageType messageType) {
    return MessageType.REQUEST_EXTERNAL_SHARE.equals(messageType);
  }

  private String getInitialGrpName(MessageOrRequest mor) {
    StringBuffer sb = new StringBuffer();
    sb.append(mor.getOriginator().getLastName() + "-");
    for (CommunicationTarget ct : mor.getRecipients()) {
      sb.append(ct.getRecipient().getLastName() + "-");
    }
    sb.append("collabGroup");
    return sb.toString();
  }
}
