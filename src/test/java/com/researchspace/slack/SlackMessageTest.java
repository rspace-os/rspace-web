package com.researchspace.slack;

import static org.junit.Assert.assertEquals;

import com.researchspace.core.util.JacksonUtil;
import com.researchspace.core.util.TransformerUtils;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SlackMessageTest {

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {}

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
