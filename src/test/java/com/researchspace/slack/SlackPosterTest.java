package com.researchspace.slack;

import static org.junit.Assert.assertTrue;

import com.researchspace.service.impl.ConditionalTestRunnerNotSpring;
import com.researchspace.service.impl.RunIfSystemPropertyDefined;
import java.net.URI;
import java.net.URISyntaxException;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

@Ignore("requires correct real secret webhook")
@RunWith(ConditionalTestRunnerNotSpring.class)
public class SlackPosterTest {

  public static final String SLACK_WEBHOOK = "https://hooks.slack.com/services/<secret_webhook>";

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {}

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void test() throws URISyntaxException {
    RestTemplate template = new RestTemplate();
    String converted = ("Hello");
    String jsonMessage = SlackMessageTest.createAnySlackMessage(converted).toJSON();

    URI uri;
    uri = new URI(SLACK_WEBHOOK);
    HttpEntity<String> requestEntity = new HttpEntity<>(jsonMessage);
    ResponseEntity<String> resp = template.postForEntity(uri, requestEntity, String.class);
    assertTrue(HttpStatus.OK.equals(resp.getStatusCode()));
  }
}
