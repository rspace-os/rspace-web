package com.researchspace.slack;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.researchspace.service.impl.ConditionalTestRunner;
import com.researchspace.service.impl.ConditionalTestRunnerNotSpring;
import com.researchspace.service.impl.RunIfSystemPropertyDefined;
import com.researchspace.session.SessionTimeZoneUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

@Ignore("requires correct real secret webhook")
@RunWith(ConditionalTestRunner.class)
public class SlackPosterTest extends SpringTransactionalTest {

  @Value("${slack.realConnectionTest.webhookUrl}")
  private String slackTestWebhookUrl;

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {}

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void basicTest() throws URISyntaxException {
    RestTemplate template = new RestTemplate();
    String converted = ("Hello");
    String jsonMessage = SlackMessageTest.createAnySlackMessage(converted).toJSON();

    URI uri = new URI(slackTestWebhookUrl);
    HttpEntity<String> requestEntity = new HttpEntity<>(jsonMessage);
    ResponseEntity<String> resp = template.postForEntity(uri, requestEntity, String.class);
    assertTrue(HttpStatus.OK.equals(resp.getStatusCode()));
  }

  // curl -X POST --data-urlencode 'payload={"channel": "#ops-rspace", "username": "rspaceDev",
  // "text": "posted to #rspace-slackpost-test and comes from rspaceDev", "icon_emoji": ":ghost:"}'
  // https://hooks.slack.com/services/<webhook_url>

  @Test
  @RunIfSystemPropertyDefined("nightly")
  public void testSlackTemplate() throws URISyntaxException, JsonProcessingException {
    RestTemplate template = new RestTemplate();
    final ObjectMapper mapper = new ObjectMapper();
    ObjectNode node = mapper.createObjectNode();
    node.put("channel", "#rspace-slackpost-test");
    node.put("username", "rspaceDev");
    String text =
        "posted from slack test at " + new SessionTimeZoneUtils().formatDateForClient(new Date());
    node.put("text", text);
    String json = mapper.writeValueAsString(node);
    System.err.println("json is " + json);
    URI uri = new URI(slackTestWebhookUrl);
    HttpEntity<String> requestEntity = new HttpEntity<>(json);
    ResponseEntity<String> resp = template.postForEntity(uri, requestEntity, String.class);
    assertEquals(HttpStatus.OK, resp.getStatusCode());
    System.err.println(resp);
  }


}
