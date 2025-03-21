package com.researchspace.service.impl;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.model.preference.HierarchicalPermission;
import com.researchspace.model.system.SystemProperty;
import com.researchspace.model.system.SystemPropertyValue;
import com.researchspace.service.SystemPropertyManager;
import com.researchspace.service.SystemPropertyName;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class SystemConfigurationInitializorTest extends SpringTransactionalTest {

  public @Rule MockitoRule mockito = MockitoJUnit.rule();

  @Mock private SystemPropertyManager mockSystemPropertyManager;

  private SystemConfigurationInitialisor systemConfigurationInitialisor;

  @Before
  public void setUp() throws Exception {
    super.setUp();

    systemConfigurationInitialisor = new SystemConfigurationInitialisor();
    systemConfigurationInitialisor.setSystemPropertyManager(mockSystemPropertyManager);
  }

  @After
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
    when(mockSystemPropertyManager.findByName(SystemPropertyName.CHEMISTRY_AVAILABLE))
        .thenReturn(
            new SystemPropertyValue(
                new SystemProperty(null), HierarchicalPermission.ALLOWED.name()));
    systemConfigurationInitialisor.onAppStartup(null);
    verify(mockSystemPropertyManager, Mockito.times(1))
        .save(Mockito.any(), (HierarchicalPermission) Mockito.any(), Mockito.any());
  }
}
