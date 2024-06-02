package com.researchspace.webapp.integrations.slack;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.RecordManager;
import com.researchspace.slack.SlackMessage;
import com.researchspace.slack.SlackUser;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

public class SlackServiceImpl implements SlackService {

  private static final Logger LOG = LoggerFactory.getLogger(SlackServiceImpl.class);
  private static final Long USER_LIST_LIMIT_PER_REQUEST = 200L;

  private @Autowired ApplicationEventPublisher publisher;
  private @Autowired RecordManager recordManager;

  private RestTemplate restTemplate = new RestTemplate();

  @Override
  public void saveConversation(
      String accessToken,
      String channelId,
      URI responseURI,
      Double sinceTimestamp,
      Double toTimestamp,
      User user,
      SlackUser slackUser,
      String documentName) {
    try {
      List<SlackMessage> messages =
          getMessagesFromChannel(accessToken, channelId, sinceTimestamp, toTimestamp);

      if (messages.isEmpty()) {
        String msg = "No messages found in a selected time period.";
        sendResponseToSlack(responseURI, msg);
        return;
      }

      Map<String, SlackUser> mapping = getUserMapping(accessToken);
      StringBuilder messagesFormatted = new StringBuilder();
      Collections.reverse(messages);
      for (SlackMessage message : messages) {
        // Horizontal line between messages
        if (messagesFormatted.length() > 0) {
          messagesFormatted.append("<hr>");
        }

        DateTimeZone timezone = null;
        if (mapping.containsKey(slackUser.getUserId())) {
          // slackUser was created in SlackController only with user id and team id fields, so it
          // has
          // no information about the timezone. Map 'mapping' contains SlackUser instances retrieved
          // from
          // the Slack back-end, so they have all available information about users.
          try {
            timezone = DateTimeZone.forID(mapping.get(slackUser.getUserId()).getTimezone());
          } catch (NullPointerException | IllegalArgumentException e) {
            LOG.error("Error while creating timezone for input string: ", e);
          }
        }
        messagesFormatted.append(message.toHTML(mapping, timezone));
      }
      StructuredDocument newDocument =
          recordManager.createBasicDocumentWithContent(
              user.getRootFolder().getId(), documentName, user, messagesFormatted.toString());

      publisher.publishEvent(new GenericEvent(user, newDocument, AuditAction.CREATE));

      // Format results as a SlackMessage
      String msg = String.format("Done. %d messages saved from Slack.", messages.size());
      ResponseEntity<String> resp = sendResponseToSlack(responseURI, msg);
      if (!HttpStatus.OK.equals(resp.getStatusCode())) {
        throw new SlackErrorResponseException("Slack responded with " + resp.getStatusCodeValue());
      }
    } catch (RuntimeException | SlackErrorResponseException | URISyntaxException exception) {
      // Format error message as a SlackMessage
      String msg =
          String.format("Error while saving Slack conversation [%s]", exception.getMessage());
      sendResponseToSlack(responseURI, msg);
    }
  }

  private ResponseEntity<String> sendResponseToSlack(URI responseURI, String msg) {
    SlackMessage slackMessage = new SlackMessage(msg);
    HttpEntity<String> requestEntity = new HttpEntity<>(slackMessage.toJSON());
    return restTemplate.postForEntity(responseURI, requestEntity, String.class);
  }

  @Data
  protected static class MessageHistory {
    private @JsonProperty("ok") boolean ok;
    private @JsonProperty("error") String error;
    private @JsonProperty("has_more") boolean moreMessages;
    private @JsonProperty("messages") List<SlackMessage> messages;
  }

  private List<SlackMessage> getMessagesFromChannel(
      String accessToken, String channelId, Double sinceTimestamp, Double toTimestamp)
      throws URISyntaxException, SlackErrorResponseException {

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    URI channelsHistoryURI = new URI("https://slack.com/api/conversations.history");
    List<SlackMessage> messages = new ArrayList<>();

    RequestEntity<MultiValueMap<String, String>> requestEntity;
    ResponseEntity<MessageHistory> responseEntity;
    MessageHistory messageHistory;
    MultiValueMap<String, String> kv;
    String latestTimestamp = String.format("%.06f", toTimestamp);

    do {
      kv = new LinkedMultiValueMap<>();
      kv.add("token", accessToken);
      kv.add("channel", channelId);
      if (sinceTimestamp != null && sinceTimestamp != 0)
        kv.add("oldest", String.format("%.06f", sinceTimestamp));
      if (latestTimestamp != null) kv.add("latest", latestTimestamp);

      requestEntity = new RequestEntity<>(kv, headers, HttpMethod.POST, channelsHistoryURI);
      responseEntity = restTemplate.exchange(requestEntity, MessageHistory.class);
      messageHistory = responseEntity.getBody();

      if (messageHistory.isOk()) {
        messages.addAll(messageHistory.getMessages());
      } else {
        throw new SlackErrorResponseException(messageHistory.getError());
      }

      if (messageHistory.getMessages().isEmpty()) {
        break;
      } else {
        latestTimestamp = messages.get(messages.size() - 1).getTimestamp();
      }
    } while (messageHistory.isMoreMessages() && messageHistory.isOk());

    return messages;
  }

  @Data
  private static class MembersList {
    private @JsonProperty("ok") boolean ok;
    private @JsonProperty("error") String error;
    private @JsonProperty("members") List<SlackUser> members;
    private @JsonProperty("response_metadata") ResponseMetadata responseMetadata;

    @Data
    private static class ResponseMetadata {
      private @JsonProperty("next_cursor") String nextCursor;
    }
  }

  private Map<String, SlackUser> getUserMapping(String accessToken)
      throws URISyntaxException, SlackErrorResponseException {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    URI usersListURI = new URI("https://slack.com/api/users.list");

    RequestEntity<MultiValueMap<String, String>> requestEntity;
    ResponseEntity<MembersList> responseEntity;
    MultiValueMap<String, String> kv;
    String nextCursor = null;

    Map<String, SlackUser> mapping = new HashMap<>();

    do {
      kv = new LinkedMultiValueMap<>();
      kv.add("token", accessToken);
      kv.add("limit", USER_LIST_LIMIT_PER_REQUEST.toString());
      if (nextCursor != null) kv.add("cursor", nextCursor);

      requestEntity = new RequestEntity<>(kv, headers, HttpMethod.POST, usersListURI);
      responseEntity = restTemplate.exchange(requestEntity, MembersList.class);

      MembersList membersList = responseEntity.getBody();
      if (!membersList.isOk()) {
        throw new SlackErrorResponseException(membersList.getError());
      }

      for (SlackUser slackUser : membersList.getMembers()) {
        mapping.put(slackUser.getUserId(), slackUser);
      }

      nextCursor = membersList.getResponseMetadata().getNextCursor();
    } while (nextCursor != null && !nextCursor.isEmpty());

    return mapping;
  }

  /* =================
   *   for testing
   * ================= */

  protected void setPublisher(ApplicationEventPublisher publisher) {
    this.publisher = publisher;
  }

  protected void setRecordManager(RecordManager recordManager) {
    this.recordManager = recordManager;
  }

  protected void setRestTemplate(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }
}
