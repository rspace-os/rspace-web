package com.researchspace.service.impl;

import com.researchspace.model.system.SystemPropertyValue;
import com.researchspace.service.IApplicationInitialisor;
import com.researchspace.service.SystemPropertyManager;
import lombok.AccessLevel;
import lombok.Setter;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/** Applies changes to System -> Configuration options on RSpace startup */
@Component("SystemConfigurationInitialisor")
public class SystemConfigurationInitialisor implements IApplicationInitialisor {

  private Logger log = LoggerFactory.getLogger(AbstractAppInitializor.class);

  @Value("${chemistry.provider}")
  @Setter(AccessLevel.PACKAGE)
  private String chemistryProvider;

  @Value("${chemistry.service.url}")
  @Setter(AccessLevel.PACKAGE)
  private String chemistryServiceUrl;

  @Autowired
  @Setter(AccessLevel.PACKAGE)
  private SystemPropertyManager systemPropertyManager;

  @Override
  public void onInitialAppDeployment() {}

  @Override
  public void onAppVersionUpdate() {}

  @Override
  public void onAppStartup(ApplicationContext applicationContext) {

    disableChemistryIfNoChemistryProvider();
  }

  private void disableChemistryIfNoChemistryProvider() {

    if (StringUtils.isEmpty(chemistryProvider) || StringUtils.isEmpty(chemistryServiceUrl)) {
      SystemPropertyValue chemistryAvailableValue =
          systemPropertyManager.findByName("chemistry.available");
      boolean isChemistryDisabled = "DENIED".equals(chemistryAvailableValue.getValue());

      if (!isChemistryDisabled) {
        log.info(
            "chemistry.provider: {}, chemistryServiceUrl: {}, chemistryAvailable: {}",
            chemistryProvider,
            chemistryServiceUrl,
            chemistryAvailableValue.getValue());

        /* passing 'null' as subject - this works because saving new value of 'chemistry.available'
         * doesn't trigger any side effect requiring authenticated user */
        systemPropertyManager.save("chemistry.available", "DENIED", null);
        log.info("chemistry.available system property changed to 'DENIED'");
      }
    }
  }
}
