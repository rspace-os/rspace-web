package com.researchspace.webapp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PublicDocumentHtmlSanitizerTest {

  private final PublicDocumentHtmlSanitizer sanitizer = new PublicDocumentHtmlSanitizer();

  @Test
  void stripsDBRepoLinkAttributesFromPublicHtml() {
    String sanitized =
        sanitizer.sanitize(
            """
            <p>
              <a class="dbrepo_link" href="https://dbrepo.example/database/db-1/view/view-1"
                 target="_blank" rel="noreferrer" data-dbrepo-type="view"
                 data-dbrepo-database-id="db-1" data-dbrepo-resource-id="view-1"
                 data-dbrepo-database-name="Research data" data-dbrepo-query="SELECT * FROM private"
                 data-dbrepo-url="https://dbrepo.example/database/db-1/view/view-1">Recent experiments</a>
            </p>
            """);

    assertTrue(sanitized.contains("Recent experiments"));
    assertFalse(sanitized.contains("href="));
    assertFalse(sanitized.contains("target="));
    assertFalse(sanitized.contains("dbrepo_link"));
    assertFalse(sanitized.contains("data-dbrepo-"));
    assertFalse(sanitized.contains("SELECT * FROM private"));
  }

  @Test
  void stripsDBRepoInsertedTableLinksFromPublicHtml() {
    String sanitized =
        sanitizer.sanitize(
            """
            <table data-tablesource="dbrepo">
              <tr><th><a href="https://dbrepo.example/database/db-1/table/table-1" target="_blank" rel="noreferrer">table Experiments</a></th></tr>
              <tr><td>Alpha</td></tr>
            </table>
            """);

    assertTrue(sanitized.contains("table Experiments"));
    assertTrue(sanitized.contains("Alpha"));
    assertFalse(sanitized.contains("href="));
    assertFalse(sanitized.contains("target="));
    assertFalse(sanitized.contains("rel="));
  }

  @Test
  void leavesUnrelatedHtmlUnchanged() {
    String html = "<p><a href=\"https://example.com\">ordinary link</a></p>";

    assertEquals(html, sanitizer.sanitize(html));
  }
}
