package com.researchspace.extmessages.base;

import com.researchspace.model.User;
import com.researchspace.model.apps.App;
import com.researchspace.model.apps.AppConfigElementSet;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

class DummyExternalMessageSender extends AbstractExternalWebhookMessageSender {

  RestClient restClient;

  boolean supported = true;
  String message = "{'text':'some json'}";
  String url = "https://hooks.example.test/x";
  WebhookDestinationPolicy policy =
      WebhookDestinationPolicy.parse("test.allowed.origins", "https://hooks.example.test");

  @Override
  public boolean supportsApp(App app) {
    return supported;
  }

  @Override
  protected String createMessage(MessageDetails internalMsg) {
    return message;
  }

  @Override
  RestClient getRestClient() {
    return restClient != null ? restClient : super.getRestClient();
  }

  @Override
  protected WebhookDestinationPolicy destinationPolicy() {
    return policy;
  }

  @Override
  protected String doGetPostUrl(AppConfigElementSet messageConfig) {
    return url;
  }

  @Override
  protected void postSendMessage(
      ResponseEntity<String> rc, URI uri, MessageDetails message, User subject) {}
}
