package com.researchspace.slack;

import static org.junit.Assert.assertTrue;

import com.researchspace.service.impl.ConditionalTestRunnerNotSpring;
import com.researchspace.service.impl.RunIfSystemPropertyDefined;
import java.net.URI;
import java.net.URISyntaxException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

@RunWith(ConditionalTestRunnerNotSpring.class)
public class SlackPosterTest {

  public static final String RSPACEDEV_GENERAL_WEBHOOK =
      "https://hooks.slack.com/services/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";

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
    uri = new URI(RSPACEDEV_GENERAL_WEBHOOK);
    HttpEntity<String> requestEntity = new HttpEntity<String>(jsonMessage);
    ResponseEntity<String> resp = template.postForEntity(uri, requestEntity, String.class);
    assertTrue(HttpStatus.OK.equals(resp.getStatusCode()));
  }
}
