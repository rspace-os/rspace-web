package com.researchspace.dao.customliquibaseupdates;

import java.sql.Connection;
import java.sql.SQLException;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.integration.spring.SpringLiquibase;
import liquibase.resource.ClassLoaderResourceAccessor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Calls liquibase programmatically for live, non-blocking updates not run in post-bean creation
 * method.
 *
 * <p><strong>Do not</strong> use this for schema changes. This class is for time-consuming data
 * migrations that can safely run in the background without disrupting user activity.
 */
@Component
@Getter
@Setter
@Slf4j
public class LiveLiqUpdater implements AsyncLiveLiquibaseUpdater {

  public LiveLiqUpdater(String liveUpdateChangelog) {
    super();
    this.liveUpdateChangelog = liveUpdateChangelog;
  }

  private @Autowired SpringLiquibase springLiquibase;
  // set in BaseConfig
  private String liveUpdateChangelog;

  public void executeAsync() throws Exception {
    log.info("Logging live updates");
    java.sql.Connection connection = openConnection();
    Database database =
        DatabaseFactory.getInstance()
            .findCorrectDatabaseImplementation(new JdbcConnection(connection));
    try (Liquibase liquibase =
        new Liquibase(liveUpdateChangelog, new ClassLoaderResourceAccessor(), database)) {
      liquibase.update(new Contexts(springLiquibase.getContexts()), new LabelExpression());
    }
  }

  private Connection openConnection() throws SQLException {
    return springLiquibase.getDataSource().getConnection();
  }
}
