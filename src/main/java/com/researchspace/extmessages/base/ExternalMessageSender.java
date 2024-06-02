package com.researchspace.extmessages.base;

import com.researchspace.model.User;
import com.researchspace.model.apps.App;
import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.model.comms.MessageOrRequest;
import org.springframework.http.ResponseEntity;

/** Sends a message to an external messaging system e.g. Slack */
public interface ExternalMessageSender {

  public static final String RSPACE_BLUE = "#0c7cd5";

  /**
   * Boolean test as to whether the implementation supports sending messages to the supplied App
   *
   * @param app
   * @return <code>true</code> if supported, <code>false</code> otherwise
   */
  boolean supportsApp(App app);

  /**
   * Sends a message to the external service
   *
   * @param message A {@link MessageOrRequest} object containing message text and details of sender.
   * @param messageConfig The configuration of the external app.
   * @param the subject
   * @throws IllegalArgumentException if the AppConfigElementSet is not supported.
   */
  ResponseEntity<String> sendMessage(
      MessageDetails messageDetails, AppConfigElementSet messageConfig, User subject);
}
