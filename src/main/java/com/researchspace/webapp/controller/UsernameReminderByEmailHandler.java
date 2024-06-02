package com.researchspace.webapp.controller;

import com.researchspace.core.util.RequestUtil;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.model.User;
import com.researchspace.model.permissions.SecurityLogger;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.EmailBroadcast;
import com.researchspace.service.UserManager;
import com.researchspace.service.impl.EmailBroadcastImp.EmailContent;
import com.researchspace.service.impl.StrictEmailContentGenerator;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.apache.velocity.app.VelocityEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class UsernameReminderByEmailHandler {
  protected Logger SECURITY_LOG = LoggerFactory.getLogger(SecurityLogger.class);
  private static final String EMAIL_SUBJECT = "RSpace username reminder";
  static final int MAX_REMINDERS_PER_EMAIL_PER_HOUR = 5;

  @Autowired IPropertyHolder properties;
  @Autowired UserManager userManager;
  @Autowired VelocityEngine velocity;

  @Autowired
  @Qualifier("emailBroadcast")
  EmailBroadcast emailer;

  Map<String, RateLimiter> perMinute = new ConcurrentHashMap<String, RateLimiter>();
  private @Autowired StrictEmailContentGenerator strictEmailContentGenerator;

  void sendUsernameReminderEmail(HttpServletRequest request, String email) {
    String remoteIpAddress = RequestUtil.remoteAddr(request);

    if (StringUtils.isBlank(email)) {
      throw new IllegalArgumentException("email cannot be empty!");
    }

    List<User> users = userManager.getUserByEmail(email);
    if (users == null || users.isEmpty()) {
      SECURITY_LOG.warn(
          "Username reminder request from {} for a non-existing email {}", remoteIpAddress, email);
      return;
    }

    RateLimiter rlForEmail = perMinute.computeIfAbsent(email, k -> createRateLimiter(email));
    try {
      rlForEmail.executeRunnable(
          () -> {
            for (User user : users) {
              Map<String, Object> velocityModel = new HashMap<>();
              velocityModel.put("ipAddress", remoteIpAddress);
              velocityModel.put("username", user.getUsername());
              velocityModel.put("loginLink", properties.getServerUrl() + "/login");

              EmailContent emailContent =
                  strictEmailContentGenerator.generatePlainTextAndHtmlContent(
                      "usernameReminderMessage.vm", velocityModel);
              emailer.sendHtmlEmail(
                  EMAIL_SUBJECT, emailContent, TransformerUtils.toList(email), null);
            }
          });
    } catch (RequestNotPermitted e) {
      throw new IllegalStateException(
          "You have exceeded the number of username reminder requests. Please contact ResearchSpace"
              + " support for assistance");
    }

    SECURITY_LOG.info("Username reminder request from {} sent to email {}", remoteIpAddress, email);
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
            e -> SECURITY_LOG.info("Username reminder request blocked from email {}", email));

    return rld;
  }
}
