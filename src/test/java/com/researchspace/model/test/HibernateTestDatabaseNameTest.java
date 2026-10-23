package com.researchspace.model.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HibernateTestDatabaseNameTest {

  private String savedProperty;

  @BeforeEach
  void saveProperty() {
    savedProperty = System.getProperty(HibernateTest.DB_NAME_PROPERTY);
    System.clearProperty(HibernateTest.DB_NAME_PROPERTY);
  }

  @AfterEach
  void restoreProperty() {
    if (savedProperty == null) {
      System.clearProperty(HibernateTest.DB_NAME_PROPERTY);
    } else {
      System.setProperty(HibernateTest.DB_NAME_PROPERTY, savedProperty);
    }
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
  void rejectsNameLongerThanMariaDbAllows() {
    System.setProperty(HibernateTest.DB_NAME_PROPERTY, "h".repeat(65));
    assertThrows(IllegalArgumentException.class, HibernateTest::scratchDatabaseName);
  }

  @Test
  void acceptsNameOfExactlySixtyFourCharacters() {
    String name = "h".repeat(64);
    System.setProperty(HibernateTest.DB_NAME_PROPERTY, name);
    assertEquals(name, HibernateTest.scratchDatabaseName());
  }
}
