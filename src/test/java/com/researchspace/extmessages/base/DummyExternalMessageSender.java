package com.researchspace.extmessages.base;

import com.researchspace.model.User;
import com.researchspace.model.apps.App;
import com.researchspace.model.apps.AppConfigElementSet;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

class DummyExternalMessageSender extends AbstractExternalWebhookMessageSender {

  RestTemplate template;

  boolean supported = true;
  String message = "{'text':'some json'}";
  String url = "http://a.b.c";

  @Override
  public boolean supportsApp(App app) {
    return supported;
  }

  @Override
  protected String createMessage(MessageDetails internalMsg) {
    return message;
  }

  @Override
  protected String getPostUrlSetting() {
    return url;
  }

  RestTemplate getRestTemplate() {
    return template;
  }

  void setTemplate(RestTemplate template) {
    this.template = template;
  }

  String doGetPostUrl(AppConfigElementSet messageConfig) {
    return url;
  }

  @Override
  protected void postSendMessage(
      ResponseEntity<String> rc, URI uri, MessageDetails message, User subject) {}
}
