package com.researchspace.dao.customliquibaseupdates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.auth.password.RSpacePasswordEncoder;
import com.researchspace.model.User;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class WrapLegacyPasswordHashes_RSDEV894Test extends SpringTransactionalTest {

  // Shiro Sha256Hash fixtures, see RSpacePasswordEncoderTest
  private static final String SALT = "AxQlNkdYaXqLnK2+z+DxAg==";
  private static final String SALTED_HEX =
      "500ae17802c8636f5983eab68f86e151e3f0cb054f3250be363c46f2335844c8"; // legacyPass1
  private static final String UNSALTED_HEX =
      "CAA6EEC0FAA20EFBA3B7AF44AF7107B05334759954EF3581030CC8E6199A33BF"; // sysWisc23!

  private @Autowired RSpacePasswordEncoder passwordEncoder;

  @Test
  void wrapsSaltedAndUnsaltedHashesOnce() throws Exception {
    User salted = createAndSaveRandomUser();
    User unsalted = createAndSaveRandomUser();
    storeRaw(salted, SALTED_HEX, SALT);
    storeRaw(unsalted, UNSALTED_HEX, null);

    String message = runChange().getConfirmationMessage();
    assertTrue(
        message.startsWith("Wrapped 2 legacy password hashes in Argon2id, skipped 0"), message);
    String saltedWrapped = storedPassword(salted);
    String unsaltedWrapped = storedPassword(unsalted);
    assertWrapped(saltedWrapped);
    assertWrapped(unsaltedWrapped);
    assertNull(storedSalt(salted));
    assertTrue(passwordEncoder.matches("legacyPass1", saltedWrapped));
    assertTrue(passwordEncoder.matches("sysWisc23!", unsaltedWrapped));

    WrapLegacyPasswordHashes_RSDEV894 second = runChange();
    assertTrue(second.getConfirmationMessage().startsWith("Wrapped 0 "));
    assertEquals(saltedWrapped, storedPassword(salted));
    assertEquals(unsaltedWrapped, storedPassword(unsalted));
  }

  @Test
  void leavesNonHashValuesAlone() throws Exception {
    User odd = createAndSaveRandomUser();
    storeRaw(odd, "not-a-hash", null);
    User badSalt = createAndSaveRandomUser();
    storeRaw(badSalt, SALTED_HEX, "%%%");

    String message = runChange().getConfirmationMessage();

    assertEquals("not-a-hash", storedPassword(odd));
    assertEquals(SALTED_HEX, storedPassword(badSalt));
    assertTrue(message.contains("skipped 2"), message);
  }

  @Test
  void endsABatchAfterEveryBatchSizeWrappedRows() throws Exception {
    List<User> legacy = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      User u = createAndSaveRandomUser();
      storeRaw(u, SALTED_HEX, SALT);
      legacy.add(u);
    }
    List<Long> wrappedAtBatchEnd = new ArrayList<>();
    WrapLegacyPasswordHashes_RSDEV894 change =
        new WrapLegacyPasswordHashes_RSDEV894() {
          @Override
          void endBatch() {
            wrappedAtBatchEnd.add(
                legacy.stream().filter(u -> !SALTED_HEX.equals(storedPassword(u))).count());
          }
        };
    change.batchSize = 2;

    runChange(change);

    assertEquals(List.of(2L, 4L), wrappedAtBatchEnd);
    legacy.forEach(u -> assertWrapped(storedPassword(u)));
  }

  private WrapLegacyPasswordHashes_RSDEV894 runChange() {
    return runChange(new WrapLegacyPasswordHashes_RSDEV894());
  }

  private WrapLegacyPasswordHashes_RSDEV894 runChange(WrapLegacyPasswordHashes_RSDEV894 change) {
    change.context = applicationContext;
    change.sessionFactory = sessionFactory;
    change.addBeans();
    change.doExecute(null);
    return change;
  }

  private void assertWrapped(String stored) {
    assertTrue(stored.startsWith("{" + RSpacePasswordEncoder.LEGACY_SHA256_ID + "}"), stored);
  }

  private void storeRaw(User u, String password, String salt) {
    sessionFactory.getCurrentSession().flush();
    sessionFactory
        .getCurrentSession()
        .createNativeMutationQuery("update User set password = :pwd, salt = :salt where id = :id")
        .setParameter("pwd", password)
        .setParameter("salt", salt)
        .setParameter("id", u.getId())
        .executeUpdate();
  }

  private String storedPassword(User u) {
    return (String) column("password", u);
  }

  private String storedSalt(User u) {
    return (String) column("salt", u);
  }

  private Object column(String name, User u) {
    return sessionFactory
        .getCurrentSession()
        .createNativeQuery("select " + name + " from User where id = :id", Object.class)
        .setParameter("id", u.getId())
        .uniqueResult();
  }
}
