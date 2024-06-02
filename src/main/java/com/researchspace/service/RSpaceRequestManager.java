package com.researchspace.service;

import com.researchspace.model.comms.CommunicationStatus;
import com.researchspace.model.comms.MessageOrRequest;
import org.apache.shiro.authz.AuthorizationException;

/**
 * Extension of CommunicationManaager that handles request creation and status updates. This is
 * decoupled from CommunicationManager so as to avoid cycles in service. dependencies.
 */
public interface RSpaceRequestManager {

  /**
   * Replies a message to existing simple message
   *
   * @param responderUserName
   * @param originalMsgId An ID for a previous message of type SimpleMessage; the message created by
   *     this method is a reply to the message identified by this identifier.
   * @param msg
   * @return
   * @throws IllegalStateException if message type is other than SimpleMessage
   * @throws AuthorizationException if responder is not in recipient list
   */
  public MessageOrRequest replyToMessage(String responderUserName, Long originalMsgId, String msg);

  /**
   * Updates the status of a RequestOrMessage.
   *
   * @param user
   * @param newStatus
   * @param requestID
   * @return the updated MessageOrRequest
   * @throws AuthorizationException if user is not in recipient list
   */
  public MessageOrRequest updateStatus(
      String user, CommunicationStatus newStatus, Long requestID, String optionalMessage);
}
