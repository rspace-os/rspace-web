package com.researchspace.service.impl;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.researchspace.core.util.TimeSource;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.service.impl.EmailBroadcastImp.EmailContent;
import java.util.Collections;
import javax.mail.AuthenticationFailedException;
import javax.mail.MessagingException;
import javax.mail.SendFailedException;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.slf4j.Logger;

public class EmailSenderTest {

  public @Rule MockitoRule mockito = MockitoJUnit.rule();
  EmailBroadcastImp emailImpl;
  @Mock TimeSource timeSource;
  @Mock Logger logger;

  final Integer maxRate = 11;

  final int expectedRetries = 3;

  @Before
  public void before() {
    emailImpl = new EmailBroadcastImp();
    // iniitial time is 0
    Mockito.when(timeSource.now()).thenReturn(new DateTime(0));
    emailImpl.setMaxSendingRate(maxRate);
    emailImpl.setErrorLog(logger);
    emailImpl.init();
  }

  class SendFailureEmailSenderTSS extends EmailBroadcastImp {
    @Override
    protected void sendMailToAddresses(EmailConfig config) throws MessagingException {
      throw new SendFailedException("test send failure");
    }
  }

  @Test
  public void retryIfSendExceptionThrown() {
    emailImpl = new SendFailureEmailSenderTSS();
    emailImpl.init();
    emailImpl.setErrorLog(logger);
    // this will throw send failure exception on each call
    emailImpl.sendHtmlEmail("any", anyHtmlBody(), Collections.emptyList(), new MessageOrRequest());

    verify(logger, times(expectedRetries)).warn(Mockito.anyString(), new Object[] {Mockito.any()});
  }

  class AuthFailureEmailSenderTSS extends EmailBroadcastImp {
    @Override
    protected void sendMailToAddresses(EmailConfig config) throws MessagingException {
      throw new AuthenticationFailedException("test auth failure");
    }
  }

  @Test
  public void noretryIfAuthExceptionThrown() {
    emailImpl = new AuthFailureEmailSenderTSS();
    emailImpl.init();
    emailImpl.setErrorLog(logger);
    emailImpl.sendHtmlEmail("any", anyHtmlBody(), Collections.emptyList(), new MessageOrRequest());
    // log error once, no retries
    Mockito.verify(logger, Mockito.times(1))
        .error(Mockito.anyString(), Mockito.anyString(), Mockito.any(), Mockito.any());
  }

  private EmailContent anyHtmlBody() {
    return EmailContent.builder().htmlContent("body").build();
  }
}
