package com.researchspace.dao.customliquibaseupdates;

import com.researchspace.auth.password.RSpacePasswordEncoder;
import java.util.List;
import java.util.regex.Pattern;
import liquibase.database.Database;
import org.hibernate.Session;

/**
 * Wraps every pre-Argon2 SHA-256 login password hash in Argon2id, once, so no fast hash stays at
 * rest (RSDEV-894, ADR 0011). Rows already carrying an encoder id prefix are skipped, so a re-run
 * after a crash only finishes the remainder. The salt moves into the wrapped value and the salt
 * column is cleared.
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
                "select id, password, salt from User"
                    + " where password is not null and password not like '{%'",
                Object[].class)
            .list();
    for (Object[] row : rows) {
      Long id = ((Number) row[0]).longValue();
      String password = (String) row[1];
      String salt = (String) row[2];
      if (!SHA256_HEX.matcher(password).matches()) {
        logger.warn("User id {} has a password that is not a SHA-256 hash, left unchanged", id);
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
}
