package com.researchspace.dao.customliquibaseupdates;

import com.researchspace.auth.password.RSpacePasswordEncoder;
import java.util.List;
import java.util.regex.Pattern;
import liquibase.database.Database;
import org.hibernate.Session;

/**
 * Prefixes every bare BCrypt verification password with {@code {bcrypt}} so the shared password
 * encoder can read it; it is re-encoded as Argon2id on next successful use (RSDEV-894, ADR 0011).
 * Values already carrying a registered encoder id are skipped, so running it again changes nothing.
 * Any other value could never verify; it is cleared and logged at ERROR, so the user can set a new
 * verification password.
 */
public class PrefixBcryptVerificationPasswords_RSDEV894 extends AbstractCustomLiquibaseUpdater {

  // the forms Spring's BCryptPasswordEncoder accepts
  private static final Pattern BCRYPT = Pattern.compile("\\$2[aby]?\\$\\d{2}\\$[./A-Za-z0-9]{53}");
  private static final List<String> REGISTERED_PREFIXES =
      List.of(
          "{" + RSpacePasswordEncoder.BCRYPT_ID + "}", "{" + RSpacePasswordEncoder.ARGON2_ID + "}");

  private int prefixed;
  private int cleared;

  @Override
  public String getConfirmationMessage() {
    return "Prefixed " + prefixed + " BCrypt verification passwords, cleared " + cleared;
  }

  @Override
  protected void doExecute(Database database) {
    Session session = sessionFactory.getCurrentSession();
    List<Object[]> rows =
        session
            .createNativeQuery(
                "select id, username, verificationPassword from User"
                    + " where verificationPassword is not null and verificationPassword <> ''",
                Object[].class)
            .list();
    String prefix = "{" + RSpacePasswordEncoder.BCRYPT_ID + "}";
    for (Object[] row : rows) {
      Long id = ((Number) row[0]).longValue();
      String username = (String) row[1];
      String hash = (String) row[2];
      if (REGISTERED_PREFIXES.stream().anyMatch(hash::startsWith)) {
        continue;
      }
      if (BCRYPT.matcher(hash).matches()) {
        update(session, id, prefix + hash);
        prefixed++;
      } else {
        logger.error(
            "Verification password of user [{}] (id {}) is not a BCrypt hash and was cleared;"
                + " the user must set a new verification password",
            username,
            id);
        update(session, id, null);
        cleared++;
      }
    }
    logger.info(getConfirmationMessage());
  }

  private static void update(Session session, Long id, String verificationPassword) {
    session
        .createNativeMutationQuery("update User set verificationPassword = :vp where id = :id")
        .setParameter("vp", verificationPassword)
        .setParameter("id", id)
        .executeUpdate();
  }
}
