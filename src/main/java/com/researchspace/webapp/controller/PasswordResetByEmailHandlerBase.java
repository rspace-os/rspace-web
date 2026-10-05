package com.researchspace.webapp.controller;

import static com.researchspace.webapp.controller.UsernameReminderByEmailHandler.MAX_REMINDERS_PER_EMAIL_PER_HOUR;

import com.researchspace.core.util.RequestUtil;
import com.researchspace.model.TokenBasedVerification;
import com.researchspace.model.TokenBasedVerificationType;
import com.researchspace.model.dtos.UserValidator;
import com.researchspace.model.permissions.SecurityLogger;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.EmailBroadcast;
import com.researchspace.service.EmailContent;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.UserManager;
import com.researchspace.service.impl.EmailContentGenerator;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.AccessLevel;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

/** Base class for password/verification-password reset by email */
public abstract class PasswordResetByEmailHandlerBase {
  protected static final Logger SECURITY_LOG = LoggerFactory.getLogger(SecurityLogger.class);
  private static final Duration RESET_RATE_LIMIT_PERIOD = Duration.ofSeconds(5);

  /**
   * Reset replies accepted per handler per five seconds. Each one hashes the new password with
   * Argon2id (about 19 MiB of heap) outside the login verifier's permits, and one valid token can
   * be posted concurrently until the first reply marks it used, so this caps what an anonymous
   * burst can allocate (ADR 0011). Over the limit, submissions are refused at once; nothing waits
   * and the token stays usable.
   */
  @Value("${user.passwordReset.maxPerFiveSeconds:10}")
  @Setter(AccessLevel.PACKAGE) // for testing
  private int maxResetsPerFiveSeconds;

  private RateLimiter resetRateLimiter;

  @Autowired UserManager userManager;
  @Autowired IPropertyHolder properties;

  @Autowired
  @Qualifier("emailBroadcast")
  EmailBroadcast emailer;

  @Autowired UserValidator userValidator;
  @Autowired MessageSourceUtils messages;
  private @Autowired EmailContentGenerator emailContentGenerator;
  Map<String, RateLimiter> resetsPerMinutePerUser = new ConcurrentHashMap<String, RateLimiter>();

  @PostConstruct
  void initResetRateLimiter() {
    resetRateLimiter =
        RateLimiter.of(
            "passwordReset",
            RateLimiterConfig.custom()
                .limitForPeriod(maxResetsPerFiveSeconds)
                .limitRefreshPeriod(RESET_RATE_LIMIT_PERIOD)
                .timeoutDuration(Duration.ZERO)
                .build());
  }

  /** Generates a reset token and sends an email with the token if the given email address exists */
  protected void sendChangeCredentialsEmail(HttpServletRequest request, String email) {
    String remoteIpAddress = RequestUtil.remoteAddr(request);

    TokenBasedVerification upc =
        userManager.createTokenBasedVerificationRequest(
            null, email, remoteIpAddress, TokenBasedVerificationType.PASSWORD_CHANGE);

    // upc = null when the email address is not registered with RSpace
    if (upc != null) {
      RateLimiter rlForEmail =
          resetsPerMinutePerUser.computeIfAbsent(email, k -> createRateLimiter(email));
      try {
        rlForEmail.executeRunnable(
            () -> {
              Map<String, Object> velocityModel = new HashMap<>();
              velocityModel.put("resetLink", getResetLink(upc.getToken()));
              velocityModel.put("ipAddress", remoteIpAddress);
              velocityModel.put("passwordType", getPasswordType());

              EmailContent emailContent =
                  emailContentGenerator.render(
                      getEmailSubjectKey(), "passwordResetMessage.vm", velocityModel);
              emailer.sendEmail(emailContent, List.of(email), null);

              SECURITY_LOG.info(
                  "Password reset request from [{}] sent to email [{}]", remoteIpAddress, email);
            });
      } catch (RequestNotPermitted e) {
        throw new IllegalStateException(
            messages.getMessage(
                "errors.rateLimitExceeded",
                new Object[] {messages.getMessage("requestType.passwordReminder")}));
      }
    } else {
      SECURITY_LOG.warn(
          "Password reset request for a non-existing email [{}], from {}", email, remoteIpAddress);
    }
  }

