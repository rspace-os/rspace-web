package com.researchspace.testutils;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

/**
 * Points Spring tests at a throwaway MariaDB container unless {@code -Djdbc.url} is given.
 *
 * <p>{@link DatabaseCleaner} empties most tables after every integration-test class, so running
 * DB-backed tests against a shared database (such as the dev stack's {@code rspace} schema) wipes
 * its users and content. CI and Jenkins pass {@code -Djdbc.url} for their own disposable databases,
 * so the extension starts nothing there; {@code TestDatabaseExtensionTest} still starts a container
 * directly, so those runs need Docker.
 *
 * <p>Registered before {@code SpringExtension} so the system properties are set before the context
 * is built. Both callbacks are needed: {@code PER_CLASS} tests build the instance, and so the
 * context, before any {@code BeforeAllCallback} runs.
 */
public class TestDatabaseExtension implements BeforeAllCallback, TestInstancePostProcessor {

  static final String DB_NAME = "rspace";
  static final String DB_USER = "rspacedbuser";
  static final String DB_PASSWORD = "rspacedbpwd";

  // ponytail: one container per test JVM (./gradlew verify starts several), each paying the
  // baseline load and Liquibase catch-up; use Testcontainers withReuse if that gets too slow.
  private static GenericContainer<?> container;

  @Override
  public void beforeAll(ExtensionContext context) {
    ensureStarted();
  }

  @Override
  public void postProcessTestInstance(Object testInstance, ExtensionContext context) {
    ensureStarted();
  }

  private static synchronized void ensureStarted() {
    if (container != null
        || System.getProperty("jdbc.url") != null
        || TestRunnerController.isFastRun()) {
      return;
    }
    GenericContainer<?> db = startContainer();
    System.setProperty("jdbc.url", jdbcUrl(db));
    System.setProperty("jdbc.username", DB_USER);
    System.setProperty("jdbc.password", DB_PASSWORD);
    container = db;
  }

  /** Starts a MariaDB container with the baseline schema loaded; the caller stops it. */
  static GenericContainer<?> startContainer() {
    if (!DockerClientFactory.instance().isDockerAvailable()) {
      throw new IllegalStateException(
          "DB-backed tests need Docker for a throwaway MariaDB, or -Djdbc.url pointing at a"
              + " database whose contents they may delete");
    }
    GenericContainer<?> db =
        new GenericContainer<>("mariadb:11.8")
            .withEnv("MARIADB_ROOT_PASSWORD", DB_PASSWORD)
            .withEnv("MARIADB_DATABASE", DB_NAME)
            .withEnv("MARIADB_USER", DB_USER)
            .withEnv("MARIADB_PASSWORD", DB_PASSWORD)
            .withCopyFileToContainer(
                MountableFile.forHostPath("docker/dev/db/db-config.cnf", 0644),
                "/etc/mysql/conf.d/rspace.cnf")
            // Liquibase cannot bootstrap an empty schema; load the same baseline as
            // -Denvironment=drop-recreate-db, via the mariadb client in the image's init step.
            .withCopyFileToContainer(
                MountableFile.forHostPath(
                    "src/main/resources/sqlUpdates/liquibaseConfig/rs-dbbaseline-utf8.sql", 0644),
                "/docker-entrypoint-initdb.d/00-baseline.sql")
            .withExposedPorts(3306)
            // logged once by the temporary init server and again by the real one
            .waitingFor(Wait.forLogMessage(".*ready for connections.*", 2));
    db.start();
    return db;
  }

  static String jdbcUrl(GenericContainer<?> db) {
    return "jdbc:mysql://" + db.getHost() + ":" + db.getMappedPort(3306) + "/" + DB_NAME;
  }
}
