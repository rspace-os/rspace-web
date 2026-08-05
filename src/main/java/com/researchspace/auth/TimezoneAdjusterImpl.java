package com.researchspace.auth;

import com.researchspace.session.SessionAttributeUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.jsp.jstl.core.Config;
import java.util.TimeZone;

public class TimezoneAdjusterImpl implements TimezoneAdjuster {

  @Override
  public void setUserTimezoneInSession(HttpServletRequest reqx, HttpSession sssn) {
    String timezone = reqx.getParameter("timezone");
    if (timezone != null) {
      setTimezoneInSession(sssn, timezone);
    }
  }

  private void setTimezoneInSession(HttpSession sssn, String timezone) {
    TimeZone tz = TimeZone.getTimeZone(timezone);

    sssn.setAttribute(SessionAttributeUtils.TIMEZONE, tz);
    // sets for JSP formatDate for session
    Config.set(sssn, Config.FMT_TIME_ZONE, tz);
  }

  @Override
  public void setUserTimezoneInSession(String timezone, HttpSession session) {
    setTimezoneInSession(session, timezone);
  }
}
