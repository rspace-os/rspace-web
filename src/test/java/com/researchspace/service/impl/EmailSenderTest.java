package com.researchspace.service.impl;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.service.EmailContent;
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
    EmailBroadcastImpl emailSender = new FailingSenderStub(new SendFailedException("test failure"));
    emailSender.setErrorLog(logger);
    emailSender.setRetryDelayMillis(RETRY_MILLIS);
    emailSender.init();
    emailSender.sendHtmlEmail(
        "any", anyHtmlBody(), Collections.emptyList(), new MessageOrRequest());

    verify(logger, times(3)).warn(Mockito.anyString(), new Object[] {Mockito.any()});
  }

  @Test
  public void whenAuthFailsDontRetry() {
    EmailBroadcastImpl emailSender =
        new FailingSenderStub(new AuthenticationFailedException("test failure"));
    emailSender.setErrorLog(logger);
    emailSender.setRetryDelayMillis(RETRY_MILLIS);
    emailSender.init();
    emailSender.sendHtmlEmail(
        "any", anyHtmlBody(), Collections.emptyList(), new MessageOrRequest());
    // log error once, no retries
    Mockito.verify(logger, Mockito.times(1))
        .error(Mockito.anyString(), Mockito.anyString(), Mockito.any(), Mockito.any());
  }

  static class FailingSenderStub extends EmailBroadcastImpl {
    private final MessagingException failure;

    FailingSenderStub(MessagingException failure) {
      super(new EmailContentGenerator(), "http://localhost:8080");
      this.failure = failure;
    }

    @Override
    protected void sendMailToAddresses(EmailConfig config) throws MessagingException {
      throw failure;
    }
  }

  private EmailContent anyHtmlBody() {
    return new EmailContent(null, "<html><body>body</body></html>", "body");
  }
}
