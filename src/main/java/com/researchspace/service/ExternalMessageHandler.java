package com.researchspace.service;

import com.researchspace.model.User;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.views.ServiceOperationResult;
import java.util.List;
import org.springframework.http.ResponseEntity;

/** Top level interface for posting messages to externally configured web applications. */
public interface ExternalMessageHandler {

  /**
   * Sends an external message, and creates a {@link MessageOrRequest} object recording the
   * transmission of the message.
   *
   * @param message A non-empty message
   * @param appConfigElementSetId The id of the configuration of the message service
   * @param recordId One or more document ids
   * @param user the subject
   * @return a {@link ServiceOperationResult} encapsulatng details of the operation
   */
  ServiceOperationResult<ResponseEntity<String>> sendExternalMessage(
      String message, Long appConfigElementSetId, List<Long> recordId, User user);
}
