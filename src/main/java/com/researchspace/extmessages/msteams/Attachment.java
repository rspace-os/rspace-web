package com.researchspace.extmessages.msteams;

import lombok.Data;

/** A single Adaptive Card attachment within an {@link AdaptiveCardMessage}. */
@Data
class Attachment {
  private String contentType = "application/vnd.microsoft.card.adaptive";
  private String contentUrl = null;
  private AdaptiveCard content;
}
