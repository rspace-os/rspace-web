package com.researchspace.service.impl;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.model.preference.HierarchicalPermission;
import com.researchspace.model.system.SystemProperty;
import com.researchspace.model.system.SystemPropertyValue;
import com.researchspace.service.SystemPropertyManager;
import com.researchspace.service.SystemPropertyName;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SystemConfigurationInitializorTest extends SpringTransactionalTest {

  @Mock private SystemPropertyManager mockSystemPropertyManager;

  private SystemConfigurationInitialisor systemConfigurationInitialisor;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();

    systemConfigurationInitialisor = new SystemConfigurationInitialisor();
    systemConfigurationInitialisor.setSystemPropertyManager(mockSystemPropertyManager);
  }

  @AfterEach
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testOnAppStartupUpdateChemistrySetting() throws IOException {

    // 1. simulate chemistry properties being not configured
    systemConfigurationInitialisor.setChemistryProvider("");
    systemConfigurationInitialisor.setChemistryServiceUrl("");

    // if system setting is already 'DENIED' do nothing
    when(mockSystemPropertyManager.findByName(SystemPropertyName.CHEMISTRY_AVAILABLE))
        .thenReturn(
            new SystemPropertyValue(
                new SystemProperty(null), HierarchicalPermission.DENIED.name()));
    systemConfigurationInitialisor.onAppStartup(null);
    verify(mockSystemPropertyManager, Mockito.never())
        .save(Mockito.any(), (HierarchicalPermission) Mockito.any(), Mockito.any());

    // if system setting is 'ALLOWED', update to 'DENIED'
    when(mockSystemPropertyManager.findByName(SystemPropertyName.CHEMISTRY_AVAILABLE))
        .thenReturn(
            new SystemPropertyValue(
                new SystemProperty(null), HierarchicalPermission.ALLOWED.name()));
    systemConfigurationInitialisor.onAppStartup(null);
    verify(mockSystemPropertyManager, Mockito.times(1))
        .save(Mockito.any(), (HierarchicalPermission) Mockito.any(), Mockito.any());

    // 2. simulate chemistry properties being configured
    systemConfigurationInitialisor.setChemistryProvider("indigo");
    systemConfigurationInitialisor.setChemistryServiceUrl("http://indigoService:8090");

    // system setting is 'ALLOWED' and properties are configured - do nothing
    lenient()
        .when(mockSystemPropertyManager.findByName(SystemPropertyName.CHEMISTRY_AVAILABLE))
        .thenReturn(
            new SystemPropertyValue(
                new SystemProperty(null), HierarchicalPermission.ALLOWED.name()));
    systemConfigurationInitialisor.onAppStartup(null);
    verify(mockSystemPropertyManager, Mockito.times(1))
        .save(Mockito.any(), (HierarchicalPermission) Mockito.any(), Mockito.any());
  }
}
