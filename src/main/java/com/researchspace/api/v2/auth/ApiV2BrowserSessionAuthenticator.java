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

/** Resolves the effective subject and original actor from an authenticated browser session. */
@Service
@RequiredArgsConstructor
public class ApiV2BrowserSessionAuthenticator {

  private final UserManager userManager;

  public Optional<ApiV2Caller> authenticateIfPresent(HttpServletRequest request) {
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
    boolean sessionSaysRunAs =
        Boolean.TRUE.equals(session.getAttribute(SessionAttributeUtils.IS_RUN_AS));
    if (sessionSaysRunAs != subject.isRunAs()) {
      throw new ApiV2AuthenticationException();
    }

    Object principal = subject.getPrincipal();
    if (!(principal instanceof String username)) {
      if (sessionSaysRunAs) {
        throw new ApiV2AuthenticationException();
      }
      return Optional.empty();
    }
    User authenticatedUser = userManager.getUserByUsername(username, true);
    if (authenticatedUser == null) {
      if (sessionSaysRunAs) {
        throw new ApiV2AuthenticationException();
      }
      return Optional.empty();
    }

    if (!sessionSaysRunAs) {
      return Optional.of(ApiV2Caller.direct(authenticatedUser));
    }

    Object previousPrincipal = subject.getPreviousPrincipals().getPrimaryPrincipal();
    if (!(previousPrincipal instanceof String actorUsername)) {
      throw new ApiV2AuthenticationException();
    }
    User actor = userManager.getUserByUsername(actorUsername, true);
    if (actor == null) {
      throw new ApiV2AuthenticationException();
    }
    return Optional.of(new ApiV2Caller(authenticatedUser, actor));
  }
}
