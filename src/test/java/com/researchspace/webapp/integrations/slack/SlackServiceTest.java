package com.researchspace.webapp.integrations.slack;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.slack.SlackMessage;
import com.researchspace.webapp.controller.RecordAccessDeniedException;
import com.researchspace.webapp.integrations.slack.SlackServiceImpl.MessageHistory;
import java.net.URISyntaxException;
import java.util.ArrayList;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

public class SlackServiceTest {

  private SlackServiceImpl slackService = new SlackServiceImpl();

  private RestTemplate restTemplate = mock(RestTemplate.class);

  @Before
  public void setUp() throws Exception {
    slackService.setRestTemplate(restTemplate);
  }

  @Test
  public void noMessagesInSelectedPeriodResponse()
      throws URISyntaxException, RecordAccessDeniedException {

    MessageHistory emptyMsgHistory = new MessageHistory();
    emptyMsgHistory.setOk(true);
    emptyMsgHistory.setMessages(new ArrayList<SlackMessage>());
    ResponseEntity<MessageHistory> emptyMessagesResponse =
        new ResponseEntity<MessageHistory>(emptyMsgHistory, HttpStatus.OK);
    when(restTemplate.exchange(any(), eq(MessageHistory.class))).thenReturn(emptyMessagesResponse);

    slackService.saveConversation(null, null, null, null, null, null, null, null);

    HttpEntity<String> expectedMessage =
        new HttpEntity<>(
            "{\"text\":\"No messages found in a selected time period.\",\"channel\":null,"
                + "\"type\":null,\"attachments\":[],\"ts\":null,\"user\":null}");
    verify(restTemplate).postForEntity(null, expectedMessage, String.class);
  }
}
