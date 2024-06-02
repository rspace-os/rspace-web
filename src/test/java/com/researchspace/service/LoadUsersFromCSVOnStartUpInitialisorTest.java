package com.researchspace.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.researchspace.service.impl.LoadUsersFromCSVOnStartUpInitialisor;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class LoadUsersFromCSVOnStartUpInitialisorTest extends SpringTransactionalTest {

  @Autowired
  @Qualifier("loadUsersFromCSVOnStartUpInitialisor")
  private IApplicationInitialisor init;

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {
    super.tearDown();
    contentInitializer.setCustomInitActive(true); // restore default setting
  }

  @Test
  public void testOnInitialAppDeploymentFailsGracefullyIfCSVFileMissing() throws Exception {
    LoadUsersFromCSVOnStartUpInitialisor tss =
        getTargetObject(init, LoadUsersFromCSVOnStartUpInitialisor.class);
    String original = tss.getLoadUsersOnStartUpFile();
    tss.setLoadUsersOnStartUpFile("sddaddsad"); // non existent fi
    int initialNumGRoups = getTotalNumGroups();
    init.onInitialAppDeployment();
    assertEquals(initialNumGRoups, getTotalNumGroups());
    tss.setLoadUsersOnStartUpFile(original);
  }

  // this code is currently not used in application - it has been superceded by liquibase
  // for loading in data.
  @Ignore
  @Test
  public void testOnInitialAppDeploymentHappyCase() {
    int initialNumUsers = getTotalNumUsers();
    int initialNumGRoups = getTotalNumGroups();
    contentInitializer.setCustomInitActive(false); // don't create content, not needed
    init.onInitialAppDeployment();
    // fals here on jenkins??
    assertTrue(getTotalNumGroups() > initialNumGRoups);
    assertTrue(getTotalNumUsers() > initialNumUsers);
  }
}
