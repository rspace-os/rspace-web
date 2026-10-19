package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.model.User;
import com.researchspace.service.IntegrationsHandler;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class IntegrationCredentialTransactionIT extends RealTransactionSpringTestBase {
  @Autowired private IntegrationsHandler integrationsHandler;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
  }

  @AfterEach
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  void failedCredentialWriteRollsBackTheNewConfigSet() {
    User user = createAndSaveUser(getRandomAlphabeticString("credentialUser"));
    logoutAndLoginAs(user);
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    String countSql =
        "select count(*) from AppConfigElementSet s join UserAppConfig c on s.userAppConfig_id=c.id"
            + " join App a on c.app_id=a.id where c.user_id=? and a.name='app.dataverse'";
    Integer before = jdbc.queryForObject(countSql, Integer.class, user.getId());
    Map<String, String> options =
        Map.of(
            "DATAVERSE_ALIAS",
            "rollback",
            "DATAVERSE_URL",
            "https://dataverse.example",
            "DATAVERSE_APIKEY",
            "x".repeat(5000));

    assertThrows(
        DataIntegrityViolationException.class,
        () -> integrationsHandler.saveAppOptions(null, options, "DATAVERSE", false, user));

    assertEquals(before, jdbc.queryForObject(countSql, Integer.class, user.getId()));
    assertEquals(
        0,
        jdbc.queryForObject(
            "select count(*) from UserConnection where userId=? and providerId='DATAVERSE'",
            Integer.class,
            user.getUsername()));
  }
}
