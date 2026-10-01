package com.researchspace.slack;

import com.researchspace.analytics.service.AnalyticsEvent;
import com.researchspace.extmessages.base.AbstractExternalWebhookMessageSender;
import com.researchspace.extmessages.base.ExternalMessageSender;
import com.researchspace.extmessages.base.MessageDetails;
import com.researchspace.model.User;
import com.researchspace.model.apps.App;
import com.researchspace.model.core.IRSpaceDoc;
import com.researchspace.properties.IPropertyHolder;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

public class SlackMessageSender extends AbstractExternalWebhookMessageSender
    implements ExternalMessageSender {

  private String webhookURLSettingName = "SLACK_WEBHOOK_URL";
  Logger log = LoggerFactory.getLogger(SlackMessageSender.class);

  int MAX_ATTACHMENTS = 20;
  @Autowired IPropertyHolder props;

  @Override
  public boolean supportsApp(App app) {
    return App.APP_SLACK.equals(app.getName());
  }

  protected String createMessage(MessageDetails message) {
    String msg = formatMessage(message);
    SlackMessage slack = new SlackMessage(msg, message.getOriginator().getFullName());
    int count = 0;
    if (!message.getRecords().isEmpty()) {
      for (IRSpaceDoc doc : message.getRecords()) {
        if (count > MAX_ATTACHMENTS) {
          log.warn(
              "There are {} records but only {} can be sent ",
              message.getRecords().size(),
              MAX_ATTACHMENTS);
          break;
        }

        slack.addSlackAttachment(new SlackAttachment(props, doc));
        count++;
      }
    } else {
      log.warn("No records to send, can't add attachment");
    }
    return slack.toJSON();
  }

  private String formatMessage(MessageDetails message) {
    return "*From:* "
        + message.getOriginator().getFullName()
        + "\n"
        + convert(message.getMessage());
  }

  @Override
  protected String getPostUrlSetting() {
    return webhookURLSettingName;
  }

  @Override
  protected void postSendMessage(
      ResponseEntity<String> rc, URI uri, MessageDetails message, User subject) {
    log.info(
        "Message sent with response code {} by {} [{}] to URI {}",
        rc.getStatusCodeValue(),
        subject.getUsername(),
        subject.getId(),
        uri);
    analyticsMgr.trackChatApp(subject, "message_post", AnalyticsEvent.SLACK_USED);
  }
}
