package com.researchspace.dao.hibernate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.hibernate.dialect.Dialect;
import org.junit.jupiter.api.Test;

/**
 * Fast unit test (no Spring context, no DB) asserting the packaged default deployment properties
 * carry a usable {@code hibernate.dialect}.
 *
 * <p>The application context resolves the dialect placeholder in {@code applicationContext-dao.xml}
 * from deployment properties at runtime; {@code defaultDeployment.properties} supplies the default
 * for every deployment. If the value is missing (or the build-time substitution from pom.xml
 * breaks), Hibernate 6 silently drops the unresolved setting and probes the database at startup to
 * guess the dialect, so the application fails to boot wherever that probe cannot connect.
 */
public class HibernateDialectConfigTest {

  @Test
  public void defaultDeploymentPropertiesDeclareALoadableDialect() throws Exception {
    String dialect = loadDefaultDeploymentProperties().getProperty("hibernate.dialect");

    assertNotNull(dialect, "hibernate.dialect is missing from defaultDeployment.properties");
    assertFalse(
        dialect.isBlank() || dialect.contains("${"),
        "hibernate.dialect was not substituted at build time: [" + dialect + "]");
    assertTrue(
        Dialect.class.isAssignableFrom(Class.forName(dialect)),
        dialect + " is not a Hibernate Dialect");
  }

  private Properties loadDefaultDeploymentProperties() throws IOException {
    Properties properties = new Properties();
    try (InputStream in =
        getClass().getResourceAsStream("/deployments/defaultDeployment.properties")) {
      assertNotNull(in, "deployments/defaultDeployment.properties not on classpath");
      properties.load(in);
    }
    return properties;
  }
}
