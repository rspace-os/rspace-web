package com.researchspace.service.impl;

import com.researchspace.model.User;
import com.researchspace.model.comms.Communication;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageRecipientFactory;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.preference.Preference;
import com.researchspace.service.Broadcaster;
import com.researchspace.service.EmailBroadcast;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.vavr.CheckedRunnable;
import io.vavr.control.Try;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.mail.Address;
import javax.mail.AuthenticationFailedException;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.NoSuchProviderException;
import javax.mail.SendFailedException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MailDateFormat;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.velocity.tools.generic.DateTool;
import org.jsoup.helper.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
 *       <p><b> Implementation notes </b>
 *       <p>TODO this class has too many responsibilities; needs refactoring into separate classes
 */
public class EmailBroadcastImp implements EmailBroadcast, Broadcaster {

  public static final String TEXT_ONLY_EMAIL_DEFAULT = "This email can only be viewed in HTML";

  private @Autowired StrictEmailContentGenerator strictEmailContentGenerator;

  void setStrictEmailContentGenerator(StrictEmailContentGenerator strictEmailContentGenerator) {
    this.strictEmailContentGenerator = strictEmailContentGenerator;
  }

  RetryConfig retryConfig = null;

  RateLimiter rateLimiter = null;

  void setRateLimiter(RateLimiter rateLimiter) {
    this.rateLimiter = rateLimiter;
  }

  RateLimiter buildRateLimiter(Integer maxEmailsPerSecond) {
    RateLimiterConfig cfg =
        RateLimiterConfig.custom()
            .limitForPeriod(maxEmailsPerSecond)
            .limitRefreshPeriod(Duration.of(1, ChronoUnit.SECONDS))
            .timeoutDuration(Duration.of(5, ChronoUnit.SECONDS))
            .build();
    return RateLimiter.of("emailLimiter", cfg);
  }

  RetryConfig buildRetryConfig() {
    IntervalFunction intervalWithCustomExponentialBackoff =
        IntervalFunction.ofExponentialBackoff(1000, 2d);
    return RetryConfig.custom()
        .maxAttempts(3)
        .intervalFunction(intervalWithCustomExponentialBackoff)
        .retryExceptions(SendFailedException.class, MessagingException.class)
        .ignoreExceptions(AuthenticationFailedException.class) // will never succeed
        .build();
  }

  /** Encapsulates HTML and possibly-null plain-text variant. */
  @AllArgsConstructor
  @Builder
  public static class EmailContent {
    @Getter private final String htmlContent;

    private final String plainTextContent;

    public Optional<String> getPlainTextContent() {
      return Optional.ofNullable(plainTextContent);
    }
  }

  @lombok.Value
  @AllArgsConstructor
  public static class EmailConfig {
    private List<String> addresses;
    private String subject;
    private EmailContent content;
    private Communication comm;
    private boolean isStrictValidEmail;
  }

  private Integer maxEmailsPerSecond = 5;

  public Integer getMaxSendingRate() {
    return maxEmailsPerSecond;
  }

  void setMaxSendingRate(Integer maxEmailsPerSecond) {
    this.maxEmailsPerSecond = maxEmailsPerSecond;
  }

  // these constants are relied upon by external scripts that examine the log
  // file,
  // so don't change these values without consideration - see RSOPS-19/RSPAC-785
  public static final String AUTHENTICATION_FAILURE_PREFIX = "AUTHENTICATION";
  public static final String SEND_FAILURE_PREFIX = "SEND_FAILURE";
  public static final String GENERAL_FAILURE_PREFIX = "FAILURE";

  private Logger log = LoggerFactory.getLogger(getClass());
  private Logger emailErrorLog = LoggerFactory.getLogger(FailedEmailLogger.class);

  void setErrorLog(Logger errorLog) {
    this.emailErrorLog = errorLog;
  }

  private MessageRecipientFactory msgRecipientFac = new MessageRecipientFactory();

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

  @Value("${server.urls.prefix}")
  private String htmlDomainPrefix;

  @Value("${mail.ssl.enabled}")
  private String sslEnabled;

