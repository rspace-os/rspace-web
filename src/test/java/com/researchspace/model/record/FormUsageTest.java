package com.researchspace.model.record;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.User;
import java.util.Date;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FormUsageTest {

  FormUsage formUsage;

  @BeforeEach
  public void setUp() throws Exception {}

  @AfterEach
  public void tearDown() throws Exception {}

  @Test
  public void testFormUsageUserFormInitialState() throws InterruptedException {
    long currTime = new Date().getTime();
    Thread.sleep(5);
    createFormUsage();
    assertNull(formUsage.getId());

    assertNotNull(formUsage.getLastUsedTimeInMillis());
    assertTrue(formUsage.getLastUsedTimeInMillis() > currTime);
  }

  private void createFormUsage() {
    User u = TestFactory.createAnyUser("user");
    RSForm t = TestFactory.createAnyForm();
    formUsage = new FormUsage(u, t);
  }
}
