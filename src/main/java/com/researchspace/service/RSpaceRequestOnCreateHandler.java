package com.researchspace.service;

import com.researchspace.model.User;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageType;

public interface RSpaceRequestOnCreateHandler {

  /**
   * HAndles any processing of the request action when the request is created
   *
   * @param mor
   * @param subject
   */
  void handleMessageOrRequestSetUp(MessageOrRequest mor, User subject);

  /**
   * Boolean test for whether this handler can handle the specified {@link MessageType}
   *
   * @param messageType
   * @return
   */
  boolean handleRequest(MessageType messageType);
}
