package com.researchspace.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.service.EmailContent;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
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
    emailSender.sendEmail(anyHtmlBody(), List.of("user@example.com"), new MessageOrRequest());

    verify(logger, times(3)).warn(Mockito.anyString(), new Object[] {Mockito.any()});
  }

  @Test
  public void whenAuthFailsDontRetry() {
    EmailBroadcastImpl emailSender =
        new FailingSenderStub(new AuthenticationFailedException("test failure"));
    emailSender.setErrorLog(logger);
    emailSender.setRetryDelayMillis(RETRY_MILLIS);
    emailSender.init();
    emailSender.sendEmail(anyHtmlBody(), List.of("user@example.com"), new MessageOrRequest());
    // log error once, no retries
    Mockito.verify(logger, Mockito.times(1))
        .error(Mockito.anyString(), Mockito.anyString(), Mockito.any(), Mockito.any());
  }

  @Test
  public void batchesRecipientsByAddressLimit() {
    CountingSenderStub sender = countingSender();

    sender.sendEmail(anyHtmlBody(), addresses(7), null);
    assertEquals(3, sender.messageCount);

    sender.messageCount = 0;
    sender.sendEmail(anyHtmlBody(), addresses(3), null);
    assertEquals(1, sender.messageCount);

    sender.messageCount = 0;
    sender.sendEmail(anyHtmlBody(), List.of(), null);
    assertEquals(0, sender.messageCount);
  }

  @Test
  public void rateLimiterRejectsExcessBatches() {
    CountingSenderStub sender = countingSenderWithRateLimit(Duration.ofMillis(1));

    assertThrows(
        RequestNotPermitted.class, () -> sender.sendEmail(anyHtmlBody(), addresses(7), null));
  }

  @Test
  public void rateLimiterWaitsForCapacity() {
    CountingSenderStub sender = countingSenderWithRateLimit(Duration.ofSeconds(1));

    sender.sendEmail(anyHtmlBody(), addresses(7), null);

    assertEquals(3, sender.messageCount);
  }

  @Test
  public void authenticationFailuresAreNotRetried() {
    RetryConfig config = new EmailBroadcastImpl(5, 25).buildRetryConfig();

    assertFalse(config.getExceptionPredicate().test(new AuthenticationFailedException()));
    assertTrue(config.getExceptionPredicate().test(new MessagingException()));
  }

  @Test
  public void rejectsInvalidDeliveryLimits() {
    assertThrows(IllegalArgumentException.class, () -> new EmailBroadcastImpl(0, 25));
    assertThrows(IllegalArgumentException.class, () -> new EmailBroadcastImpl(5, 0));
  }

  static class FailingSenderStub extends EmailBroadcastImpl {
    private final MessagingException failure;

    FailingSenderStub(MessagingException failure) {
      super(5, 25);
      this.failure = failure;
    }

    @Override
    protected void sendMailToAddresses(EmailConfig config) throws MessagingException {
      throw failure;
    }
  }

  static class CountingSenderStub extends EmailBroadcastImpl {
    int messageCount;

    CountingSenderStub() {
      super(5, 25);
    }

    @Override
    protected void sendMailToAddresses(EmailConfig config) {
      messageCount++;
    }
  }

  private CountingSenderStub countingSender() {
    CountingSenderStub sender = new CountingSenderStub();
    sender.init();
    sender.setAddressChunkSize(3);
    return sender;
  }

  private CountingSenderStub countingSenderWithRateLimit(Duration timeout) {
    CountingSenderStub sender = countingSender();
    sender.setRateLimiter(
        RateLimiter.of(
            "emailLimiter",
            RateLimiterConfig.custom()
                .limitForPeriod(2)
                .limitRefreshPeriod(Duration.ofMillis(100))
                .timeoutDuration(timeout)
                .build()));
    return sender;
  }

  private List<String> addresses(int count) {
    return IntStream.range(0, count).mapToObj(i -> "user" + i + "@example.com").toList();
  }

  private EmailContent anyHtmlBody() {
    return new EmailContent("subject", "<html><body>body</body></html>", "body");
  }
}
