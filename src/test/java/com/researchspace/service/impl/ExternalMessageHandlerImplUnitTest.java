package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.researchspace.extmessages.base.ExternalMessageSender;
import com.researchspace.model.User;
import com.researchspace.model.apps.App;
import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.model.apps.UserAppConfig;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.ExternalMessageHandler;
import com.researchspace.service.ExternalMessageSenderFactory;
import com.researchspace.service.MessageOrRequestCreatorManager;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.UserAppConfigManager;
import com.researchspace.testutils.TestFactory;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

@ExtendWith(MockitoExtension.class)
public class ExternalMessageHandlerImplUnitTest {

  private static final Long CFG_SET_ID = 1L;

  @Mock private UserAppConfigManager userAppMgr;
  @Mock private ExternalMessageSenderFactory messageSenderFactory;
  @Mock private IPermissionUtils permUtils;
  @Mock private MessageSourceUtils messageSource;
  @Mock private MessageOrRequestCreatorManager commMgr;
  @Mock private ExternalMessageSender sender;

  @InjectMocks private ExternalMessageHandlerImpl handler;

  private User user;

  @BeforeEach
  public void setUp() {
    user = TestFactory.createAnyUser("any");
  }

  private AppConfigElementSet setUpConfigForApp(String appName) {
    App app = new App(appName, appName, false);
    UserAppConfig appConfig = new UserAppConfig(user, app, true);
    AppConfigElementSet cfgSet = new AppConfigElementSet();
    cfgSet.setUserAppConfig(appConfig);
    when(userAppMgr.findByAppConfigElementSetId(CFG_SET_ID)).thenReturn(Optional.of(cfgSet));
    when(messageSenderFactory.findMessageSenderForApp(app)).thenReturn(Optional.of(sender));
    return cfgSet;
  }

  private ServiceOperationResult<ResponseEntity<String>> sendMessage() {
    return handler.sendExternalMessage("a message", CFG_SET_ID, Collections.emptyList(), user);
  }

  @Test
  public void unauthorizedFromMsTeamsWebhookReturnsWorkflowTemplateGuidance() {
    setUpConfigForApp(App.APP_MSTEAMS);
    when(sender.sendMessage(any(), any(), eq(user)))
        .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));
    when(messageSource.getMessage(ExternalMessageHandlerImpl.MSTEAMS_UNAUTHORIZED_MSG_KEY))
        .thenReturn("msteams unauthorized guidance");

    ServiceOperationResult<ResponseEntity<String>> result = sendMessage();

    assertFalse(result.isSucceeded());
    assertEquals("msteams unauthorized guidance", result.getMessage());
  }

  @Test
  public void unauthorizedFromOtherAppWebhookReturnsStatusMessage() {
    setUpConfigForApp(App.APP_SLACK);
    when(sender.sendMessage(any(), any(), eq(user)))
        .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));
    when(messageSource.getMessage(
            eq(ExternalMessageHandlerImpl.SEND_FAILED_STATUS_MSG_KEY), any(Object[].class)))
        .thenReturn("webhook returned an error status");

    ServiceOperationResult<ResponseEntity<String>> result = sendMessage();

    assertFalse(result.isSucceeded());
    assertEquals("webhook returned an error status", result.getMessage());
  }

  @Test
  public void clientErrorFromMsTeamsWebhookReturnsStatusMessage() {
    setUpConfigForApp(App.APP_MSTEAMS);
    when(sender.sendMessage(any(), any(), eq(user)))
        .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));
    when(messageSource.getMessage(
            eq(ExternalMessageHandlerImpl.SEND_FAILED_STATUS_MSG_KEY), any(Object[].class)))
        .thenReturn("webhook returned an error status");

    ServiceOperationResult<ResponseEntity<String>> result = sendMessage();

    assertFalse(result.isSucceeded());
    assertEquals("webhook returned an error status", result.getMessage());
  }

  @Test
  public void connectionFailureReturnsGenericFailureMessage() {
    setUpConfigForApp(App.APP_MSTEAMS);
    when(sender.sendMessage(any(), any(), eq(user)))
        .thenThrow(new ResourceAccessException("connection refused"));
    when(messageSource.getMessage(ExternalMessageHandler.SEND_FAILED_MSG_KEY))
        .thenReturn("could not send message");

    ServiceOperationResult<ResponseEntity<String>> result = sendMessage();

    assertFalse(result.isSucceeded());
    assertEquals("could not send message", result.getMessage());
  }

  @Test
  public void successfulSendReturnsSucceededResult() {
    setUpConfigForApp(App.APP_MSTEAMS);
    when(sender.sendMessage(any(), any(), eq(user))).thenReturn(ResponseEntity.ok("1"));

    ServiceOperationResult<ResponseEntity<String>> result = sendMessage();

    assertTrue(result.isSucceeded());
  }
}
