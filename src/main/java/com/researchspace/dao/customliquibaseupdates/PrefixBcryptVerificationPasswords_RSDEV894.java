package com.researchspace.dao.customliquibaseupdates;

import com.researchspace.auth.password.RSpacePasswordEncoder;
import java.util.List;
import java.util.regex.Pattern;
import liquibase.database.Database;
import org.hibernate.Session;

/**
 * Prefixes every bare BCrypt verification password with {@code {bcrypt}} so the shared password
 * encoder can read it; it is re-encoded as Argon2id on next successful use (RSDEV-894, ADR 0011).
 * Prefixed values are skipped, so running it again changes nothing. Values that are not BCrypt
 * hashes are logged at ERROR and left unchanged; those users must set a new verification password.
 */
public class PrefixBcryptVerificationPasswords_RSDEV894 extends AbstractCustomLiquibaseUpdater {

  private static final Pattern BCRYPT = Pattern.compile("\\$2[abxy]?\\$\\d{2}\\$[./A-Za-z0-9]{53}");

  private int prefixed;
  private int skipped;

  @Override
  public String getConfirmationMessage() {
    return "Prefixed " + prefixed + " BCrypt verification passwords, skipped " + skipped;
  }

  @Override
  protected void doExecute(Database database) {
    Session session = sessionFactory.getCurrentSession();
    List<Object[]> rows =
        session
            .createNativeQuery(
                "select id, username, verificationPassword from User"
                    + " where verificationPassword is not null and verificationPassword <> ''"
                    + " and verificationPassword not like :encoderIdPrefix",
                Object[].class)
            .setParameter("encoderIdPrefix", "{%")
            .list();
    String prefix = "{" + RSpacePasswordEncoder.BCRYPT_ID + "}";
    for (Object[] row : rows) {
      Long id = ((Number) row[0]).longValue();
      String username = (String) row[1];
      String hash = (String) row[2];
      if (!BCRYPT.matcher(hash).matches()) {
        logger.error(
            "Verification password of user [{}] (id {}) is not a BCrypt hash and was left"
                + " unchanged; the user must set a new verification password",
            username,
            id);
        skipped++;
        continue;
      }
      session
          .createNativeMutationQuery("update User set verificationPassword = :vp where id = :id")
          .setParameter("vp", prefix + hash)
          .setParameter("id", id)
          .executeUpdate();
      prefixed++;
    }
    logger.info(getConfirmationMessage());
  }
}
