package com.researchspace.webapp.integrations.slack;

import com.researchspace.model.User;
import com.researchspace.slack.SlackUser;
import com.researchspace.webapp.controller.RecordAccessDeniedException;
import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.scheduling.annotation.Async;

public interface SlackService {
  @Async(value = "slackRequestExecutor")
  void saveConversation(
      String accessToken,
      String channelId,
      URI responseURI,
      Double sinceTimestamp,
      Double toTimestamp,
      User user,
      SlackUser slackUser,
      String notebookName)
      throws URISyntaxException, RecordAccessDeniedException;
}
