package com.researchspace.slack;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.researchspace.core.util.JacksonUtil;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

@Data
@Slf4j
public class SlackMessage {

  private static final String DATE_FORMAT = "[d MMM yyyy HH:mm:ss]";

  private String text, channel, type;
  private @JsonProperty("ts") String timestamp;
  private @JsonProperty("user") String username;
  private List<SlackAttachment> attachments = new ArrayList<>();

  public SlackMessage() {}

  public SlackMessage(String text) {
    this(text, null);
  }

  public SlackMessage(String text, String username, List<SlackAttachment> attachments) {
    this(text, username);
    this.attachments = attachments;
  }

  public SlackMessage(String text, String username) {
    this.text = text;
    this.username = username;
  }

  public void addSlackAttachment(SlackAttachment attachment) {
    this.attachments.add(attachment);
  }

  public String toJSON() {
    return JacksonUtil.toJson(this);
  }

  /**
   * Formats message as HTML
   *
   * @param userMap mapping for user ID to SlackUser
   * @return
   */
  public String toHTML(Map<String, SlackUser> userMap, @Nullable DateTimeZone timezone) {
    StringBuilder htmlMessage = new StringBuilder();

    htmlMessage.append("<p>");

    // Add timestamp if available
    if (timestamp != null && !timestamp.isEmpty() && timezone != null) {
      htmlMessage.append(
          new SimpleDateFormat(DATE_FORMAT)
              .format(
                  new DateTime(
                          Timestamp.from(
                              Instant.ofEpochSecond(Double.valueOf(timestamp).longValue())),
                          timezone)
                      .toDate()));
      htmlMessage.append(' ');
    }

    // Add user's display name if available
    if (username != null) {
      String displayName =
          userMap.containsKey(username)
              ? userMap.get(username).getProfile().getRealName()
              : "Unknown User";
      htmlMessage.append(displayName);
      htmlMessage.append(": ");
    }

    htmlMessage.append(SlackMarkupToHTMLFormatter.format(text, userMap));

    // Format all attachments
    for (SlackAttachment attachment : attachments) {
      htmlMessage.append(attachment.toHTML());
    }
    htmlMessage.append("</p>\n");

    return htmlMessage.toString();
  }
}
