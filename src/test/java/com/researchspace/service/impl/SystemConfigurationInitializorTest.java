package com.researchspace.service.impl;

import static org.mockito.Mockito.when;

import com.researchspace.model.system.SystemPropertyValue;
import com.researchspace.service.SystemPropertyManager;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class SystemConfigurationInitializorTest extends SpringTransactionalTest {

  public @Rule MockitoRule mockito = MockitoJUnit.rule();

  @Mock private SystemPropertyManager mockSystemPropertyManager;

  private SystemConfigurationInitialisor systemConfigurationInitializor;

  @Before
  public void setUp() throws Exception {
    super.setUp();

    systemConfigurationInitializor = new SystemConfigurationInitialisor();
    systemConfigurationInitializor.setSystemPropertyManager(mockSystemPropertyManager);
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testOnAppStartupUpdateChemistrySetting() throws IOException {

    when(mockSystemPropertyManager.findByName("chemistry.available"))
        .thenReturn(new SystemPropertyValue(null, "ALLOWED"));

    //
    systemConfigurationInitializor.setChemistryProvider("");
    systemConfigurationInitializor.setChemistryServiceUrl("");
    systemConfigurationInitializor.onAppStartup(null);

    //  verify(mockChemistryClient, Mockito.never()).save(Mockito.any(), Mockito.any());
  }
}
