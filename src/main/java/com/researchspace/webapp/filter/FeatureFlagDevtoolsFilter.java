package com.researchspace.webapp.filter;

import com.researchspace.model.User;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.UserManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Supplies devtools eligibility to legacy JSP entry points after Shiro resolves the subject. */
@Component("featureFlagDevtoolsFilter")
public class FeatureFlagDevtoolsFilter extends OncePerRequestFilter {
  private final UserManager users;
  private final FeatureFlagManager featureFlags;

  public FeatureFlagDevtoolsFilter(UserManager users, FeatureFlagManager featureFlags) {
    this.users = users;
    this.featureFlags = featureFlags;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Subject subject = SecurityUtils.getSubject();
    boolean allowed = false;
    if (subject != null && subject.isAuthenticated()) {
      User user = users.getUserByUsername((String) subject.getPrincipal());
      allowed =
          user != null && !user.isAnonymousGuestAccount() && featureFlags.canUseDevtools(user);
    }
    request.setAttribute("canUseDevtools", allowed);
    chain.doFilter(request, response);
  }
}
