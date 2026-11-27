package com.researchspace.extmessages.base;

import static com.researchspace.testutils.SystemPropertyTestFactory.createAnyAppWithConfigElements;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.researchspace.model.User;
import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.testutils.TestFactory;
import java.nio.charset.StandardCharsets;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
public class ExternalMessageSenderTest {
  @Mock MessageSourceUtils messages;
  @InjectMocks DummyExternalMessageSender msteamsSender;
  private MockRestServiceServer server;
  private User sender;

  @BeforeEach
  public void setUp() {
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    msteamsSender.restClient = builder.build();
    sender = TestFactory.createAnyUser("sender");
  }

  @Test
  public void testSendMessageApp() {
    msteamsSender.message = "{\"text\":\"Grüße ✓\"}";
    server
        .expect(requestTo(msteamsSender.url))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        // UTF-8: as ISO-8859-1, non-Latin-1 characters would become "?"
        .andExpect(content().bytes(msteamsSender.message.getBytes(StandardCharsets.UTF_8)))
        .andRespond(withSuccess());

    assertEquals(
        HttpStatus.OK, msteamsSender.sendMessage(null, config(sender), sender).getStatusCode());
    server.verify();
  }

  @Test
  public void testSendMessageAppPostFails() {
    server
        .expect(requestTo(msteamsSender.url))
        .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("error details"));
    AppConfigElementSet config = config(sender);

    HttpServerErrorException e =
        assertThrows(
            HttpServerErrorException.class, () -> msteamsSender.sendMessage(null, config, sender));

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatusCode());
    assertEquals(0, e.getResponseBodyAsByteArray().length); // the error body is never read
  }

  @Test
  public void testSendMessageAppThrowsIAEIfAppNotSupported() {
    msteamsSender.supported = false;
    AppConfigElementSet config = config(sender);
    assertThrows(
        IllegalArgumentException.class, () -> msteamsSender.sendMessage(null, config, sender));
    server.verify(); // nothing was posted
  }

  @Test
  public void loopbackWebhookIsRejectedBeforePosting() {
    msteamsSender.url = "http://127.0.0.1:9988/x";
    AppConfigElementSet config = config(sender);
    assertThrows(
        WebhookUrlNotAllowedException.class, () -> msteamsSender.sendMessage(null, config, sender));
    server.verify(); // nothing was posted
  }

  @Test
  public void testSendMessageAppThrowsAuthExceptionIfNotUser() {
    User imposter = TestFactory.createAnyUser("imposter");
    AppConfigElementSet config = config(sender);
    assertThrows(
        AuthorizationException.class, () -> msteamsSender.sendMessage(null, config, imposter));
    server.verify(); // nothing was posted
    verify(messages)
        .getMessage(
            Mockito.eq("errors.authorization.failure.sendExternalMessage"),
            aryEq(new Object[] {imposter.getUsername()}));
  }

  private static AppConfigElementSet config(User owner) {
    return createAnyAppWithConfigElements(owner, "message")
        .getAppConfigElementSets()
        .iterator()
        .next();
  }
}
