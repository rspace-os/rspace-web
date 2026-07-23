package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.comms.CommunicationStatus;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageType;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.UserLocaleService;
import java.io.Writer;
import java.util.Locale;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.context.Context;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RequestNotificationMessageGeneratorUnitTest {

  @Test
  void templateFailureReturnsLocalizedFallbackInsteadOfInternalException() {
    VelocityEngine velocity = mock(VelocityEngine.class);
    doThrow(new ResourceNotFoundException("sensitive template path"))
        .when(velocity)
        .mergeTemplate(anyString(), anyString(), any(Context.class), any(Writer.class));

    RequestNotificationMessageGenerator generator = new RequestNotificationMessageGenerator();
    generator.velocity = velocity;
    MessageSourceUtils messages = mock(MessageSourceUtils.class);
    UserLocaleService userLocaleService = mock(UserLocaleService.class);
    Locale recipientLocale = Locale.CANADA_FRENCH;
    when(userLocaleService.getLocaleFor(any(User.class))).thenReturn(recipientLocale);
    when(messages.getMessageForLocale(
            eq("email.requestStatus.generationFailed"), eq(recipientLocale)))
        .thenReturn("localized fallback");
    ReflectionTestUtils.setField(generator, "messages", messages);
    ReflectionTestUtils.setField(generator, "userLocaleService", userLocaleService);

    MessageOrRequest request = new MessageOrRequest(MessageType.REQUEST_RECORD_WITNESS);
    String result =
        generator.generateMsgOnRequestStatusUpdate(
            new User("recipient"), CommunicationStatus.COMPLETED, CommunicationStatus.NEW, request);

    assertEquals("localized fallback", result);
    verify(messages).getMessageForLocale("email.requestStatus.generationFailed", recipientLocale);
  }
}
