package com.researchspace.webapp.controller;

import com.researchspace.auth.TimezoneAdjuster;
import com.researchspace.model.User;
import com.researchspace.session.SessionAttributeUtils;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/session")
public class SessionController extends BaseController {
  private @Autowired TimezoneAdjuster timeZone;

  @PostMapping("/ajax/timezone")
  @ResponseBody
  public void setTimezone(
      HttpServletRequest request,
      HttpSession session,
      @CookieValue(name = SessionAttributeUtils.TIMEZONE, required = false) String timezone) {

    if (!StringUtils.isBlank(timezone)) {
      timeZone.setUserTimezoneInSession(timezone, session);
    } else {
      timeZone.setUserTimezoneInSession(request, session);
    }
  }

  @GetMapping("/ajax/fullNameAndEmail")
  @ResponseBody
  public AjaxReturnObject<Map<String, String>> getFullNameAndEmail() {
    User user = userManager.getAuthenticatedUserInSession();

    Map<String, String> response = new HashMap<>();
    response.put("fullName", user.getFullName());
    response.put("email", user.getEmail());

    return new AjaxReturnObject<>(response, null);
  }

  @Value("${posthog.client.id}")
  private String posthogClientId;

  @Value("${posthog.server.url}")
  private String posthogServerUrl;

  @GetMapping("/ajax/analyticsProperties")
  @ResponseBody
  public Map<String, Object> getAnalyticsProps(HttpSession session) {

    User user = userManager.getAuthenticatedUserInSession();
    Map<String, Object> result = new HashMap<>();

    Boolean analyticsEnabled = Boolean.valueOf(properties.getAnalyticsEnabled());
    result.put("analyticsEnabled", analyticsEnabled);
    result.put("currentUser", user.getUsername());

    if (analyticsEnabled) {
      result.put("analyticsUserId", session.getAttribute("analyticsUserId"));
      result.put(
          "analyticsServerKey", session.getServletContext().getAttribute("analyticsServerKey"));
    }
    if (StringUtils.isNotEmpty(posthogServerUrl) && StringUtils.isNotEmpty(posthogClientId)) {
      result.put("posthogClientId", posthogClientId);
      result.put("posthogServerUrl", posthogServerUrl);
    }
    return result;
  }
}
