package com.researchspace.extmessages.msteams;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.extmessages.base.AbstractExternalWebhookMessageSender;
import com.researchspace.extmessages.base.MessageDetails;
import com.researchspace.model.User;
import com.researchspace.model.apps.App;
import com.researchspace.testutils.SpringTransactionalTest;
import java.net.URI;
import java.net.URISyntaxException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@EnabledIfSystemProperty(named = "nightly", matches = "(|true)")
public class PostToMSTeamsRealConnectionTest extends SpringTransactionalTest {

  @Value("${msteams.realConnectionTest.webhookUrl}")
  private String msTeamsTestWebhookUrl;

  private static final String LREM_IPSUM =
      "Lorem ipsum dolor sit amet, consectetur adipiscing "
          + "elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad"
          + " minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea "
          + "commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse "
          + "cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non "
          + "proident, sunt in culpa qui officia deserunt mollit anim id est laborum.";

  class BasicMsTeamsPoster extends AbstractExternalWebhookMessageSender {
    @Override
    public boolean supportsApp(App app) {
      return true;
    }

    @Override
    protected String createMessage(MessageDetails internalMsg) {
      return null;
    }

    @Override
    protected String getPostUrlSetting() {
      return null;
    }

    protected ResponseEntity<String> doSendMessage(String jsonMessage, URI uri) {
      return super.doSendMessage(jsonMessage, uri);
    }

    @Override
    protected HttpHeaders createPostHeaders() {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      return headers;
    }

    @Override
    protected void postSendMessage(
        ResponseEntity<String> rc, URI uri, MessageDetails message, User subject) {}
  }

  BasicMsTeamsPoster poster = null;

  @BeforeEach
  public void setUp() throws Exception {
    poster = new BasicMsTeamsPoster();
  }

  @Test
  public void postToMSTeam() throws URISyntaxException {
    AdaptiveCard card = new AdaptiveCard();
    card.getBody().add(TextBlock.heading("From Bob Smith"));
    card.getBody()
        .add(
            TextBlock.body(
                "Message about [SD4005](https://ops.researchspace.com/globalId/SD4005)"));
    card.getBody().add(TextBlock.body(LREM_IPSUM));
    FactSet facts = new FactSet();
    facts.add("Owner", "Bob Smith");
    facts.add("ID", "SD4005");
    card.getBody().add(facts);

    AdaptiveCardMessage payload = new AdaptiveCardMessage();
    payload.addCard(card);

    ResponseEntity<String> response =
        poster.doSendMessage(payload.toJSON(), new URI(msTeamsTestWebhookUrl));
    assertTrue(
        response.getStatusCode().is2xxSuccessful(),
        "Workflows webhook should accept the Adaptive Card");
  }
}
