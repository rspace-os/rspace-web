package com.researchspace.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Sets user timezone in the session, if the request has correct attribute. Should degrade
 * gracefully if the user timezone information is not in the request.
 */
public interface TimezoneAdjuster {

  void setUserTimezoneInSession(HttpServletRequest reqx, HttpSession sssn);

  void setUserTimezoneInSession(String timezone, HttpSession session);
}
