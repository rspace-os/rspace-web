package com.researchspace.extmessages.base;

import static com.researchspace.model.system.SystemPropertyTestFactory.createAnyAppWithConfigElements;
import static com.researchspace.testutils.RSpaceTestUtils.assertAuthExceptionThrown;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.model.User;
import com.researchspace.model.apps.UserAppConfig;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.OperationFailedMessageGenerator;
import java.net.URI;
import java.net.URISyntaxException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

public class ExternalMessageSenderTest {

  public @Rule MockitoRule rule = MockitoJUnit.rule();
  @Mock OperationFailedMessageGenerator authGen;
  @Mock RestTemplate template;
  @InjectMocks DummyExternalMessageSender msteamsSender;
  private User sender;

  @Before
  public void setUp() throws Exception {
    sender = TestFactory.createAnyUser("sender");
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testSendMessageApp() throws RestClientException, URISyntaxException {
    ResponseEntity<String> okResponse = new ResponseEntity<>(HttpStatus.OK);
    when(template.postForEntity(
            Mockito.eq(new URI(msteamsSender.url)),
            Mockito.any(HttpEntity.class),
            Mockito.eq(String.class)))
        .thenReturn(okResponse);
    UserAppConfig cfg = createAnyAppWithConfigElements(sender, "message");
    assertEquals(
        okResponse,
        msteamsSender.sendMessage(null, cfg.getAppConfigElementSets().iterator().next(), sender));
  }

  @Test
  public void testSendMessageAppPostFails() throws RestClientException, URISyntaxException {
    ResponseEntity<String> errorResponse = new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
    when(template.postForEntity(
            Mockito.eq(new URI(msteamsSender.url)),
            Mockito.any(HttpEntity.class),
            Mockito.eq(String.class)))
        .thenReturn(errorResponse);
    UserAppConfig cfg = createAnyAppWithConfigElements(sender, "message");
    assertEquals(
        errorResponse,
        msteamsSender.sendMessage(null, cfg.getAppConfigElementSets().iterator().next(), sender));
  }

  @Test
  public void testSendMessageAppThrowsIAEIfAppNotSupported() throws Exception {
    msteamsSender.supported = false;
    UserAppConfig cfg = createAnyAppWithConfigElements(sender, "message");
    CoreTestUtils.assertExceptionThrown(
        () ->
            msteamsSender.sendMessage(
                null, cfg.getAppConfigElementSets().iterator().next(), sender),
        IllegalArgumentException.class);
    // never invoked
    assertMessageNotPosted();
  }

  private void assertMessageNotPosted() throws URISyntaxException {
    Mockito.verify(template, Mockito.never())
        .postForEntity(
            Mockito.eq(new URI(msteamsSender.url)),
            Mockito.any(HttpEntity.class),
            Mockito.eq(String.class));
  }

  @Test
  public void testSendMessageAppThrowsAuthExceptionIfNotUser() throws Exception {
    User imposter = TestFactory.createAnyUser("imposter");
    UserAppConfig cfg = createAnyAppWithConfigElements(sender, "message");
    assertAuthExceptionThrown(
        () ->
            msteamsSender.sendMessage(
                null, cfg.getAppConfigElementSets().iterator().next(), imposter));
    assertMessageNotPosted();
    verify(authGen).getFailedMessage(Mockito.eq(imposter), Mockito.anyString());
  }
}
