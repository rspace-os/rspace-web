package com.axiope.webapp.listener;

import com.researchspace.analytics.service.AnalyticsManager;
import com.researchspace.model.User;
import com.researchspace.session.SessionAttributeUtils;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.context.support.WebApplicationContextUtils;

public class AnalyticsSessionListener implements HttpSessionListener, ServletContextListener {

  @Autowired private AnalyticsManager analyticsManager;

  @Override
  public void sessionCreated(HttpSessionEvent se) {
    // nothing here
  }

  @Override
  public void sessionDestroyed(HttpSessionEvent se) {
    if (se.getSession() != null) {
      User user = (User) se.getSession().getAttribute(SessionAttributeUtils.USER);
      if (user != null) {
        analyticsManager.userLoggedOut(user, null);
      }
    }
  }

  @Override
  public void contextInitialized(ServletContextEvent sce) {
    WebApplicationContextUtils.getRequiredWebApplicationContext(sce.getServletContext())
        .getAutowireCapableBeanFactory()
        .autowireBean(this);

    sce.getServletContext()
        .setAttribute("analyticsServerKey", analyticsManager.getAnalyticsServerKey());
  }

  @Override
  public void contextDestroyed(ServletContextEvent sce) {
    // nothing here
  }
}
