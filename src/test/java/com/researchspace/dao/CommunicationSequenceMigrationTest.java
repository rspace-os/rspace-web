package com.researchspace.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Runs only on the explicitly selected throwaway schema, with temporary legacy data. */
@EnabledIfSystemProperty(named = "jdbc.url", matches = ".*/rspace_fixtest\\?.*")
public class CommunicationSequenceMigrationTest extends BaseDaoTestCase {
  @Test
  void migrationRespectsLegacyReservationsAndEveryConcreteTable() throws Exception {
    String[] tables = {
      "Notification",
      "MessageOrRequest",
      "GroupMessageOrRequest",
      "CreateGroupMessageOrRequest",
      "ShareRecordMessageOrRequest",
      "CommunicationTarget"
    };
    var parser = DocumentBuilderFactory.newInstance();
    parser.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    String sql;
    try (var input =
        getClass().getResourceAsStream("/sqlUpdates/changeLog-communication-sequences.xml")) {
      sql =
          parser
              .newDocumentBuilder()
              .parse(input)
              .getElementsByTagName("sql")
              .item(0)
              .getTextContent();
    }
    try (var connection =
            DriverManager.getConnection(
                System.getProperty("jdbc.url"), "rspacedbuser", "rspacedbpwd");
        var statement = connection.createStatement()) {
      long[] restore = new long[2];
      String[] sequences = {"communication_seq", "communication_target_seq"};
      for (int i = 0; i < 2; i++) {
        try (var rs = statement.executeQuery("SELECT next_not_cached_value FROM " + sequences[i])) {
          rs.next();
          restore[i] = rs.getLong(1);
        }
      }
      try {
        for (String table : tables) {
          statement.execute("CREATE TEMPORARY TABLE `" + table + "` (id BIGINT)");
        }
        statement.execute(
            "CREATE TEMPORARY TABLE hibernate_sequences (sequence_name VARCHAR(100), next_val"
                + " BIGINT)");
        statement.execute(
            "INSERT INTO hibernate_sequences VALUES ('Communication', 20000),"
                + " ('CommunicationTarget', 30000)");
        for (int highest = 0; highest < tables.length; highest++) {
          for (String table : tables) statement.execute("DELETE FROM `" + table + "`");
          statement.execute("INSERT INTO `" + tables[highest] + "` VALUES (40000)");
          for (String sequence : sequences) statement.execute("DROP SEQUENCE " + sequence);
          for (String part : sql.split(";")) if (!part.isBlank()) statement.execute(part);
          for (int i = 0; i < 2; i++) {
            try (var rs = statement.executeQuery("SELECT NEXT VALUE FOR " + sequences[i])) {
              rs.next();
              long upper = rs.getLong(1);
              long high = i == 0 ? (highest < 5 ? 40000 : 20000) : (highest == 5 ? 40000 : 30000);
              assertTrue(
                  upper - 49 > high, "first pooled block must exceed legacy high water mark");
              assertEquals(0, upper % 50);
            }
          }
          try (var rs = statement.executeQuery("SELECT SUM(next_val) FROM hibernate_sequences")) {
            rs.next();
            assertEquals(50000, rs.getLong(1));
          }
        }
      } finally {
        // Keep any already-cached Hibernate blocks disjoint from subsequent sequence values.
        for (int i = 0; i < 2; i++) {
          statement.execute("DROP SEQUENCE IF EXISTS " + sequences[i]);
          statement.execute(
              "CREATE SEQUENCE "
                  + sequences[i]
                  + " START WITH "
                  + Math.max(100000, restore[i] + 100000)
                  + " INCREMENT BY 50 NOCACHE");
        }
      }
    }
  }
}
