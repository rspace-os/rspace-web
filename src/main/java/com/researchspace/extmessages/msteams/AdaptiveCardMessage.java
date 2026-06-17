package com.researchspace.extmessages.msteams;

import com.researchspace.core.util.JacksonUtil;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * Top-level payload for a Microsoft Teams Workflows incoming webhook. Workflows webhooks (the
 * replacement for the retired Office 365 connector webhooks) require an Adaptive Card wrapped in a
 * {@code {"type":"message","attachments":[...]}} envelope and reject the legacy MessageCard format
 * with HTTP 400.
 */
@Data
class AdaptiveCardMessage {
  private String type = "message";
  private List<Attachment> attachments = new ArrayList<>();

  void addCard(AdaptiveCard card) {
    Attachment attachment = new Attachment();
    attachment.setContent(card);
    attachments.add(attachment);
  }

  String toJSON() {
    return JacksonUtil.toJson(this);
  }
}
