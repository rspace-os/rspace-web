package com.researchspace.extmessages.msteams;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.extmessages.base.MessageDetails;
import com.researchspace.model.User;
import com.researchspace.model.apps.App;
import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.model.apps.UserAppConfig;
import com.researchspace.model.core.IRSpaceDoc;
import com.researchspace.model.core.Person;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.UserConnectionManager;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class MsTeamsMessageSenderTest {

  private final ObjectMapper mapper = new ObjectMapper();
  MsTeamsMessageSender msteamsSender;
  UserConnectionManager userConnectionManager;

  @BeforeEach
  public void setUp() throws Exception {
    userConnectionManager = mock(UserConnectionManager.class);
    msteamsSender = new MsTeamsMessageSender(userConnectionManager);
  }

  @Test
  public void testSupportsApp() {
    assertTrue(msteamsSender.supportsApp(new App(App.APP_MSTEAMS, "any", false)));
    assertFalse(msteamsSender.supportsApp(new App(App.APP_SLACK, "any", false)));
  }

  @Test
  public void webhookUrlComesFromEncryptedUserConnection() {
    String username = "user";
    String configSetId = "42";
    User user = mock(User.class);
    when(user.getUsername()).thenReturn(username);
    AppConfigElementSet configSet = mock(AppConfigElementSet.class);
    when(configSet.getId()).thenReturn(42L);
    UserAppConfig appConfig = mock(UserAppConfig.class);
    when(configSet.getUserAppConfig()).thenReturn(appConfig);
    when(appConfig.getUser()).thenReturn(user);
    UserConnection connection =
        new UserConnection(new UserConnectionId(username, "MSTEAMS", configSetId), "");
    connection.setSecret("https://teams.example/webhook");
    when(userConnectionManager.findByUserNameProviderName(username, "MSTEAMS", configSetId))
        .thenReturn(Optional.of(connection));

    assertEquals("https://teams.example/webhook", msteamsSender.doGetPostUrl(configSet));
  }

  @Test
  public void messageWithoutDocumentsBuildsAdaptiveCardEnvelope() throws Exception {
    Person originator = mock(Person.class);
    when(originator.getFullName()).thenReturn("Jane Doe");
    MessageDetails details =
        new MessageDetails(originator, "hello<br/>world", Collections.emptyList());

    JsonNode payload = mapper.readTree(invokeCreateMessage(details));

    assertEquals("message", payload.get("type").asText());
    JsonNode attachment = payload.get("attachments").get(0);
    assertEquals("application/vnd.microsoft.card.adaptive", attachment.get("contentType").asText());
    JsonNode card = attachment.get("content");
    assertEquals("AdaptiveCard", card.get("type").asText());
    JsonNode body = card.get("body");
    assertEquals("Message from Jane Doe", body.get(0).get("text").asText());
    // <br/> from the form is converted to a newline
    assertEquals("hello\nworld", body.get(1).get("text").asText());
  }

  @Test
  public void messageWithDocumentBuildsSummaryAndFacts() throws Exception {
    Person originator = mock(Person.class);
    when(originator.getFullName()).thenReturn("Jane Doe");
    Person owner = mock(Person.class);
    when(owner.getFullName()).thenReturn("Bob Smith");
    IRSpaceDoc doc = mock(IRSpaceDoc.class);
    when(doc.getName()).thenReturn("My experiment");
    when(doc.getGlobalIdentifier()).thenReturn("SD4005");
    when(doc.getOwner()).thenReturn(owner);

    IPropertyHolder props = mock(IPropertyHolder.class);
    when(props.getServerUrl()).thenReturn("https://rspace.example.com");
    ReflectionTestUtils.setField(msteamsSender, "props", props);

    MessageDetails details =
        new MessageDetails(originator, "please review", Collections.singletonList(doc));

    JsonNode body =
        mapper
            .readTree(invokeCreateMessage(details))
            .get("attachments")
            .get(0)
            .get("content")
            .get("body");

    assertEquals("From Jane Doe", body.get(0).get("text").asText());
    assertEquals("please review", body.get(1).get("text").asText());
    assertEquals(
        "Message about [My experiment](https://rspace.example.com/globalId/SD4005)",
        body.get(2).get("text").asText());
    JsonNode factSet = body.get(3);
    assertEquals("FactSet", factSet.get("type").asText());
    assertEquals("Owner", factSet.get("facts").get(0).get("title").asText());
    assertEquals("Bob Smith", factSet.get("facts").get(0).get("value").asText());
    assertEquals("ID", factSet.get("facts").get(1).get("title").asText());
    assertEquals("SD4005", factSet.get("facts").get(1).get("value").asText());
  }

  private String invokeCreateMessage(MessageDetails details) {
    return (String) ReflectionTestUtils.invokeMethod(msteamsSender, "createMessage", details);
  }
}
