package com.researchspace.extmessages.base;

import com.researchspace.analytics.service.AnalyticsManager;
import com.researchspace.model.User;
import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.service.MessageSourceUtils;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Helper methods for posting external messages. <br>
 * Also defines abstract callback methods for subclasses to implement.
 */
@Slf4j
public abstract class AbstractExternalWebhookMessageSender implements ExternalMessageSender {

  protected @Autowired MessageSourceUtils messages;
  protected @Autowired AnalyticsManager analyticsMgr;

  @Override
  public final ResponseEntity<String> sendMessage(
      MessageDetails message, AppConfigElementSet messageConfig, User subject) {
    Validate.isTrue(
        supportsApp(messageConfig.getApp()),
        messageConfig.getApp().getName() + " is not supported!");

    if (!subject.equals(messageConfig.getUserAppConfig().getUser())) {
      throw new AuthorizationException(
          messages.getMessage(
              "errors.authorization.failure.sendExternalMessage",
              new Object[] {subject.getUsername()}));
    }
    String jsonMessage = createMessage(message);

    URI uri = getPostUrl(messageConfig).get();
    ResponseEntity<String> rc = doSendMessage(jsonMessage, uri);
    postSendMessage(rc, uri, message, subject);

    return rc;
  }

  /**
   * Performs any tidy up, notification or analytics actions after the message has been sent
   *
   * @param rc
   * @param uri
   * @param message
   * @param subject
   */
  protected abstract void postSendMessage(
      ResponseEntity<String> rc, URI uri, MessageDetails message, User subject);

  /**
   * Posts message to the supplied URI
   *
   * @param jsonMessage
   * @param uri
   */
  protected ResponseEntity<String> doSendMessage(String jsonMessage, URI uri) {
    RestTemplate template = getRestTemplate();
    HttpEntity<String> requestEntity = new HttpEntity<String>(jsonMessage, createPostHeaders());
    try {
      ResponseEntity<String> resp = template.postForEntity(uri, requestEntity, String.class);
      if (!resp.getStatusCode().is2xxSuccessful()) {
        log.warn("Status code response was not successful : {}", resp.getStatusCode());
      }
      return resp;
    } catch (RestClientException rce) {
      // not the URL: a webhook URL is a credential (RSDEV-1525)
      log.warn("Posting message to {} webhook failed", getClass().getSimpleName());
      throw rce;
    }
  }

  /*
   * Package scoped for testing
   */
  RestTemplate getRestTemplate() {
    return new RestTemplate();
  }

  /**
   * Headers applied to the webhook POST. The default sends no explicit headers (the body is then
   * posted as {@code text/plain}); subclasses can override to set a content type the webhook
   * requires.
   *
   * @return headers to attach to the outgoing request
   */
  protected HttpHeaders createPostHeaders() {
    return new HttpHeaders();
  }

  /**
   * Creates body of message from information in the supplied {@link MessageOrRequest}
   *
   * @return
   */
  protected abstract String createMessage(MessageDetails internalMsg);

  /**
   * Queries configuration for the name of the URL property saved as a webhook.
   *
   * @param messageConfig
   * @return
   */
  protected Optional<URI> getPostUrl(AppConfigElementSet messageConfig) {
    URI uri = null;
    String webhookUrl = doGetPostUrl(messageConfig);
    try {
      uri = new URI(webhookUrl);
    } catch (URISyntaxException e) {
      log.error("Couldn't make a URI from the webhook URL: {}", e.getReason());
    }
    return Optional.ofNullable(uri);
  }

  /**
   * Gets the webhook URL that the messages for this configuration are posted to.
   *
   * @param messageConfig the channel's configuration
   * @return the webhook URL
   */
  protected abstract String doGetPostUrl(AppConfigElementSet messageConfig);

  // converts <br/> from form into newlines
  static final Pattern BR_TAG = Pattern.compile("<\\s*br\\s*/>");

  protected String convert(String string) {
    Matcher m = BR_TAG.matcher(string);
    return m.replaceAll("\n");
  }
}
