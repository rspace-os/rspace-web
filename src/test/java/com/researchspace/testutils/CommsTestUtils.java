package com.researchspace.testutils;

import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.comms.CommunicationPriority;
import com.researchspace.model.comms.GroupMessageOrRequest;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.comms.MsgOrReqstCreationCfg;
import com.researchspace.model.comms.Notification;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.comms.RequestFactory;
import java.text.ParseException;

public class CommsTestUtils {

  public static MessageOrRequest createARequest(User originator) throws ParseException {
    return createRequestOfType(originator, MessageType.REQUEST_JOIN_EXISTING_COLLAB_GROUP);
  }

  public static MessageOrRequest createSimpleMessage(User originator) {
    return createRequestOfType(originator, MessageType.SIMPLE_MESSAGE);
  }

  public static MessageOrRequest createRequestOfType(User originator, MessageType type) {
    RequestFactory rf = createARequestFactory();
    MsgOrReqstCreationCfg config = new MsgOrReqstCreationCfg();
    config.setMessageType(type);
    MessageOrRequest message = rf.createMessageOrRequestObject(config, null, null, originator);

    message.setLatest(true);
    message.setMessage("Please look at line 250");
    return message;
  }

  private static RequestFactory createARequestFactory() {
    RequestFactory rf = new RequestFactory();
    return rf;
  }

  public static GroupMessageOrRequest createAGroupRequest(User originator, Group grp)
      throws ParseException {
    RequestFactory rf = createARequestFactory();
    MsgOrReqstCreationCfg config = new MsgOrReqstCreationCfg();
    config.setMessageType(MessageType.REQUEST_JOIN_EXISTING_COLLAB_GROUP);

    GroupMessageOrRequest message =
        (GroupMessageOrRequest) rf.createMessageOrRequestObject(config, null, grp, originator);

    message.setLatest(true);
    message.setMessage("Please join the group");
    return (GroupMessageOrRequest) message;
  }

  public static Notification createAnyNotification(User originator) {

    Notification message = new Notification();
    message.setMessage("A notification");
    message.setOriginator(originator);
    message.setPriority(CommunicationPriority.URGENT);
    message.setNotificationType(NotificationType.NOTIFICATION_DOCUMENT_EDITED);
    return message;
  }
}
