package com.researchspace.service.impl;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.service.impl.EmailBroadcastImp.EmailContent;
import java.util.Collections;
import javax.mail.AuthenticationFailedException;
import javax.mail.MessagingException;
import javax.mail.SendFailedException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;

@RunWith(MockitoJUnitRunner.class)
public class EmailSenderTest {

  @Mock Logger logger;

  // override default retry config to make tests faster (10ms is the minimum permitted by retry
  // library)
  private static final int RETRY_MILLIS = 10;

  @Test
  public void whenSendFailsRetryUntilMaxAttempts() {
    EmailBroadcastImp emailSender = new SendFailureStub();
    emailSender.setErrorLog(logger);
    emailSender.setRetryDelayMillis(RETRY_MILLIS);
    emailSender.init();
    emailSender.retryConfig.getIntervalFunction();
    // this will throw send failure exception on each call
    emailSender.sendHtmlEmail(
        "any", anyHtmlBody(), Collections.emptyList(), new MessageOrRequest());

    verify(logger, times(3)).warn(Mockito.anyString(), new Object[] {Mockito.any()});
  }

  @Test
  public void whenAuthFailsDontRetry() {
    EmailBroadcastImp emailSender = new AuthFailureStub();
    emailSender.setErrorLog(logger);
    emailSender.setRetryDelayMillis(RETRY_MILLIS);
    emailSender.init();
    emailSender.sendHtmlEmail(
        "any", anyHtmlBody(), Collections.emptyList(), new MessageOrRequest());
    // log error once, no retries
    Mockito.verify(logger, Mockito.times(1))
        .error(Mockito.anyString(), Mockito.anyString(), Mockito.any(), Mockito.any());
  }

  static class SendFailureStub extends EmailBroadcastImp {
    @Override
    protected void sendMailToAddresses(EmailConfig config) throws MessagingException {
      throw new SendFailedException("test send failure");
    }
  }

  static class AuthFailureStub extends EmailBroadcastImp {
    @Override
    protected void sendMailToAddresses(EmailConfig config) throws MessagingException {
      throw new AuthenticationFailedException("test auth failure");
    }
  }

  private EmailContent anyHtmlBody() {
    return EmailContent.builder().htmlContent("body").build();
  }
}
