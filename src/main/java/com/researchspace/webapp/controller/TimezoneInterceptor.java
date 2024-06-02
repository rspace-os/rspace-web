package com.researchspace.webapp.controller;

import static com.researchspace.session.SessionAttributeUtils.TIMEZONE;

import com.researchspace.auth.TimezoneAdjuster;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.session.SessionAttributeUtils;
import java.io.IOException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

/** Interceptor that handles getting Timezone from cookie. This is only needed for SSO variants. */
public class TimezoneInterceptor extends HandlerInterceptorAdapter {

  private @Autowired IPropertyHolder properties;
  private @Autowired TimezoneAdjuster timezoneAdjuster;

  void setTimezoneAdjuster(TimezoneAdjuster timezoneAdjuster) {
    this.timezoneAdjuster = timezoneAdjuster;
  }

  /**
   * checks whether a timezone cookie is present in the request, if so it adds it to the session, if
   * it is not already in the session
   */
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws IOException {
    boolean tzAddedToSession = false;
    if (properties.isSSO() && request.getSession().getAttribute(TIMEZONE) == null) {
      if (request.getCookies() != null) {
        for (Cookie cookie : request.getCookies()) {
          if (TIMEZONE.equals(cookie.getName())) {
            String tz = cookie.getValue();
            if (!StringUtils.isEmpty(tz)) {
              timezoneAdjuster.setUserTimezoneInSession(tz, request.getSession());
              tzAddedToSession = true;
            }
          }
        }
      }
    }
    request.getSession().setAttribute(SessionAttributeUtils.FIRST_REQUEST, tzAddedToSession);

    return true;
  }
}
