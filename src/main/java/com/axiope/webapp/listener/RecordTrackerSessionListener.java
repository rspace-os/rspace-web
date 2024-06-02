package com.axiope.webapp.listener;

import com.researchspace.service.FormManager;
import com.researchspace.service.RecordManager;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.context.support.WebApplicationContextUtils;

public class RecordTrackerSessionListener implements HttpSessionListener, ServletContextListener {

  private Logger log = LoggerFactory.getLogger(RecordTrackerSessionListener.class);

  @Autowired private RecordManager recordManager;

  @Autowired private FormManager formManager;

  @Override
  public void sessionCreated(HttpSessionEvent se) {
    log.info("Session Created " + se.toString());
  }

  @Override
  public void sessionDestroyed(HttpSessionEvent se) {

    String sessionId = se.getSession().getId();
    recordManager.removeFromEditorTracker(sessionId);
    formManager.removeFromEditorTracker(sessionId);
    log.info("Session Destroyed " + se.toString());
  }

  @Override
  public void contextInitialized(ServletContextEvent sce) {
    WebApplicationContextUtils.getRequiredWebApplicationContext(sce.getServletContext())
        .getAutowireCapableBeanFactory()
        .autowireBean(this);
  }

  @Override
  public void contextDestroyed(ServletContextEvent sce) {}
}
