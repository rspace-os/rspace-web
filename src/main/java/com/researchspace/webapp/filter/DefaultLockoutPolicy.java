package com.researchspace.webapp.filter;

import com.researchspace.model.User;
import java.util.Calendar;
import org.springframework.stereotype.Component;

/**
 * Locks a user acount for lockoutDuration milliseconds if there are <code>maxFailures</code> login
 * failures within <code>timeout</code> seconds.
 *
 * <p>After lockoutDuration millis, the user account is unlocked on first successful login attempt.
 */
@Component("lockoutPolicy")
public class DefaultLockoutPolicy implements IUserAccountLockoutPolicy {

  private static final int MAX_CONSECUTIVE_FAILURES_DEFAULT = 4;
  private static final long TIMEOUT_DEFAULT_MILLIS = 1000 * 60 * 2; // 2 minutes
  private static final long LOCKOUT_TIME_DEFAULT_MILLIS = 1000 * 60 * 5; // 5 minutes

  private int maxFailures = MAX_CONSECUTIVE_FAILURES_DEFAULT;
  private long timeout = TIMEOUT_DEFAULT_MILLIS;
  private long lockoutDuration = LOCKOUT_TIME_DEFAULT_MILLIS;

  @Override
  public void handleLockoutOnSuccess(User u) {

    // reset failed attempts if below failed attempts limit or if lockout time is over
    if (u.getLoginFailure() != null) {
      if (u.getNumConsecutiveLoginFailures() < maxFailures || isAfterLockoutTime(u)) {
        resetFailedAttempts(u);
      }
    }
  }

  @Override
  public boolean isAfterLockoutTime(User u) {
    return milisSinceFirstLoginFailure(u) > (timeout + lockoutDuration);
  }

  @Override
  public void handleLockoutOnFailure(User u) {

    long millisSinceFirstFailure = milisSinceFirstLoginFailure(u);

    // reset failed attempts if lockout time is over
    if (u.getLoginFailure() != null && isAfterLockoutTime(u)) {
      resetFailedAttempts(u);
    }

    u.setNumConsecutiveLoginFailures((byte) (u.getNumConsecutiveLoginFailures() + 1));
    if (u.getLoginFailure() == null || millisSinceFirstFailure >= timeout) {
      u.setLoginFailure(Calendar.getInstance().getTime());
    }

    if (u.getNumConsecutiveLoginFailures() >= maxFailures && millisSinceFirstFailure < timeout) {
      u.setAccountLocked(true);
    }
  }

  @Override
  public void forceUnlock(User user) {
    resetFailedAttempts(user);
  }

  private void resetFailedAttempts(User u) {
    u.setAccountLocked(false);
    u.setLoginFailure(null);
    u.setNumConsecutiveLoginFailures((byte) 0);
  }

  private long milisSinceFirstLoginFailure(User u) {
    if (u.getLoginFailure() == null) {
      return 0;
    }

    Long milisNow = Calendar.getInstance().getTime().getTime();
    return milisNow - u.getLoginFailure().getTime();
  }

  /*
   * =====================
   *  for tests
   * =====================
   */
  protected void setMaxFailures(int maxFailures) {
    this.maxFailures = maxFailures;
  }

  protected int getMaxFailures() {
    return maxFailures;
  }

  protected void setTimeout(long timeout) {
    this.timeout = timeout;
  }

  protected void setLockoutDuration(long lockoutDuration) {
    this.lockoutDuration = lockoutDuration;
  }
}
