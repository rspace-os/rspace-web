package com.researchspace.extmessages.msteams;

import static org.junit.Assert.assertEquals;

import com.researchspace.core.util.TransformerUtils;
import com.researchspace.extmessages.base.AbstractExternalWebhookMessageSender;
import com.researchspace.extmessages.base.MessageDetails;
import com.researchspace.model.User;
import com.researchspace.model.apps.App;
import com.researchspace.service.impl.ConditionalTestRunner;
import com.researchspace.service.impl.RunIfSystemPropertyDefined;
import com.researchspace.testutils.SpringTransactionalTest;
import java.net.URI;
import java.net.URISyntaxException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;

@RunWith(ConditionalTestRunner.class)
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
    protected void postSendMessage(
        ResponseEntity<String> rc, URI uri, MessageDetails message, User subject) {}
  }

  BasicMsTeamsPoster poster = null;

  @Before
  public void setUp() throws Exception {
    poster = new BasicMsTeamsPoster();
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void postToMSTeam() throws URISyntaxException {
    Fact owner = new Fact("Owner", "Bob Smith");
    Fact id = new Fact("ID", "SD4005");
    Section section = new Section();
    section.setFacts(TransformerUtils.toList(owner, id));
    section.setActivityTitle(
        "Message about [SD4005](https://ops.researchspace.com/globalId/SD4005)");
    section.setActivityText(LREM_IPSUM);
    MSCard card = new MSCard();
    card.setSections(TransformerUtils.toList(section));
    card.setSummary("Message about [SD4005](https://ops.researchspace.com/globalId/SD4005");
    String json = card.toJSON();
    assertEquals("1", poster.doSendMessage(json, new URI(msTeamsTestWebhookUrl)).getBody());
  }
}
