package com.researchspace.dao;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Guards the rule that no request-supplied sort key reaches query construction. Every {@code
 * getOrderBy()} call in the DAO layer, and in the services that pick a query branch from the sort
 * key, must be wrapped in a sort enum's {@code fromRequest(...)}. Anything else is a new place
 * where request text could be concatenated into HQL or SQL.
 *
 * <p>To add a sort key, add a constant to the listing's enum in {@code
 * com.researchspace.model.sort} and a case to the DAO's switch. Do not read the raw string.
 */
public class OrderBySafeByConstructionTest {

  private static final List<Path> SCANNED_ROOTS =
      List.of(
          Paths.get("src/main/java/com/researchspace/dao"),
          Paths.get("src/main/java/com/researchspace/admin/service/impl"),
          Paths.get("src/main/java/com/researchspace/service/audit/search"),
          Paths.get("src/main/java/com/researchspace/service/impl/AuditManagerImpl.java"));

  private static final Pattern RAW_READ = Pattern.compile("\\.getOrderBy\\(\\)");
  private static final Pattern RESOLVED_READ =
      Pattern.compile("[A-Za-z]+Sort\\.fromRequest\\(\\s*[\\w.]+\\.getOrderBy\\(\\)\\s*\\)");

  @Test
  public void everyOrderByReadInQueryCodeIsResolvedThroughASortEnum() throws IOException {
    List<String> violations = new ArrayList<>();
    for (Path root : SCANNED_ROOTS) {
      try (Stream<Path> files = Files.walk(root)) {
        for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
          List<String> lines = Files.readAllLines(file);
          for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (RAW_READ.matcher(line).find() && !RESOLVED_READ.matcher(line).find()) {
              violations.add(file + ":" + (i + 1) + ": " + line.trim());
            }
          }
        }
      }
    }
    assertTrue(
        violations.isEmpty(),
        "orderBy read without resolving it through a sort enum:\n" + String.join("\n", violations));
  }
}
