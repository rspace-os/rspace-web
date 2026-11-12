package com.researchspace.model.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HibernateTestDatabaseNameTest {

  private static final List<String> PROPERTIES =
      List.of(HibernateTest.DB_NAME_PROPERTY, "jdbc.server.url", "jdbc.url.maven");
  private final Map<String, String> saved = new HashMap<>();

  @BeforeEach
  void saveProperties() {
    for (String name : PROPERTIES) {
      saved.put(name, System.getProperty(name));
      System.clearProperty(name);
    }
  }

  @AfterEach
  void restoreProperties() {
    saved.forEach(
        (name, value) -> {
          if (value == null) {
            System.clearProperty(name);
          } else {
            System.setProperty(name, value);
          }
        });
  }

  @Test
  void defaultsToHibtestWhenPropertyAbsent() {
    assertEquals("hibtest", HibernateTest.scratchDatabaseName());
  }

  @Test
  void usesTheConfiguredName() {
    System.setProperty(HibernateTest.DB_NAME_PROPERTY, "hibtest_feature_branch");
    assertEquals("hibtest_feature_branch", HibernateTest.scratchDatabaseName());
  }

  @Test
  void rejectsNameWithCharactersOutsideAnIdentifier() {
    System.setProperty(HibernateTest.DB_NAME_PROPERTY, "hibtest; drop database rspace");
    assertThrows(IllegalArgumentException.class, HibernateTest::scratchDatabaseName);
  }

  @Test
  void rejectsNameWithoutHibtestPrefix() {
    System.setProperty(HibernateTest.DB_NAME_PROPERTY, "rspace");
    assertThrows(IllegalArgumentException.class, HibernateTest::scratchDatabaseName);
  }

  @Test
  void rejectsEmptyName() {
    System.setProperty(HibernateTest.DB_NAME_PROPERTY, "");
    assertThrows(IllegalArgumentException.class, HibernateTest::scratchDatabaseName);
  }

  @Test
  void rejectsNameLongerThanMariaDbAllows() {
    System.setProperty(HibernateTest.DB_NAME_PROPERTY, "hibtest" + "h".repeat(58));
    assertThrows(IllegalArgumentException.class, HibernateTest::scratchDatabaseName);
  }

  @Test
  void acceptsNameOfExactlySixtyFourCharacters() {
    String name = "hibtest" + "h".repeat(57);
    System.setProperty(HibernateTest.DB_NAME_PROPERTY, name);
    assertEquals(name, HibernateTest.scratchDatabaseName());
  }

  @Test
  void scratchUrlDefaultsToLocalTestServer() {
    assertEquals("jdbc:mysql://localhost:3306/hibtest", HibernateUtils.jdbcUrl("hibtest"));
  }

  @Test
  void scratchUrlUsesConfiguredServerAndRemovesTrailingSlash() {
    System.setProperty("jdbc.server.url", "jdbc:mysql://localhost:3377/");
    assertEquals(
        "jdbc:mysql://localhost:3377/hibtest_feature_branch",
        HibernateUtils.jdbcUrl("hibtest_feature_branch"));
  }

  @Test
  void scratchUrlAcceptsTheMavenBuildServerName() {
    System.setProperty("jdbc.url.maven", "jdbc:mysql://localhost:3377");
    assertEquals("jdbc:mysql://localhost:3377/hibtest", HibernateUtils.jdbcUrl("hibtest"));
  }

  @Test
  void scratchUrlPrefersTheNewServerName() {
    System.setProperty("jdbc.server.url", "jdbc:mysql://localhost:3377");
    System.setProperty("jdbc.url.maven", "jdbc:mysql://localhost:3306");
    assertEquals("jdbc:mysql://localhost:3377/hibtest", HibernateUtils.jdbcUrl("hibtest"));
  }
}
