package com.researchspace.slack;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.core.util.JacksonUtil;
import com.researchspace.core.util.TransformerUtils;
import java.util.List;
import org.junit.jupiter.api.Test;

public class SlackMessageTest {

  @Test
  public void testToJSON() {
    SlackMessage message = createAnySlackMessage("Hello");
    String messageJson = message.toJSON();
    SlackMessage mess2 = JacksonUtil.fromJson(messageJson, SlackMessage.class);
    assertEquals(message, mess2);
  }

  static SlackMessage createAnySlackMessage(String text) {
    SlackMessage message = new SlackMessage();
    message.setText(text);
    message.setUsername("SlackMessageTestJunit");
    message.setAttachments(createASlackAttachment());
    return message;
  }

  static List<SlackAttachment> createASlackAttachment() {
    SlackAttachment attach = new SlackAttachment();
    attach.setFallback("fallback text");
    attach.setText(" this is the text");
    attach.setTitle("Title header");
    attach.setTitleLink("http://www.google.com");
    attach.setAuthorName("Mr Junit Test");
    return TransformerUtils.toList(attach);
  }
}