  @Value("${mail.debug}")
  private String mailDebug;

  private Integer addressChunkSize = 25;

  /**
   * @param addressChunkSize > 0
   * @throws IllegalArgumentException if <=0
   */
  public void setAddressChunkSize(Integer addressChunkSize) {
    Validate.isTrue(addressChunkSize > 0, "Chunk size must be > 0");
    this.addressChunkSize = addressChunkSize;
  }

  public EmailBroadcastImp() {
    this(5, 25);
  }

  public EmailBroadcastImp(Integer maxEmailsPerSecond, Integer addressChunkSize) {
    this.maxEmailsPerSecond = maxEmailsPerSecond;
    this.addressChunkSize = addressChunkSize;
    log.info("Email sender will rate limit to {} mails per seconds", maxEmailsPerSecond);
  }

  @PostConstruct
  public void init() {
    retryConfig = buildRetryConfig();
    rateLimiter = buildRateLimiter(maxEmailsPerSecond);
  }

  public void setHtmlDomainPrefix(String htmlDomainPrefix) {
    this.htmlDomainPrefix = htmlDomainPrefix;
  }

  @Override
  public void broadcast(Communication comm) {

    List<String> addrs = new ArrayList<>();
    Set<CommunicationTarget> recpts = comm.getRecipients();
    addRecipients(comm, addrs, recpts);
    // no point continuing if nobody wants emails
    if (addrs.isEmpty()) {
      return;
    }
    String subj = comm.getSubject();
    if (subj == null || subj.length() < 1) {
      subj = "email: ";
    }
    EmailContent body = generateEmailBody(comm);
    // we split into separate emails of 25 recipients each to avoid too many
    // recipients issues (RSPAC-2156):
    Collection<List<String>> addressPartitions = partitionAddressesBySize(addrs, addressChunkSize);
    for (List<String> addressPartition : addressPartitions) {
      PartitionedEmailSender sender =
          new PartitionedEmailSender(comm, subj, body, addressPartition);
      rateLimiter.executeRunnable(sender);
    }
  }

  @lombok.Value
  class PartitionedEmailSender implements Runnable {
    Communication comm;
    String subj;
    EmailContent body;
    List<String> addressPartition;

    public void run() {
      sendHtmlEmail(subj, body, addressPartition, comm);
    }
  }

  private Collection<List<String>> partitionAddressesBySize(List<String> addrs, int chunkSize) {
    final AtomicInteger counter = new AtomicInteger();
    Collection<List<String>> addressPartitions =
        addrs.stream()
            .collect(Collectors.groupingBy(it -> counter.getAndIncrement() / chunkSize))
            .values();
    return addressPartitions;
  }

  @Override
  public void sendTextEmail(String subj, String txt, List<String> recipients, Communication comm) {
    String html = "<html><body>" + txt + "</body></html>";
    sendAndLog(
        new EmailConfig(
            recipients, subj, EmailContent.builder().htmlContent(html).build(), comm, false));
  }

  @Override
  public void sendHtmlEmail(
      String subj, EmailContent content, List<String> recipients, Communication comm) {
    sendAndLog(new EmailConfig(recipients, subj, content, comm, true));
  }

