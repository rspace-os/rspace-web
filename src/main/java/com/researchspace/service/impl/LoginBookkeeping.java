package com.researchspace.service.impl;

import com.researchspace.booking.dao.BookingCalendarPermissionGateDao;
import com.researchspace.booking.service.BookingCalendarRevocationCoordinator;
import com.researchspace.dao.UserDao;
import com.researchspace.model.User;
import com.researchspace.webapp.filter.IUserAccountLockoutPolicy;
import java.util.Date;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Runs each login policy attempt in its own transaction, without merging a detached account. */
@Component
public class LoginBookkeeping {
  private final UserDao users;
  private final IUserAccountLockoutPolicy policy;
  private final BookingCalendarRevocationCoordinator revocations;
  private final BookingCalendarPermissionGateDao gate;
  private final TransactionTemplate transaction;

  public LoginBookkeeping(
      UserDao users,
      IUserAccountLockoutPolicy policy,
      BookingCalendarRevocationCoordinator revocations,
      BookingCalendarPermissionGateDao gate,
      PlatformTransactionManager transactions) {
    this.users = users;
    this.policy = policy;
    this.revocations = revocations;
    this.gate = gate;
    transaction = new TransactionTemplate(transactions);
    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  /** Records success and the policy's expiry unlock, retrying changed policy inputs. */
  public User recordSuccess(String username) {
    rejectNestedGate();
    for (int attempt = 0; attempt < 3; attempt++) {
      User saved =
          transaction.execute(
              status -> {
                User before = users.readLoginState(username);
                User after = copy(before);
                applySuccess(after);
                return users.updateLoginState(before, after, after.getLastLogin()) ? after : null;
              });
      if (saved != null) return saved;
    }
    return transaction.execute(
        status -> {
          User state = users.readLoginState(username);
          User current = users.lockLoginAccount(state.getId());
          applySuccess(current);
          return current;
        });
  }

  /** Retries neutral guarded writes, then re-evaluates under the gate before locking an account. */
  public void recordFailure(String usernameOrAlias) {
    rejectNestedGate();
    Long userId = null;
    for (int attempt = 0; attempt < 3; attempt++) {
      Attempt result =
          transaction.execute(
              status -> {
                User before = users.readLoginState(usernameOrAlias);
                User after = copy(before);
                policy.handleLockoutOnFailure(after);
                boolean newLock = !before.isAccountLocked() && after.isAccountLocked();
                boolean saved = !newLock && users.updateLoginState(before, after, null);
                return new Attempt(before.getId(), newLock, saved);
              });
      userId = result.userId();
      if (result.saved()) return;
      if (result.newLock()) break;
    }
    Long id = userId;
    transaction.executeWithoutResult(
        status -> {
          revocations.affectUser(id);
          User current = users.lockLoginAccount(id);
          policy.handleLockoutOnFailure(current);
        });
  }

  private void applySuccess(User user) {
    policy.handleLockoutOnSuccess(user);
    user.setLoginFailure(null);
    user.setNumConsecutiveLoginFailures((byte) 0);
    user.setLastLogin(new Date());
  }

  private void rejectNestedGate() {
    gate.assertNoSuspendedAncestor();
    if (gate.hasActiveGate()) {
      throw new IllegalStateException("Login bookkeeping cannot suspend a permission gate");
    }
  }

  private static User copy(User before) {
    User after = new User();
    after.setId(before.getId());
    after.setNumConsecutiveLoginFailures(before.getNumConsecutiveLoginFailures());
    after.setLoginFailure(before.getLoginFailure());
    after.setAccountLocked(before.isAccountLocked());
    return after;
  }

  private record Attempt(Long userId, boolean newLock, boolean saved) {}
}
