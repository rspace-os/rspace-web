package com.researchspace.extmessages.base;

import com.researchspace.analytics.service.AnalyticsManager;
import com.researchspace.extmessages.base.WebhookDestinationPolicy.HostResolver;
import com.researchspace.model.User;
import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.service.MessageSourceUtils;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
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

  /*
   * The send runs on the servlet thread, so it must be bounded (RSDEV-1340; compare the B2INST
   * client). The send timeout is a deadline for the whole exchange: on expiry Spring's JDK client
   * closes the response body, so a slow or endless body cannot hold the thread either. Redirects
   * are never followed, so an allowed host cannot forward the post elsewhere.
   */
  static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
  static final Duration SEND_TIMEOUT = Duration.ofSeconds(10);

  private RestTemplate restTemplate = createRestTemplate(CONNECT_TIMEOUT, SEND_TIMEOUT);
  HostResolver hostResolver = WebhookDestinationPolicy.DNS;

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
    URI uri = checkedPostUrl(messageConfig);
    String jsonMessage = createMessage(message);

    ResponseEntity<String> rc = doSendMessage(jsonMessage, uri);
    postSendMessage(rc, uri, message, subject);

    return rc;
  }

  /**
   * @return the allowed destinations for this sender's webhooks
   */
  protected abstract WebhookDestinationPolicy destinationPolicy();

  private URI checkedPostUrl(AppConfigElementSet messageConfig) {
    String webhookUrl = doGetPostUrl(messageConfig);
    try {
      destinationPolicy().checkResolved(webhookUrl, hostResolver);
    } catch (WebhookUrlNotAllowedException e) {
      // not the URL: a webhook URL is a credential (RSDEV-1525)
      log.warn("{} webhook destination rejected: {}", getClass().getSimpleName(), e.getReason());
      throw e;
    }
    return URI.create(webhookUrl);
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
      // Void: the response body is never read into memory
      ResponseEntity<Void> resp = template.postForEntity(uri, requestEntity, Void.class);
      if (!resp.getStatusCode().is2xxSuccessful()) {
        log.warn("Status code response was not successful : {}", resp.getStatusCode());
      }
      return new ResponseEntity<>(resp.getStatusCode());
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
    return restTemplate;
  }

  /*
   * Package scoped so tests can use short timeouts
   */
  void setTimeouts(Duration connectTimeout, Duration sendTimeout) {
    restTemplate = createRestTemplate(connectTimeout, sendTimeout);
  }

  private static RestTemplate createRestTemplate(Duration connectTimeout, Duration sendTimeout) {
    HttpClient client =
        HttpClient.newBuilder()
            .connectTimeout(connectTimeout)
            .followRedirects(HttpClient.Redirect.NEVER)
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
    factory.setReadTimeout(sendTimeout);
    RestTemplate template = new RestTemplate(factory);
    template.setErrorHandler(new BodyIgnoringErrorHandler());
    return template;
  }

  /** Raises the usual status exceptions without reading the error response body. */
  private static class BodyIgnoringErrorHandler extends DefaultResponseErrorHandler {
    @Override
    protected void handleError(
        ClientHttpResponse response, HttpStatusCode statusCode, URI url, HttpMethod method)
        throws IOException {
      String statusText = response.getStatusText();
      HttpHeaders headers = response.getHeaders();
      if (statusCode.is4xxClientError()) {
        throw HttpClientErrorException.create(statusCode, statusText, headers, null, null);
      }
      throw HttpServerErrorException.create(statusCode, statusText, headers, null, null);
    }
  }

  /**
   * Creates the headers for the webhook POST.
   *
   * <p>Declares {@code application/json} so the body is sent as UTF-8. Without it, a String body is
   * sent as ISO-8859-1 and non-Latin-1 characters become {@code ?}.
   *
   * @return headers with an {@code application/json} content type
   */
  protected HttpHeaders createPostHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  /**
   * Creates body of message from information in the supplied {@link MessageOrRequest}
   *
   * @return
   */
  protected abstract String createMessage(MessageDetails internalMsg);

  /** Queries configuration for the webhook URL. The URL is a credential: never log it. */
  protected abstract String doGetPostUrl(AppConfigElementSet messageConfig);

  // converts <br/> from form into newlines
  static final Pattern BR_TAG = Pattern.compile("<\\s*br\\s*/>");

  protected String convert(String string) {
    Matcher m = BR_TAG.matcher(string);
    return m.replaceAll("\n");
  }
}
