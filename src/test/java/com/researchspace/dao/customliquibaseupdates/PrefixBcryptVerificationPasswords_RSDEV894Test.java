package com.researchspace.dao.customliquibaseupdates;

import static com.researchspace.testutils.LegacyVerificationPasswordFixture.BCRYPT_HASH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.User;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.jupiter.api.Test;

class PrefixBcryptVerificationPasswords_RSDEV894Test extends SpringTransactionalTest {

  @Test
  void prefixesBareBcryptOnce() throws Exception {
    User u = createAndSaveRandomUser();
    storeVerificationPassword(u, BCRYPT_HASH);

    runChange();
    assertEquals("{bcrypt}" + BCRYPT_HASH, storedVerificationPassword(u));
    assertTrue(runChange().getConfirmationMessage().startsWith("Prefixed 0 "));
    assertEquals("{bcrypt}" + BCRYPT_HASH, storedVerificationPassword(u));
  }

  @Test
  void clearsValuesThatCouldNeverVerify() throws Exception {
    User notBcrypt = createAndSaveRandomUser();
    User unknownId = createAndSaveRandomUser();
    storeVerificationPassword(notBcrypt, "not-bcrypt");
    storeVerificationPassword(unknownId, "{noop}x");
    runChange();
    assertNull(storedVerificationPassword(notBcrypt));
    assertNull(storedVerificationPassword(unknownId));
  }

  private PrefixBcryptVerificationPasswords_RSDEV894 runChange() {
    PrefixBcryptVerificationPasswords_RSDEV894 change =
        new PrefixBcryptVerificationPasswords_RSDEV894();
    change.context = applicationContext;
    change.sessionFactory = sessionFactory;
    change.addBeans();
    change.doExecute(null);
    return change;
  }

  private void storeVerificationPassword(User u, String value) {
    sessionFactory.getCurrentSession().flush();
    sessionFactory
        .getCurrentSession()
        .createNativeMutationQuery("update User set verificationPassword = :vp where id = :id")
        .setParameter("vp", value)
        .setParameter("id", u.getId())
        .executeUpdate();
  }

  private String storedVerificationPassword(User u) {
    return (String)
        sessionFactory
            .getCurrentSession()
            .createNativeQuery("select verificationPassword from User where id = :id", Object.class)
            .setParameter("id", u.getId())
            .uniqueResult();
  }
}
