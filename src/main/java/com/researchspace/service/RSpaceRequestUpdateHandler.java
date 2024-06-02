package com.researchspace.service;

import com.researchspace.model.User;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.MessageType;

/**
 * Handler interface for message types; not to be confused with handlers for HTTP requests in the
 * controller layer.
 */
public interface RSpaceRequestUpdateHandler {

  /**
   * Boolean test for whether this handler can handle the specified {@link MessageType}
   *
   * @param messageType
   * @return
   */
  boolean handleRequest(MessageType messageType);

  /**
   * HAndles any processing of the request action when the request status is updated
   *
   * @param updatedTarget
   * @param subject
   */
  void handleMessageOrRequestUpdate(CommunicationTarget updatedTarget, User subject);
}
