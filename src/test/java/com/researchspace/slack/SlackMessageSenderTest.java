package com.researchspace.slack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.analytics.service.AnalyticsManager;
import com.researchspace.extmessages.base.ExternalMessageSender;
import com.researchspace.extmessages.base.MessageDetails;
import com.researchspace.model.User;
import com.researchspace.model.apps.App;
import com.researchspace.model.apps.AppConfigElement;
import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.model.apps.UserAppConfig;
import com.researchspace.model.core.IRSpaceDoc;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.testutils.TestFactory;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;

public class SlackMessageSenderTest {

  private ExternalMessageSender slackSender;

  @BeforeEach
  public void setUp() throws Exception {
    slackSender = new SlackMessageSender();
  }

  @Test
  public void testUnsupportedAppThrowsIAE() {
    App unsupported = new App("any", "label", false);
    assertFalse(slackSender.supportsApp(unsupported));
    User anyUser = TestFactory.createAnyUser("any");
    AppConfigElementSet set = new AppConfigElementSet();
    UserAppConfig cfg = new UserAppConfig(anyUser, unsupported, true);

    assertThrows(IllegalArgumentException.class, () -> cfg.addConfigSet(set));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "これはテストです。this is a test",
        "中文 한국어",
        "café cafe\u0301",
        "العربية עברית",
        "हिन्दी",
        "𠮷 😀",
        "試料 α μm −20 °C “quote”",
        "Newline\nTab\tQuote\"Backslash\\",
        "ASCII message<br/>second line"
      })
  void sendsUtf8JsonWithoutChangingText(String message) throws Exception {
    CompletableFuture<CapturedRequest> captured = new CompletableFuture<>();
    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/slack",
        exchange -> {
          try (var body = exchange.getRequestBody()) {
            captured.complete(
                new CapturedRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    body.readAllBytes()));
            exchange.sendResponseHeaders(200, -1);
          } finally {
            exchange.close();
          }
        });
    server.start();
    try {
      User user = TestFactory.createAnyUser("sender");
      user.setFirstName("太郎");
      user.setLastName("研究者");
      App app = new App(App.APP_SLACK, "Slack", true);
      AppConfigElement webhook = mock(AppConfigElement.class);
      when(webhook.getValue())
          .thenReturn("http://localhost:" + server.getAddress().getPort() + "/slack");
      AppConfigElementSet config = mock(AppConfigElementSet.class);
      when(config.getApp()).thenReturn(app);
      when(config.getUserAppConfig()).thenReturn(new UserAppConfig(user, app, true));
      when(config.findElementByPropertyName("SLACK_WEBHOOK_URL")).thenReturn(webhook);

      IPropertyHolder props = mock(IPropertyHolder.class);
      when(props.getServerUrl()).thenReturn("https://rspace.example.com");
      ReflectionTestUtils.setField(slackSender, "props", props);
      ReflectionTestUtils.setField(slackSender, "analyticsMgr", mock(AnalyticsManager.class));
      IRSpaceDoc document = mock(IRSpaceDoc.class);
      when(document.getName()).thenReturn("試料 😀");
      when(document.getGlobalIdentifier()).thenReturn("SD123");
      when(document.getOwner()).thenReturn(user);

      var response =
          slackSender.sendMessage(
              new MessageDetails(user, message, List.of(document)), config, user);
      assertEquals(HttpStatus.OK, response.getStatusCode());
      CapturedRequest request = captured.get(5, TimeUnit.SECONDS);
      String json =
          StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(request.body())).toString();
      var payload = new ObjectMapper().readTree(json);
      assertEquals(
          "*From:* " + user.getFullName() + "\n" + message.replace("<br/>", "\n"),
          payload.get("text").asText());
      assertEquals(user.getFullName(), payload.get("user").asText());
      assertEquals(1, payload.get("attachments").size());
      var attachment = payload.get("attachments").get(0);
      assertEquals("試料 😀", attachment.get("title").asText());
      assertEquals(
          "https://rspace.example.com/globalId/SD123", attachment.get("title_link").asText());
      assertEquals("POST", request.method());
      assertEquals(
          new MediaType("application", "json", StandardCharsets.UTF_8),
          MediaType.parseMediaType(request.contentType()));
    } finally {
      server.stop(0);
    }
  }

  private record CapturedRequest(String method, String contentType, byte[] body) {}
}
