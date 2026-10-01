package com.researchspace.service.impl;

import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.IApplicationInitialisor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

public class FeatureFlagInitialisor implements IApplicationInitialisor {

  @Autowired private FeatureFlagManager featureFlagManager;

  @Override
  public void onInitialAppDeployment() {}

  @Override
  public void onAppVersionUpdate() {}

  @Override
  public void onAppStartup(ApplicationContext applicationContext) {
    featureFlagManager.reconcileOnStartup();
  }
}
