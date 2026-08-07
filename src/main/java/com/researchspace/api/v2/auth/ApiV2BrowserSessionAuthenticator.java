package com.researchspace.api.v2.auth;

import com.researchspace.model.User;
import com.researchspace.service.UserManager;
import com.researchspace.session.SessionAttributeUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.session.Session;
import org.apache.shiro.subject.Subject;
import org.springframework.stereotype.Service;

/** Resolves the user from an existing authenticated browser session. */
@Service
@RequiredArgsConstructor
public class ApiV2BrowserSessionAuthenticator {

  private final UserManager userManager;

  public Optional<User> authenticateIfPresent(HttpServletRequest request) {
    if (request.getSession(false) == null) {
      return Optional.empty();
    }
    Subject subject = SecurityUtils.getSubject();
    if (!subject.isAuthenticated()) {
      return Optional.empty();
    }
    Session session = subject.getSession(false);
    if (session == null) {
      return Optional.empty();
    }
    Object user = session.getAttribute(SessionAttributeUtils.USER);
    if (user instanceof User authenticatedUser) {
      return Optional.of(authenticatedUser);
    }
    Object principal = subject.getPrincipal();
    return principal instanceof String username
        ? Optional.ofNullable(userManager.getUserByUsername(username))
        : Optional.empty();
  }
}
