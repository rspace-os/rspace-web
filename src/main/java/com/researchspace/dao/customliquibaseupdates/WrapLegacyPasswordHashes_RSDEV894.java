package com.researchspace.dao.customliquibaseupdates;

import com.researchspace.auth.password.RSpacePasswordEncoder;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import liquibase.database.Database;
import org.hibernate.Session;

/**
 * Wraps every pre-Argon2 SHA-256 login password hash in Argon2id, once, so no fast hash stays at
 * rest (RSDEV-894, ADR 0011). Rows already carrying an encoder id prefix are skipped, so running it
 * again changes nothing. The salt moves into the wrapped value and the salt column is cleared. Rows
 * that are not a SHA-256 hex digest with a Base64 salt are logged at ERROR and left unchanged;
 * those users cannot log in until an administrator resets their password.
 */
public class WrapLegacyPasswordHashes_RSDEV894 extends AbstractCustomLiquibaseUpdater {

  private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-fA-F]{64}");
  static final int BATCH_SIZE = 100;

  int batchSize = BATCH_SIZE;

  private RSpacePasswordEncoder passwordEncoder;
  private int wrapped;
  private int skipped;
  private long encodeNanos;
  private long maxEncodeNanos;
  private long loopNanos;

  @Override
  protected void addBeans() {
    passwordEncoder = context.getBean(RSpacePasswordEncoder.class);
  }

  /** Includes the Argon2 time, so every upgrade's startup log is a timing data point. */
  @Override
  public String getConfirmationMessage() {
    return String.format(
        Locale.ROOT,
        "Wrapped %d legacy password hashes in Argon2id, skipped %d:"
            + " encode %d ms (%.1f ms/row, max %d ms), loop %d ms",
        wrapped,
        skipped,
        millis(encodeNanos),
        wrapped == 0 ? 0.0 : encodeNanos / 1e6 / wrapped,
        millis(maxEncodeNanos),
        millis(loopNanos));
  }

  /**
   * Commits every {@link #BATCH_SIZE} rows so a killed run resumes: the next run skips rows already
   * wrapped.
   */
  @Override
  protected void doExecute(Database database) {
    Session session = sessionFactory.getCurrentSession();
    List<Object[]> rows =
        session
            .createNativeQuery(
                "select id, username, password, salt from User"
                    + " where password is not null and password not like :encoderIdPrefix",
                Object[].class)
            .setParameter("encoderIdPrefix", "{%")
            .list();
    long loopStart = System.nanoTime();
    for (Object[] row : rows) {
      Long id = ((Number) row[0]).longValue();
      String username = (String) row[1];
      String password = (String) row[2];
      String salt = (String) row[3];
      if (!SHA256_HEX.matcher(password).matches() || !isBase64OrNull(salt)) {
        logger.error(
            "Password of user [{}] (id {}) is not a salted SHA-256 hash and was left unchanged;"
                + " an administrator must reset it before this user can log in",
            username,
            id);
        skipped++;
        continue;
      }
      long encodeStart = System.nanoTime();
      String wrappedHash = passwordEncoder.wrapLegacySha256(password, salt);
      long encodeTook = System.nanoTime() - encodeStart;
      encodeNanos += encodeTook;
      maxEncodeNanos = Math.max(maxEncodeNanos, encodeTook);
      session
          .createNativeMutationQuery("update User set password = :pwd, salt = null where id = :id")
          .setParameter("pwd", wrappedHash)
          .setParameter("id", id)
          .executeUpdate();
      wrapped++;
      if (wrapped % batchSize == 0) {
        endBatch();
        session = sessionFactory.getCurrentSession();
      }
    }
    loopNanos = System.nanoTime() - loopStart;
    logger.info(getConfirmationMessage());
  }

  void endBatch() {
    commitTransaction();
    openTransaction(getTxMger());
  }

  private static long millis(long nanos) {
    return nanos / 1_000_000;
  }

  private static boolean isBase64OrNull(String salt) {
    if (salt == null) {
      return true;
    }
    try {
      Base64.getDecoder().decode(salt);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
