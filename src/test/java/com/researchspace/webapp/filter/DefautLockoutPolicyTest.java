package com.researchspace.webapp.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import org.junit.Before;
import org.junit.Test;

public class DefautLockoutPolicyTest {

  private static final int TIMEOUT = 50;
  private static final int LOCKOUT = 50;

  private User u;
  private DefaultLockoutPolicy policy;

  @Before
  public void setUp() {
    u = TestFactory.createAnyUser("any");
    policy = new DefaultLockoutPolicy();
    policy.setLockoutDuration(LOCKOUT);
    policy.setMaxFailures(2);
    policy.setTimeout(TIMEOUT);
  }

  @Test
  public void testHandleLockoutOnFailure() throws InterruptedException {
    assertFalse(u.isAccountLocked()); // sanity check

    // locked out after 2 attempts
    policy.handleLockoutOnFailure(u);
    assertFalse(u.isAccountLocked());
    policy.handleLockoutOnFailure(u);
    assertTrue(u.isAccountLocked());
    // logging with right password doesn't unlock yet
    policy.handleLockoutOnSuccess(u);
    assertTrue(u.isAccountLocked());
    assertEquals(2, u.getNumConsecutiveLoginFailures());

    Thread.sleep(TIMEOUT + LOCKOUT + 1); // > max time

    // when lockout timeout is over account should be unlocked and attempts reset
    policy.handleLockoutOnFailure(u);
    assertFalse(u.isAccountLocked());
    assertEquals(1, u.getNumConsecutiveLoginFailures());

    // when providing right password attempts should be reset
    policy.handleLockoutOnSuccess(u);
    assertFalse(u.isAccountLocked());
    assertNull(u.getLoginFailure());
    assertEquals(0, u.getNumConsecutiveLoginFailures());
  }
}
