package com.researchspace.dao.customliquibaseupdates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.EcatImage;
import com.researchspace.model.User;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.sql.Connection;
import liquibase.changelog.ChangeLogParameters;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.parser.ChangeLogParserFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class RestoreDeleteOnRepairedMediaAcls_RSDEV1438IT extends RealTransactionSpringTestBase {

  @Test
  void ownerCanDeleteRepairedMediaFileAfterChangeset() throws Exception {
    User user = createInitAndLoginAnyUser();
    String u = user.getUsername();
    EcatImage repaired = addImageToGallery(user);
    EcatImage control = addImageToGallery(user);
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    // The exact ACL UpdateEmptyACLsForMediaFiles_RSPAC2559 wrote: seven owner elements, no DELETE.
    String updaterAcl =
        String.join(
            "&",
            u + "=RECORD:WRITE:",
            u + "=RECORD:CREATE:",
            u + "=RECORD:CREATE_FOLDER:",
            u + "=RECORD:SEND:",
            u + "=RECORD:FOLDER_RECEIVE:",
            u + "=RECORD:RENAME:",
            u + "=RECORD:COPY:");
    jdbc.update("UPDATE BaseRecord SET acl = ? WHERE id = ?", updaterAcl, repaired.getId());
    String controlAcl = aclOf(jdbc, control.getId());

    assertThrows(
        AuthorizationException.class,
        () -> recordDeletionMgr.deleteRecord(null, repaired.getId(), user));

    runChangeset();

    assertEquals(updaterAcl + "&" + u + "=RECORD:DELETE:", aclOf(jdbc, repaired.getId()));
    assertEquals(controlAcl, aclOf(jdbc, control.getId()));
    recordDeletionMgr.deleteRecord(null, repaired.getId(), user);
    assertTrue(
        jdbc.queryForObject(
            "SELECT deleted FROM BaseRecord WHERE id = ?", Boolean.class, repaired.getId()));
  }

  private String aclOf(JdbcTemplate jdbc, Long id) {
    return jdbc.queryForObject("SELECT acl FROM BaseRecord WHERE id = ?", String.class, id);
  }

  private void runChangeset() throws Exception {
    String file = "sqlUpdates/changeLog-rsdev-1438.xml";
    try (Connection connection = dataSource.getConnection();
        var resources = new ClassLoaderResourceAccessor()) {
      var changelog =
          ChangeLogParserFactory.getInstance()
              .getParser(file, resources)
              .parse(file, new ChangeLogParameters(), resources);
      var database =
          DatabaseFactory.getInstance()
              .findCorrectDatabaseImplementation(new JdbcConnection(connection));
      changelog.getChangeSets().get(0).execute(changelog, database);
      database.commit();
    }
  }
}
