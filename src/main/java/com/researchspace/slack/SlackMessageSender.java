package com.researchspace.slack;

import static com.researchspace.service.IntegrationsHandler.SLACK_APP_NAME;

import com.researchspace.analytics.service.AnalyticsEvent;
import com.researchspace.extmessages.base.AbstractExternalWebhookMessageSender;
import com.researchspace.extmessages.base.ExternalMessageSender;
import com.researchspace.extmessages.base.MessageDetails;
import com.researchspace.model.User;
import com.researchspace.model.apps.App;
import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.model.core.IRSpaceDoc;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.UserConnectionManager;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

public class SlackMessageSender extends AbstractExternalWebhookMessageSender
    implements ExternalMessageSender {

  Logger log = LoggerFactory.getLogger(SlackMessageSender.class);

  int MAX_ATTACHMENTS = 20;
  @Autowired IPropertyHolder props;
  @Autowired UserConnectionManager userConnectionManager;

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

  // the webhook URL is a credential, kept in the channel's encrypted UserConnection
  @Override
  protected String doGetPostUrl(AppConfigElementSet messageConfig) {
    return userConnectionManager
        .findByUserNameProviderName(
            messageConfig.getUserAppConfig().getUser().getUsername(),
            SLACK_APP_NAME,
            String.valueOf(messageConfig.getId()))
        .map(UserConnection::getSecret)
        .orElse("");
  }

  @Override
  protected void postSendMessage(
      ResponseEntity<String> rc, URI uri, MessageDetails message, User subject) {
    log.info(
        "Message sent with response code {} by {} [{}]",
        rc.getStatusCodeValue(),
        subject.getUsername(),
        subject.getId());
    analyticsMgr.trackChatApp(subject, "message_post", AnalyticsEvent.SLACK_USED);
  }
}
