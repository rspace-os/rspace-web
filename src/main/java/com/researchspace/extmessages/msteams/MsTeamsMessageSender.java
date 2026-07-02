package com.researchspace.extmessages.msteams;

import com.researchspace.analytics.service.AnalyticsEvent;
import com.researchspace.extmessages.base.AbstractExternalWebhookMessageSender;
import com.researchspace.extmessages.base.ExternalMessageSender;
import com.researchspace.extmessages.base.MessageDetails;
import com.researchspace.model.User;
import com.researchspace.model.apps.App;
import com.researchspace.model.core.IRSpaceDoc;
import com.researchspace.properties.IPropertyHolder;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public class MsTeamsMessageSender extends AbstractExternalWebhookMessageSender
    implements ExternalMessageSender {

  private String webhookPropertyName = "MSTEAMS_WEBHOOK_URL";
  Logger log = LoggerFactory.getLogger(MsTeamsMessageSender.class);

  private @Autowired IPropertyHolder props;

  @Override
  public boolean supportsApp(App app) {
    return App.APP_MSTEAMS.equals(app.getName());
  }

  protected String getPostUrlSetting() {
    return webhookPropertyName;
  }

  /**
   * Builds an Adaptive Card "message" payload. Teams Workflows incoming webhooks (which replaced
   * the retired Office 365 connector webhooks) require this format and reject the legacy
   * MessageCard format with HTTP 400.
   */
  protected String createMessage(MessageDetails message) {
    AdaptiveCard card = new AdaptiveCard();
    List<CardElement> body = card.getBody();
    final int docCount = message.getRecords().size();

    if (docCount == 0) {
      log.debug("Message has no associated documents; sending card without document summaries");
      body.add(TextBlock.heading("Message from " + message.getOriginator().getFullName()));
      body.add(TextBlock.body(convert(message.getMessage())));
    } else {
      body.add(TextBlock.heading("From " + message.getOriginator().getFullName()));
      body.add(TextBlock.body(convert(message.getMessage())));
      for (IRSpaceDoc doc : message.getRecords()) {
        body.add(TextBlock.body(createSummary(doc, createDocumentLink(doc))));
        body.add(factsFor(doc));
      }
    }

    AdaptiveCardMessage payload = new AdaptiveCardMessage();
    payload.addCard(card);
    return payload.toJSON();
  }

  @Override
  protected void postSendMessage(
      ResponseEntity<String> rc, URI uri, MessageDetails message, User subject) {
    analyticsMgr.trackChatApp(subject, "message_post", AnalyticsEvent.TEAMS_USED);
  }

  /**
   * Workflows webhooks reject a {@code text/plain} body; the Adaptive Card must be sent as JSON.
   */
  @Override
  protected HttpHeaders createPostHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  private FactSet factsFor(IRSpaceDoc doc) {
    FactSet facts = new FactSet();
    facts.add("Owner", doc.getOwner().getFullName());
    facts.add("ID", doc.getGlobalIdentifier());
    return facts;
  }

  private String createSummary(IRSpaceDoc doc, String link) {
    return String.format("Message about [%s](%s)", doc.getName(), link);
  }

  private String createDocumentLink(IRSpaceDoc doc) {
    return String.format("%s/globalId/%s", props.getServerUrl(), doc.getGlobalIdentifier());
  }
}
