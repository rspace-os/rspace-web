package com.researchspace.service;

import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MsgOrReqstCreationCfg;
import java.util.Date;
import java.util.Set;

/** Creates new messages and requests */
public interface MessageOrRequestCreatorManager {
  /**
   * Creates and persists a new Message or Request
   *
   * @param reqCmnd a MsgOrReqstCreationCfg from the UI
   * @param originatorUserName the creator of the message
   * @param recipientUserNames the recipients
   * @param prevMessageId {@link Long} can be <code>null</code>.
   * @param requestedCompletionDate A {@link Date} can be <code>null</code>.
   * @return A persisted {@link MessageOrRequest}
   */
  MessageOrRequest createRequest(
      MsgOrReqstCreationCfg reqCmnd,
      String originatorUserName,
      Set<String> recipientUserNames,
      Long prevMessageId,
      Date requestedCompletionDate);

  /**
   * Simple request creation method when there is no previous messgage or requested completion date
   *
   * @param reqCmnd
   * @param originatorUserName
   * @param recipientUserNames
   * @return a {@link MessageOrRequest}
   */
  MessageOrRequest createRequest(
      MsgOrReqstCreationCfg reqCmnd, String originatorUserName, Set<String> recipientUserNames);
}
