package com.researchspace.extmessages.base;

import static com.researchspace.testutils.RSpaceTestUtils.assertAuthExceptionThrown;
import static com.researchspace.testutils.SystemPropertyTestFactory.createAnyAppWithConfigElements;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.apps.UserAppConfig;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.testutils.TestFactory;
import java.net.URI;
import java.net.URISyntaxException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
public class ExternalMessageSenderTest {
  @Mock MessageSourceUtils messages;
  @Mock RestTemplate template;
  @InjectMocks DummyExternalMessageSender msteamsSender;
  private User sender;

  @BeforeEach
  public void setUp() throws Exception {
    sender = TestFactory.createAnyUser("sender");
  }

  @AfterEach
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
    assertThrows(
        IllegalArgumentException.class,
        () ->
            msteamsSender.sendMessage(
                null, cfg.getAppConfigElementSets().iterator().next(), sender));
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
    verify(messages)
        .getMessage(
            Mockito.eq("errors.authorization.failure.sendExternalMessage"),
            aryEq(new Object[] {imposter.getUsername()}));
  }
}
