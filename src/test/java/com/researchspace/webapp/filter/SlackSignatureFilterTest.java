package com.researchspace.webapp.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SlackSignatureFilterTest {
  private static final String SECRET = "signing-secret";
  private static final long NOW = 1700000000;
  private final SlackSignatureFilter filter =
      new SlackSignatureFilter(SECRET, Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC));

  @Test
  void validSignedBodyIsTheOnlySourceOfControllerParameters() throws Exception {
    var request = request("text=hello+world%2B&user_id=U1", NOW);
    var response = new MockHttpServletResponse();
    AtomicBoolean called = new AtomicBoolean();
    filter.doFilter(
        request,
        response,
        (req, res) -> {
          HttpServletRequest form = (HttpServletRequest) req;
          assertEquals("hello world+", form.getParameter("text"));
          assertEquals("U1", form.getParameterMap().get("user_id")[0]);
          called.set(true);
        });
    assertTrue(called.get());
  }

  @Test
  void missingTamperedStaleAndUnsignedQueryRequestsNeverReachController() throws Exception {
    var tampered = request("text=original", NOW);
    tampered.setContent("text=attacker".getBytes(StandardCharsets.UTF_8));
    reject(tampered);
    reject(request("text=hello", NOW - 301));
    reject(request("text=hello", NOW + 301));
    var missing = request("text=hello", NOW);
    missing.removeHeader("X-Slack-Signature");
    reject(missing);
    var query = request("text=hello", NOW);
    query.setQueryString("user_id=attacker");
    reject(query);
    var duplicate = request("text=hello&text=attacker", NOW);
    var response = new MockHttpServletResponse();
    filter.doFilter(
        duplicate,
        response,
        (req, res) -> {
          throw new AssertionError();
        });
    assertEquals(400, response.getStatus());
    var disabled = new SlackSignatureFilter("");
    response = new MockHttpServletResponse();
    disabled.doFilter(
        request("text=hello", NOW),
        response,
        (req, res) -> {
          throw new AssertionError();
        });
    assertEquals(401, response.getStatus());
  }

  private void reject(MockHttpServletRequest request) throws Exception {
    var response = new MockHttpServletResponse();
    AtomicBoolean called = new AtomicBoolean();
    filter.doFilter(request, response, (req, res) -> called.set(true));
    assertFalse(called.get());
    assertEquals(401, response.getStatus());
  }

  private MockHttpServletRequest request(String body, long timestamp) throws Exception {
    var request = new MockHttpServletRequest("POST", "/slack/callbacks/search");
    request.setContentType("application/x-www-form-urlencoded; charset=UTF-8");
    request.setContent(body.getBytes(StandardCharsets.UTF_8));
    request.addHeader("X-Slack-Request-Timestamp", Long.toString(timestamp));
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    request.addHeader(
        "X-Slack-Signature",
        "v0="
            + HexFormat.of()
                .formatHex(
                    mac.doFinal(
                        ("v0:" + timestamp + ":" + body).getBytes(StandardCharsets.UTF_8))));
    return request;
  }
}
