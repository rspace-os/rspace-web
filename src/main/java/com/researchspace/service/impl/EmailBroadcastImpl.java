package com.researchspace.service.impl;

import com.researchspace.model.comms.Communication;
import com.researchspace.model.comms.MessageRecipientFactory;
import com.researchspace.service.EmailBroadcast;
import com.researchspace.service.EmailContent;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.vavr.CheckedRunnable;
import io.vavr.control.Try;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import javax.annotation.PostConstruct;
import javax.mail.Address;
import javax.mail.AuthenticationFailedException;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.SendFailedException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MailDateFormat;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import org.apache.commons.lang3.RandomStringUtils;
import org.jsoup.helper.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

/**
 * This is the main class for sending emails. It uses rate-limiting and retry mechanism, which work
 * as follows:
 *
 * <ul>
 *   <li>The rate limiter is configured globally to throttle email requests to <code>
 *       maxEmailsPerSecond</code>. The default is 5. The timeout wait is 5 seconds, after which the
 *       email send will abort with an exception.
 *   <li>For each individual email sent, there is a retry mechanism, with exponential back-off to
 *       retry mails in the event of an email server exception. This will retry a maximum of 3
 *       times.
 *   <li>If there are too many recipients on an email, some email providers reject the mail (e.g.
 *       AWS limit is 50 recipients per email). Address lists are partitioned into groups of 25 to
 *       avoid these errors and the message sent in separate messages.
 */
public class EmailBroadcastImpl implements EmailBroadcast {

  private int retryDelayMillis = 1000;

  void setRetryDelayMillis(int retryDelayMillis) {
    this.retryDelayMillis = retryDelayMillis;
  }

  RetryConfig retryConfig;

  RateLimiter rateLimiter;

  void setRateLimiter(RateLimiter rateLimiter) {
    this.rateLimiter = rateLimiter;
  }

  RateLimiter buildRateLimiter(Integer maxEmailsPerSecond) {
    RateLimiterConfig cfg =
        RateLimiterConfig.custom()
            .limitForPeriod(maxEmailsPerSecond)
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .timeoutDuration(Duration.ofSeconds(5))
            .build();
    return RateLimiter.of("emailLimiter", cfg);
  }

  RetryConfig buildRetryConfig() {
    IntervalFunction intervalWithCustomExponentialBackoff =
        IntervalFunction.ofExponentialBackoff(retryDelayMillis, 2d);
    return RetryConfig.custom()
        .maxAttempts(3)
        .intervalFunction(intervalWithCustomExponentialBackoff)
        .retryExceptions(SendFailedException.class, MessagingException.class)
        .ignoreExceptions(AuthenticationFailedException.class) // will never succeed
        .build();
  }

  record EmailConfig(List<String> addresses, EmailContent content, Communication comm) {
    EmailConfig {
      addresses =
          addresses.stream()
              .filter(address -> !address.endsWith(EmailBroadcast.UNKNOWN_EMAIL_SUFFIX))
              .toList();
    }
  }

  private final Integer maxEmailsPerSecond;

  public Integer getMaxSendingRate() {
    return maxEmailsPerSecond;
  }

  // these constants are relied upon by external scripts that examine the log
  // file,
  // so don't change these values without consideration - see RSOPS-19/RSPAC-785
  public static final String AUTHENTICATION_FAILURE_PREFIX = "AUTHENTICATION";
  public static final String SEND_FAILURE_PREFIX = "SEND_FAILURE";
  public static final String GENERAL_FAILURE_PREFIX = "FAILURE";

  private final Logger log = LoggerFactory.getLogger(getClass());
  private Logger emailErrorLog = LoggerFactory.getLogger(FailedEmailLogger.class);

  void setErrorLog(Logger errorLog) {
    this.emailErrorLog = errorLog;
  }

  private final MessageRecipientFactory msgRecipientFac = new MessageRecipientFactory();

  /**
   * These are detected by Spring via PropertyPlaceholderConfigurer and injected in. They're
   * injected here rather than in MailUtils so as to keep MailUtils free of Spring configuration.
   */
  @Value("${mail.emailAccount}")
  private String emailAccount;

  @Value("${mail.password}")
  private String password;

  @Value("${mail.port}")
  private String port;

  @Value("${mail.emailHost}")
  private String emailHost;

  @Value("${mail.from}")
  private String from;

  @Value("${mail.replyTo}")
  private String replyTo;

  @Value("${mail.ssl.enabled}")
  private String sslEnabled;

  @Value("${mail.debug}")
  private String mailDebug;

  private Integer addressChunkSize;

  /**
   * @param addressChunkSize > 0
   * @throws IllegalArgumentException if <=0
   */
  public void setAddressChunkSize(Integer addressChunkSize) {
    Validate.isTrue(addressChunkSize != null && addressChunkSize > 0, "Chunk size must be > 0");
    this.addressChunkSize = addressChunkSize;
  }

  public EmailBroadcastImpl(Integer maxEmailsPerSecond, Integer addressChunkSize) {
    Validate.isTrue(maxEmailsPerSecond != null && maxEmailsPerSecond > 0, "Rate must be > 0");
    this.maxEmailsPerSecond = maxEmailsPerSecond;
    setAddressChunkSize(addressChunkSize);
    log.info("Email sender will rate limit to {} mails per seconds", maxEmailsPerSecond);
  }