  private void sendAndLog(EmailConfig config) {
    String id = RandomStringUtils.randomAlphabetic(10);
    logEmailSendStart(config.getContent().getHtmlContent(), id);
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
        log.info("Message [" + id + "] sent");
      } catch (AuthenticationFailedException e) {
        emailErrorLog.error(
            "{}:{} -  {}. Not retrying", AUTHENTICATION_FAILURE_PREFIX, id, e.getMessage());
        throw e;
      } catch (SendFailedException e1) {
        emailErrorLog.warn("{}:{} - {}. Retrying}", SEND_FAILURE_PREFIX, id, e1.getMessage());
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

  // package-scoped for testing
  void addRecipients(Communication comm, List<String> addrs, Set<CommunicationTarget> recpts) {

    for (CommunicationTarget tg : recpts) {

      User recipient = tg.getRecipient();
      // don't send emails to users with disabled accounts
      // rspac-2142
      if (!recipient.isEnabled()) {
        continue;
      }
      if (comm.isNotification()
          && recipient
              .getValueForPreference(Preference.BROADCAST_NOTIFICATIONS_BY_EMAIL)
              .getValueAsBoolean()) {
        addrs.add(recipient.getEmail());
        comm.setSubject("Notification");
      } else if (comm.isMessageOrRequest()
          && recipient
              .getValueForPreference(Preference.BROADCAST_REQUEST_BY_EMAIL)
              .getValueAsBoolean()) {
        addrs.add(recipient.getEmail());
        comm.setSubject("Request");
      }
    }
  }

  @Override
  public EmailContent generateEmailBody(Communication comm) {
    EmailContent body = null;
    String templateName = null;

    if (comm.isNotification()) {
      templateName = "notification.vm";
    } else if (comm.isMessageOrRequest()) {
      MessageType messageType = ((MessageOrRequest) comm).getMessageType();
      if (MessageType.SIMPLE_MESSAGE.equals(messageType)) {
        templateName = "message.vm";
      } else {
        templateName = "request.vm";
      }
    }

    if (templateName != null) {
      body =
          strictEmailContentGenerator.generatePlainTextAndHtmlContent(
              templateName, getVelocityData(comm));
    }
    return body;
  }

  private Map<String, Object> getVelocityData(Communication comm) {
    Map<String, Object> cfg = new HashMap<>();
    cfg.put("cmm", comm);
    cfg.put("baseURL", htmlDomainPrefix);
    cfg.put("dateOb", new Date());
    cfg.put("date", new DateTool());
    return cfg;
  }

  /*
   * package scoped for overriding in tests
   */
  void sendMailToAddresses(EmailConfig emailConfig) throws MessagingException {
    // don't sent emails to unknown addresses
    emailConfig.getAddresses().removeIf(a -> a.endsWith(EmailBroadcast.UNKNOWN_EMAIL_SUFFIX));
    if (emailConfig.getAddresses().isEmpty()) {
      return;
    }
    Properties props = readEmailProperties();
    Session session = createSession(props);

    MimeMessage message = new MimeMessage(session);
    try (Transport transport = configureMailTransport(session)) {
      setMessageContent(emailConfig, message);
      transport.sendMessage(message, message.getAllRecipients());
    }
  }

  private Transport configureMailTransport(Session session)
      throws NoSuchProviderException, MessagingException {
    Transport transport;
    transport = session.getTransport("smtp");
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
    message.setSubject(emailConfig.getSubject());
    message.setFrom(new InternetAddress(from));
    msgRecipientFac.populateRecipients(emailConfig.getAddresses(), message, emailConfig.getComm());
    message.setReplyTo(new Address[] {new InternetAddress(replyTo)});
    if (!emailConfig.isStrictValidEmail()) {
      message.setContent(emailConfig.getContent().getHtmlContent(), "text/html; charset=utf8");
    } else {
      // it's strict email, minimise spamminess by including plain-text alternative.
      Multipart multiPart = generateMultipartContent(emailConfig);
      message.setContent(multiPart);
    }
  }

  // package-scoped for testing
  Multipart generateMultipartContent(EmailConfig emailConfig) throws MessagingException {
    Multipart multiPart = new MimeMultipart("alternative");
    MimeBodyPart textPart = new MimeBodyPart();
    textPart.setText(
        emailConfig.getContent().getPlainTextContent().orElse(TEXT_ONLY_EMAIL_DEFAULT), "utf-8");

    MimeBodyPart htmlPart = new MimeBodyPart();

    htmlPart.setContent(emailConfig.getContent().getHtmlContent(), "text/html");
    htmlPart.setHeader("Content-Type", "text/html");
    // ordering is important to set priority that email client users
    multiPart.addBodyPart(textPart);
    multiPart.addBodyPart(htmlPart);
    return multiPart;
  }

  private Properties readEmailProperties() {
    Properties props = System.getProperties();
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
