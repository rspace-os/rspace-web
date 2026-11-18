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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

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

  private RestClient restClient = createRestClient(CONNECT_TIMEOUT, SEND_TIMEOUT);
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
    URI uri = destinationPolicy().checkResolved(doGetPostUrl(messageConfig), hostResolver);
    String jsonMessage = createMessage(message);

    ResponseEntity<String> rc = doSendMessage(jsonMessage, uri);
    postSendMessage(rc, uri, message, subject);

    return rc;
  }

  /**
   * @return the allowed destinations for this sender's webhooks
   */
  protected abstract WebhookDestinationPolicy destinationPolicy();

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
    try {
      ResponseEntity<Void> resp =
          getRestClient()
              .post()
              .uri(uri)
              .contentType(MediaType.APPLICATION_JSON)
              .body(jsonMessage)
              .retrieve()
              .onStatus(
                  HttpStatusCode::isError, AbstractExternalWebhookMessageSender::failWithoutBody)
              .toBodilessEntity(); // the response body is never read into memory
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
  RestClient getRestClient() {
    return restClient;
  }

  /*
   * Package scoped so tests can use short timeouts
   */
  void setTimeouts(Duration connectTimeout, Duration sendTimeout) {
    restClient = createRestClient(connectTimeout, sendTimeout);
  }

  private static RestClient createRestClient(Duration connectTimeout, Duration sendTimeout) {
    HttpClient client =
        HttpClient.newBuilder()
            .connectTimeout(connectTimeout)
            .followRedirects(HttpClient.Redirect.NEVER)
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
    factory.setReadTimeout(sendTimeout);
    return RestClient.builder().requestFactory(factory).build();
  }

  /**
   * Raises the usual status exceptions without the error response body, which RestClient's default
   * handler would buffer, or the URL, which it would put in the message (a webhook URL is a
   * credential, RSDEV-1525).
   */
  private static void failWithoutBody(HttpRequest request, ClientHttpResponse response)
      throws IOException {
    HttpStatusCode statusCode = response.getStatusCode();
    String statusText = response.getStatusText();
    HttpHeaders headers = response.getHeaders();
    if (statusCode.is4xxClientError()) {
      throw HttpClientErrorException.create(statusCode, statusText, headers, null, null);
    }
    throw HttpServerErrorException.create(statusCode, statusText, headers, null, null);
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
