package com.researchspace.service.impl;

import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.IApplicationInitialisor;
import org.springframework.context.ApplicationContext;

public class FeatureFlagInitialisor implements IApplicationInitialisor {

  private final FeatureFlagManager featureFlagManager;

  public FeatureFlagInitialisor(FeatureFlagManager featureFlagManager) {
    this.featureFlagManager = featureFlagManager;
  }

  @Override
  public void onInitialAppDeployment() {}

  @Override
  public void onAppVersionUpdate() {}

  @Override
  public void onAppStartup(ApplicationContext applicationContext) {
    featureFlagManager.reconcileOnStartup();
  }
}
