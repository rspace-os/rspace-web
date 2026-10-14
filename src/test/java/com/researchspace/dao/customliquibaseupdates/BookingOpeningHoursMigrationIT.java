package com.researchspace.dao.customliquibaseupdates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Runs the additive migration against isolated legacy tables in the throwaway dev database. */
@EnabledIfSystemProperty(named = "booking.migration.jdbc.url", matches = ".+")
class BookingOpeningHoursMigrationIT {

  @Test
  void upgradesEffectiveSchedulesAndPreservesUnknownAndDeletionHistory() throws Exception {
    String url = System.getProperty("booking.migration.jdbc.url");
    String schema = "booking_hours_" + UUID.randomUUID().toString().replace("-", "");
    try (Connection connection = DriverManager.getConnection(url, "root", "rspacedbpwd");
        Statement sql = connection.createStatement()) {
      sql.execute(
          "CREATE DATABASE " + schema + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
      try {
        sql.execute("USE " + schema);
        for (String table : List.of("BookingConfiguration", "BookingConfigurationDefaults")) {
          sql.execute(
              "CREATE TABLE "
                  + table
                  + " (id BIGINT PRIMARY KEY, openingStart VARCHAR(5) NOT NULL, openingEnd"
                  + " VARCHAR(5) NOT NULL, openDays VARCHAR(64) NOT NULL, openingExceptions"
                  + " VARCHAR(1024) NOT NULL)");
          sql.execute(
              "CREATE TABLE "
                  + table
                  + "_AUD (id BIGINT, REV INT, REVTYPE TINYINT, openingStart VARCHAR(5), openingEnd"
                  + " VARCHAR(5), openDays VARCHAR(64), openingExceptions VARCHAR(1024), PRIMARY"
                  + " KEY(id, REV))");
          sql.execute(
              "INSERT INTO "
                  + table
                  + " VALUES(1,'09:00','17:00','[5,1,3]',"
                  + "'[{\"dayOfWeek\":5,\"start\":\"08:00\",\"end\":\"12:00\"},"
                  + "{\"dayOfWeek\":3,\"start\":\"00:00\",\"end\":\"24:00\"},"
                  + "{\"dayOfWeek\":6,\"start\":\"10:00\",\"end\":\"16:00\"}]')");
          sql.execute(
              "INSERT INTO "
                  + table
                  + "_AUD VALUES(1,1,0,'08:00','18:00',NULL,NULL),"
                  + "(1,2,1,'09:00','17:00','[5,1]','[{\"dayOfWeek\":5,\"start\":\"08:00\",\"end\":\"12:00\"}]'),"
                  + "(1,3,1,NULL,NULL,NULL,NULL),(1,4,2,'09:00','17:00','[1]','[]'),"
                  + "(1,5,NULL,'08:00','18:00',NULL,NULL)");
        }
        var database =
            DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(connection));
        database.setDefaultCatalogName(schema);
        try (var resources = new ClassLoaderResourceAccessor()) {
          new Liquibase("sqlUpdates/changeLog-booking-independent-hours.xml", resources, database)
              .update(new Contexts("run"));
        }
        for (String table : List.of("BookingConfiguration", "BookingConfigurationDefaults")) {
          ObjectMapper mapper = new ObjectMapper();
          assertEquals(
              mapper.readTree(
                  """
                  [{"dayOfWeek":1,"start":"09:00","end":"17:00"},
                   {"dayOfWeek":3,"start":"00:00","end":"24:00"},
                   {"dayOfWeek":5,"start":"08:00","end":"12:00"}]
                  """),
              mapper.readTree(value(sql, "SELECT openingHours FROM " + table + " WHERE id=1")));
          assertEquals(
              fullWeek("08:00", "18:00"),
              mapper.readTree(
                  value(sql, "SELECT openingHours FROM " + table + "_AUD WHERE id=1 AND REV=1")));
          assertEquals(
              mapper.readTree(
                  """
                  [{"dayOfWeek":1,"start":"09:00","end":"17:00"},
                   {"dayOfWeek":5,"start":"08:00","end":"12:00"}]
                  """),
              mapper.readTree(
                  value(sql, "SELECT openingHours FROM " + table + "_AUD WHERE id=1 AND REV=2")));
          assertEquals(
              fullWeek("08:00", "18:00"),
              mapper.readTree(
                  value(sql, "SELECT openingHours FROM " + table + "_AUD WHERE id=1 AND REV=5")));
          assertNull(value(sql, "SELECT openingHours FROM " + table + "_AUD WHERE id=1 AND REV=3"));
          assertNull(value(sql, "SELECT openingHours FROM " + table + "_AUD WHERE id=1 AND REV=4"));
          sql.execute(
              "INSERT INTO "
                  + table
                  + " (id, openingHours) VALUES(2, '"
                  + fullWeek("00:00", "24:00").toString()
                  + "')");
          assertNull(value(sql, "SELECT openingStart FROM " + table + " WHERE id=2"));
          assertEquals("09:00", value(sql, "SELECT openingStart FROM " + table + " WHERE id=1"));
        }
      } finally {
        sql.execute("DROP DATABASE " + schema);
      }
    }
  }

  private static ArrayNode fullWeek(String start, String end) {
    ArrayNode week = new ObjectMapper().createArrayNode();
    for (int day = 1; day <= 7; day++) {
      week.addObject().put("dayOfWeek", day).put("start", start).put("end", end);
    }
    return week;
  }

  private static String value(Statement statement, String query) throws Exception {
    try (ResultSet result = statement.executeQuery(query)) {
      result.next();
      return result.getString(1);
    }
  }
}
