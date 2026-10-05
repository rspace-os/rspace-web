package com.researchspace.dao.customliquibaseupdates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.User;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.jupiter.api.Test;

class PrefixBcryptVerificationPasswords_RSDEV894Test extends SpringTransactionalTest {

  // BCrypt.hashpw("verify1234", BCrypt.gensalt()), as stored before RSDEV-894
  private static final String LEGACY_BCRYPT =
      "$2a$10$fqWevKAPMKNsortKy6gS9eZbYfMnuItTnN4KUf2cy0w0dMTcIjzA6";

  @Test
  void prefixesBareBcryptOnce() throws Exception {
    User u = createAndSaveRandomUser();
    storeVerificationPassword(u, LEGACY_BCRYPT);

    runChange();
    assertEquals("{bcrypt}" + LEGACY_BCRYPT, storedVerificationPassword(u));
    assertTrue(runChange().getConfirmationMessage().startsWith("Prefixed 0 "));
    assertEquals("{bcrypt}" + LEGACY_BCRYPT, storedVerificationPassword(u));
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
