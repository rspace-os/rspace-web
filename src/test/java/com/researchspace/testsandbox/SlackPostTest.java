package com.researchspace.testsandbox;

import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.researchspace.service.impl.ConditionalTestRunnerNotSpring;
import com.researchspace.service.impl.RunIfSystemPropertyDefined;
import com.researchspace.session.SessionTimeZoneUtils;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

@RunWith(ConditionalTestRunnerNotSpring.class)
public class SlackPostTest {

  // curl -X POST --data-urlencode 'payload={"channel": "#ops-rspace", "username": "richard",
  // "text": "posted to #ops-rspace and comes from richard", "icon_emoji": ":ghost:"}'
  // https://hooks.slack.com/services/<webhook_url>

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {}

  @Test
  @RunIfSystemPropertyDefined("nightly")
  public void testSlackTemplate() throws URISyntaxException, JsonProcessingException {
    RestTemplate template = new RestTemplate();
    final ObjectMapper mapper = new ObjectMapper();
    ObjectNode node = mapper.createObjectNode();
    node.put("channel", "#ops-rspace");
    node.put("username", "richard");
    String text =
        "posted from slack test at " + new SessionTimeZoneUtils().formatDateForClient(new Date());
    node.put("text", text);
    String json = mapper.writeValueAsString(node);
    System.err.println("json is " + json);
    URI uri =
        new URI("https://hooks.slack.com/services/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");
    HttpEntity<String> requestEntity = new HttpEntity<String>(json);
    ResponseEntity<String> resp = template.postForEntity(uri, requestEntity, String.class);
    assertEquals(HttpStatus.OK, resp.getStatusCode());
    System.err.println(resp);
  }
}
