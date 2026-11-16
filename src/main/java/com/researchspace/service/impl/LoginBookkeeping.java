package com.researchspace.service.impl;

import com.researchspace.dao.UserDao;
import com.researchspace.model.User;
import com.researchspace.webapp.filter.IUserAccountLockoutPolicy;
import java.util.Date;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Records login attempts without merging a detached account. Each attempt reads the lockout fields,
 * applies the policy to a copy and writes only those fields, guarded on the values it read, in its
 * own transaction. A conflicting concurrent attempt makes the guard miss, and the attempt is
 * recomputed; after {@link #GUARDED_ATTEMPTS} misses it runs once more under a row lock.
 */
@Component
public class LoginBookkeeping {
  private static final int GUARDED_ATTEMPTS = 3;

  private final UserDao users;
  private final IUserAccountLockoutPolicy policy;
  private final TransactionTemplate transaction;

  public LoginBookkeeping(
      UserDao users, IUserAccountLockoutPolicy policy, PlatformTransactionManager transactions) {
    this.users = users;
    this.policy = policy;
    transaction = new TransactionTemplate(transactions);
    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  /** Records a successful login, including the policy's expiry unlock, and returns the state. */
  public User recordSuccess(String username) {
    for (int attempt = 0; attempt < GUARDED_ATTEMPTS; attempt++) {
      User saved =
          transaction.execute(
              status -> {
                User before = users.readLoginState(username);
                User after = copy(before);
                applySuccess(after);
                return users.updateLoginState(before, after, after.getLastLogin()) ? after : null;
              });
      if (saved != null) {
        return saved;
      }
    }
    return transaction.execute(
        status -> {
          User current = users.lockLoginAccount(users.readLoginState(username).getId());
          applySuccess(current);
          return current;
        });
  }

  /** Applies the lockout policy to a failed login. */
  public void recordFailure(String usernameOrAlias) {
    for (int attempt = 0; attempt < GUARDED_ATTEMPTS; attempt++) {
      Boolean saved =
          transaction.execute(
              status -> {
                User before = users.readLoginState(usernameOrAlias);
                User after = copy(before);
                policy.handleLockoutOnFailure(after);
                return users.updateLoginState(before, after, null);
              });
      if (Boolean.TRUE.equals(saved)) {
        return;
      }
    }
    transaction.executeWithoutResult(
        status ->
            policy.handleLockoutOnFailure(
                users.lockLoginAccount(users.readLoginState(usernameOrAlias).getId())));
  }

  private void applySuccess(User user) {
    policy.handleLockoutOnSuccess(user);
    user.setLoginFailure(null);
    user.setNumConsecutiveLoginFailures((byte) 0);
    user.setLastLogin(new Date());
  }

  private static User copy(User before) {
    User after = new User();
    after.setId(before.getId());
    after.setNumConsecutiveLoginFailures(before.getNumConsecutiveLoginFailures());
    after.setLoginFailure(before.getLoginFailure());
    after.setAccountLocked(before.isAccountLocked());
    return after;
  }
}