  // 5 per hour
  private RateLimiter createRateLimiter(String email) {
    RateLimiterConfig cfg =
        RateLimiterConfig.custom()
            .limitForPeriod(MAX_REMINDERS_PER_EMAIL_PER_HOUR)
            .limitRefreshPeriod(Duration.of(1, ChronoUnit.HOURS))
            .timeoutDuration(Duration.of(1, ChronoUnit.SECONDS))
            .build();
    RateLimiter rld = RateLimiter.of(email, cfg);
    rld.getEventPublisher()
        .onFailure(
            e -> SECURITY_LOG.info("Password reminder request blocked from email [{}]", email));
    return rld;
  }

  /** Given a reset token, returns the correct view to set the password */
  protected ModelAndView getResetPage(@RequestParam("token") String token) {
    TokenBasedVerification change = userManager.getUserVerificationToken(token);
    if (change != null && change.isValidLink(token, TokenBasedVerificationType.PASSWORD_CHANGE)) {
      ModelAndView mav = new ModelAndView("passwordReset/resetPassword");
      PasswordResetCommand command = new PasswordResetCommand();
      command.setToken(change.getToken());
      mav.addObject("passwordResetCommand", command);
      return mav;
    } else {
      return new ModelAndView("passwordReset/resetPasswordFail");
    }
  }

  protected ModelAndView submitResetPage(
      PasswordResetCommand cmd, BindingResult errors, HttpServletRequest request) throws Exception {

    TokenBasedVerification change = userManager.getUserVerificationToken(cmd.getToken());
    if (change == null
        || !change.isValidLink(cmd.getToken(), TokenBasedVerificationType.PASSWORD_CHANGE)) {
      SECURITY_LOG.warn(
          "Reset password attempt with a used, expired or unknown token, from {}",
          RequestUtil.remoteAddr(request));
      return new ModelAndView("passwordReset/resetPasswordFail");
    }
    Optional<String> usernameOpt = userManager.getUsernameByToken(cmd.getToken());
    if (usernameOpt.isEmpty()) {
      SECURITY_LOG.warn(
          "Invalid reset password attempt, with token, from {}", RequestUtil.remoteAddr(request));
      return new ModelAndView("passwordReset/resetPasswordFail");
    }
    String username = usernameOpt.get();
    userValidator.validatePasswords(cmd.getPassword(), cmd.getConfirmPassword(), username, errors);
    if (errors.hasErrors()) {
      return new ModelAndView("passwordReset/resetPassword");
    }
    if (!resetRateLimiter.acquirePermission()) {
      SECURITY_LOG.warn(
          "Reset of {} for [{}] from {} refused: more than {} resets in {} seconds",
          getPasswordType(),
          username,
          RequestUtil.remoteAddr(request),
          maxResetsPerFiveSeconds,
          RESET_RATE_LIMIT_PERIOD.toSeconds());
      errors.reject("errors.passwordReset.rateLimited");
      return new ModelAndView("passwordReset/resetPassword");
    }
    // update pwd, set as closed
    TokenBasedVerification upc = applyPasswordChange(cmd);
    if (upc != null) {
      sendPasswordChangeCompleteEmail(upc);
      SECURITY_LOG.info(
          "Completed password reset for user with email [{}] from IP address [{}]",
          upc.getEmail(),
          upc.getIpAddressOfRequestor());
      return new ModelAndView("passwordReset/resetPasswordComplete");
    } else {
      throw new Exception("Could not reset " + getPasswordType());
    }
  }

  protected void sendPasswordChangeCompleteEmail(TokenBasedVerification upc) {
    Map<String, Object> velocityModel = new HashMap<>();
    velocityModel.put("passwordType", getPasswordType());

    EmailContent emailContent =
        emailContentGenerator.render(
            getCompletionEmailSubjectKey(), "passwordResetComplete.vm", velocityModel);
    emailer.sendEmail(emailContent, List.of(upc.getEmail()), null);
  }

  protected String getResetLink(String token) {
    return String.format(getResetLinkFormat(), properties.getServerUrl(), token);
  }

  abstract TokenBasedVerification applyPasswordChange(PasswordResetCommand cmd);

  abstract String getPasswordType();

  abstract String getResetLinkFormat();

  abstract String getCompletionEmailSubjectKey();

  abstract String getEmailSubjectKey();
}
