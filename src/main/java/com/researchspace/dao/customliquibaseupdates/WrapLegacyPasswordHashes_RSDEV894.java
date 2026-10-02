package com.researchspace.dao.customliquibaseupdates;

import com.researchspace.auth.password.RSpacePasswordEncoder;
import java.util.Base64;
import java.util.List;
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

  private RSpacePasswordEncoder passwordEncoder;
  private int wrapped;
  private int skipped;

  @Override
  protected void addBeans() {
    passwordEncoder = context.getBean(RSpacePasswordEncoder.class);
  }

  @Override
  public String getConfirmationMessage() {
    return "Wrapped " + wrapped + " legacy password hashes in Argon2id, skipped " + skipped;
  }

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
      session
          .createNativeMutationQuery("update User set password = :pwd, salt = null where id = :id")
          .setParameter("pwd", passwordEncoder.wrapLegacySha256(password, salt))
          .setParameter("id", id)
          .executeUpdate();
      wrapped++;
    }
    logger.info(getConfirmationMessage());
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
