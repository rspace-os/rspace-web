package com.researchspace.dao.customliquibaseupdates;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component("appContextRetriever")
public class AppContextRetriever implements ApplicationContextAware {

  private static ApplicationContext context;

  public static ApplicationContext getApplicationContext() {
    return context;
  }

  @Override
  public void setApplicationContext(ApplicationContext ac) throws BeansException {
    context = ac;
  }
}
