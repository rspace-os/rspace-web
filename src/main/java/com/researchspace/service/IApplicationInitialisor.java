package com.researchspace.service;

import org.springframework.context.ApplicationContext;

/** Defines behaviour for functionality on application startup. */
public interface IApplicationInitialisor {

  /**
   * Implementations of this method are only called when an application is initialised for the first
   * time after deployment.
   */
  void onInitialAppDeployment();

  /**
   * Implementations of this method are only called when
   *
   * <ul>
   *   <li>an application is initialised for the first time after deployment.
   *   <li>An application version is updated.
   * </ul>
   */
  void onAppVersionUpdate();

  /**
   * Implementations of this method are called on each application startup and restart.
   *
   * @param applicationContext
   */
  void onAppStartup(ApplicationContext applicationContext);
}