  @PostConstruct
  public void init() {
    retryConfig = buildRetryConfig();
    rateLimiter = buildRateLimiter(maxEmailsPerSecond);
  }

  @Override
  public void sendEmail(EmailContent content, List<String> recipients, Communication comm) {
    List<String> addresses = new EmailConfig(recipients, content, comm).addresses();
    for (int start = 0; start < addresses.size(); start += addressChunkSize) {
      List<String> batch =
          addresses.subList(start, Math.min(start + addressChunkSize, addresses.size()));
      rateLimiter.executeRunnable(() -> sendAndLog(new EmailConfig(batch, content, comm)));
    }
  }

  private void sendAndLog(EmailConfig config) {
    String id = RandomStringUtils.randomAlphabetic(10);
    logEmailSendStart(config.content().htmlContent(), id);
    Retry retry = Retry.of("email", retryConfig);
    CheckedRunnable internal = createEmailSenderInternal(config, id);
    CheckedRunnable decorated = Retry.decorateCheckedRunnable(retry, internal);
    Try.run(decorated)
        .onFailure(t -> emailErrorLog.error("Unexpected email error: {}", t.getMessage()));
  }

  CheckedRunnable createEmailSenderInternal(EmailConfig config, String id) {
    return new EmailSenderInternal(id, config);
  }

  // this will be called by retry mechanism.
  @lombok.Value
  class EmailSenderInternal implements CheckedRunnable {
    String id;
    EmailConfig config;

    @Override
    public void run() throws Throwable {
      try {
        sendMailToAddresses(config);
        log.info("Message [{}] sent", id);
      } catch (AuthenticationFailedException e) {
        emailErrorLog.error(
            "{}:{} -  {}. Not retrying", AUTHENTICATION_FAILURE_PREFIX, id, e.getMessage());
        throw e;
      } catch (SendFailedException e1) {
        emailErrorLog.warn("{}:{} - {}. Retrying", SEND_FAILURE_PREFIX, id, e1.getMessage());
        throw e1;
      } catch (MessagingException e2) {
        emailErrorLog.warn("{}:{} - {}", GENERAL_FAILURE_PREFIX, id, e2.getMessage());
        throw e2;
      }
    }
  }

  private void logEmailSendStart(String txt, String id) {
    log.debug("Sending email [{}] with text :{}", id, txt);
  }

  /*
   * package scoped for overriding in tests
   */
  void sendMailToAddresses(EmailConfig emailConfig) throws MessagingException {
    Properties props = readEmailProperties();
    Session session = createSession(props);

    MimeMessage message = new MimeMessage(session);
    try (Transport transport = configureMailTransport(session)) {
      setMessageContent(emailConfig, message);
      transport.sendMessage(message, message.getAllRecipients());
    }
  }

  private Transport configureMailTransport(Session session) throws MessagingException {
    Transport transport = session.getTransport("smtp");
    transport.connect(emailHost, Integer.parseInt(port), emailAccount, password);
    return transport;
  }

  private Session createSession(Properties props) {
    Session session = Session.getInstance(props, null);
    session.setDebug(Boolean.parseBoolean(mailDebug));
    return session;
  }

  private void setMessageContent(EmailConfig emailConfig, MimeMessage message)
      throws MessagingException {

    setDateHeader(message, Instant.now());
    message.setSubject(emailConfig.content().subject());
    message.setFrom(new InternetAddress(from));
    msgRecipientFac.populateRecipients(emailConfig.addresses(), message, emailConfig.comm());
    message.setReplyTo(new Address[] {new InternetAddress(replyTo)});
    message.setContent(generateMultipartContent(emailConfig));
  }

  // package-scoped for testing
  Multipart generateMultipartContent(EmailConfig emailConfig) throws MessagingException {
    Multipart multiPart = new MimeMultipart("alternative");
    MimeBodyPart textPart = new MimeBodyPart();
    textPart.setText(emailConfig.content().plainTextContent(), "utf-8");

    MimeBodyPart htmlPart = new MimeBodyPart();

    htmlPart.setContent(emailConfig.content().htmlContent(), "text/html; charset=UTF-8");
    htmlPart.setHeader("Content-Type", "text/html; charset=UTF-8");
    // ordering is important to set priority that email client users
    multiPart.addBodyPart(textPart);
    multiPart.addBodyPart(htmlPart);
    return multiPart;
  }

  private Properties readEmailProperties() {
    Properties props = new Properties(System.getProperties());
    props.put("mail.smtp.user", emailAccount);
    props.put("mail.smtp.password", password);
    props.put("mail.smtp.host", emailHost);
    props.put("mail.smtp.port", port);
    // props.put("mail.debug", "true");
    props.put("mail.smtp.auth", "true");

    log.info("ssl enabled?: {}", sslEnabled);
    if (Boolean.parseBoolean(sslEnabled)) {
      props.put("mail.smtp.starttls.enable", "true");
    }
    return props;
  }

  void setDateHeader(MimeMessage message, Instant time) throws MessagingException {
    String dateHeader = new MailDateFormat().format(new Date(time.toEpochMilli()));
    message.addHeader("Date", dateHeader);
  }
}
