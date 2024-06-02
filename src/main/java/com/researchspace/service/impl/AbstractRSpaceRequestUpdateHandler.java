package com.researchspace.service.impl;

import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.service.RSpaceRequestUpdateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/** Provides reused methods between different handlers */
public abstract class AbstractRSpaceRequestUpdateHandler implements RSpaceRequestUpdateHandler {

  Logger log = LoggerFactory.getLogger(getClass());

  @Autowired protected AuditTrailService auditService;

  @Override
  public final void handleMessageOrRequestUpdate(CommunicationTarget updatedTarget, User subject) {
    validate(updatedTarget, subject);
    if (updatedTarget.isCompleted()) {
      doHandleMessageOrRequestUpdateOnCompletion(updatedTarget, subject);
    } else if (updatedTarget.isRejected()) {
      doHandleMessageOrRequestUpdateOnRejection(updatedTarget, subject);
    }
  }

  /**
   * Subclasses need to implement this handler which is called after validate() in the event that
   * the request was replied to with 'Completed' status.
   *
   * @param updatedTarget
   * @param subject
   */
  protected abstract void doHandleMessageOrRequestUpdateOnCompletion(
      CommunicationTarget updatedTarget, User subject);

  /**
   * Subclasses <em>may</em> override this handler which is called after validate() in the event
   * that the request was replied to with 'Rejected' status. This default implementation does
   * nothing.
   *
   * @param updatedTarget
   * @param subject
   */
  protected void doHandleMessageOrRequestUpdateOnRejection(
      CommunicationTarget updatedTarget, User subject) {
    log.info(
        "Request to target {} rejected by user {}",
        updatedTarget.getRecipient().getUsername(),
        subject.getUsername());
  }

  /**
   * Subclasses can override or extend. Performs default implementation with generic argument
   * valdiation,
   *
   * @param updatedTarget
   * @param subject
   * @throws {@link IllegalArgumentException} if this handler can't handle the target or if the
   *     Communication is not a message or request.
   */
  protected void validate(CommunicationTarget updatedTarget, User subject) {
    if (!updatedTarget.getCommunication().isMessageOrRequest()) {
      throw new IllegalArgumentException("Can't handle a non-MessageOrRequest type");
    }
    MessageOrRequest mor = (MessageOrRequest) updatedTarget.getCommunication();
    if (!handleRequest(mor.getMessageType())) {
      throw new IllegalArgumentException(
          "Can't handle request of type [" + mor.getMessageType() + "]");
    }
  }
}
