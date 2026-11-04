package com.researchspace.build;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

/**
 * Drops a MariaDB/MySQL database, recreates it with utf8mb4_unicode_ci collation, and loads an
 * optional baseline SQL script into it. Failures to drop or create are logged and ignored; a
 * failure in the baseline script fails the task.
 */
@UntrackedTask(because = "Resets external database state on every run")
public abstract class RecreateDatabase extends DefaultTask {

  /** JDBC driver jars. */
  @Classpath
  public abstract ConfigurableFileCollection getDriverClasspath();

  @Input
  public abstract Property<String> getDriverClassName();

  /** Server URL without a database name, e.g. {@code jdbc:mysql://localhost:3306}. */
  @Input
  public abstract Property<String> getServerUrl();

  @Input
  public abstract Property<String> getDatabaseName();

  @Input
  public abstract Property<String> getUsername();

  @Internal
  public abstract Property<String> getPassword();

  /** Loaded into the new database when set. */
  @InputFile
  @Optional
  @PathSensitive(PathSensitivity.NONE)
  public abstract RegularFileProperty getBaselineScript();

  @TaskAction
  public void recreate() throws IOException, ReflectiveOperationException {
    String db = getDatabaseName().get();
    if (!db.matches("[A-Za-z0-9_]{1,64}")) {
      throw new GradleException("Database name must be a plain identifier: " + db);
    }
    String script =
        getBaselineScript().isPresent()
            ? Files.readString(getBaselineScript().get().getAsFile().toPath(), StandardCharsets.UTF_8)
            : null;
    URL[] urls = new URL[getDriverClasspath().getFiles().size()];
    int i = 0;
    for (File jar : getDriverClasspath().getFiles()) {
      urls[i++] = jar.toURI().toURL();
    }
    try (URLClassLoader loader = new URLClassLoader(urls, getClass().getClassLoader())) {
      Driver driver =
          (Driver)
              Class.forName(getDriverClassName().get(), true, loader)
                  .getDeclaredConstructor()
                  .newInstance();
      Properties credentials = new Properties();
      credentials.setProperty("user", getUsername().get());
      credentials.setProperty("password", getPassword().get());
      // the baseline is a mysqldump file; let the server parse it whole, comments included
      credentials.setProperty("allowMultiQueries", "true");
      String url = getServerUrl().get() + "?useSSL=false";
      try (Connection connection = driver.connect(url, credentials);
          Statement statement = connection.createStatement()) {
        executeContinuingOnError(statement, "drop database if exists " + db);
        executeContinuingOnError(
            statement, "create database if not exists " + db + " collate 'utf8mb4_unicode_ci'");
        if (script != null) {
          statement.execute("use " + db);
          runScript(statement, script);
          getLogger().lifecycle("Recreated database {} from {}", db, getBaselineScript().get());
        } else {
          getLogger().lifecycle("Recreated empty database {}", db);
        }
      } catch (SQLException e) {
        throw new GradleException("Could not recreate database " + db + " at " + url, e);
      }
    }
  }

  private void executeContinuingOnError(Statement statement, String sql) {
    try {
      statement.execute(sql);
    } catch (SQLException e) {
      getLogger().warn("Ignoring failure of '{}': {}", sql, e.getMessage());
    }
  }

  /** Executes every statement in the script, surfacing the first failing one. */
  private static void runScript(Statement statement, String script) throws SQLException {
    boolean hasResultSet = statement.execute(script);
    while (hasResultSet || statement.getUpdateCount() != -1) {
      hasResultSet = statement.getMoreResults();
    }
  }
}
