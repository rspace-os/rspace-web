package com.researchspace.linkedelements;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Pure unit tests for stoichiometry-related methods in {@link RichTextUpdater}. */
class RichTextUpdaterStoichiometryTest {

  private RichTextUpdater updater;

  @BeforeEach
  void setUp() {
    updater = new RichTextUpdater();
  }

  @Test
  void updateStoichiometryIdsInCopy_updatesReactionLinkedChemImage() {
    String html =
        "<img id=\"10\" class=\"chem\" src=\"/chemical/getImageChem/10/1\""
            + " data-stoichiometry-table='{\"id\":100,\"revision\":5}' alt=\"image\"/>";
    Map<Long, Long> idMap = new HashMap<>();
    idMap.put(100L, 200L);

    String result = updater.updateStoichiometryIdsInCopy(idMap, html);

    String attrValue =
        Jsoup.parse(result).select("img.chem").first().attr("data-stoichiometry-table");
    assertTrue(attrValue.contains("\"id\":200"), "id should be updated to 200, got: " + attrValue);
    assertFalse(
        attrValue.contains("revision"), "revision should be removed from copy, got: " + attrValue);
  }

  @Test
  void updateStoichiometryIdsInCopy_updatesStandaloneTableDiv() {
    String html =
        "<div data-stoichiometry-table-only=\"true\""
            + " data-stoichiometry-table='{\"id\":300,\"revision\":2}'>placeholder</div>";
    Map<Long, Long> idMap = new HashMap<>();
    idMap.put(300L, 400L);

    String result = updater.updateStoichiometryIdsInCopy(idMap, html);

    String attrValue =
        Jsoup.parse(result)
            .select("div[data-stoichiometry-table-only=true]")
            .first()
            .attr("data-stoichiometry-table");
    assertTrue(attrValue.contains("\"id\":400"), "id should be updated to 400, got: " + attrValue);
    assertFalse(
        attrValue.contains("revision"), "revision should be removed from copy, got: " + attrValue);
  }

  @Test
  void updateStoichiometryIdsInCopy_noopWhenMapEmpty() {
    String html =
        "<div data-stoichiometry-table-only=\"true\""
            + " data-stoichiometry-table='{\"id\":1,\"revision\":1}'>placeholder</div>";
    String result = updater.updateStoichiometryIdsInCopy(new HashMap<>(), html);
    assertEquals(html, result);
  }

  @Test
  void findStandaloneStoichiometryIds_returnsParsedIds() {
    String html =
        "<div data-stoichiometry-table-only=\"true\""
            + " data-stoichiometry-table='{\"id\":42,\"revision\":3}'>placeholder</div>"
            + "<div data-stoichiometry-table-only=\"true\""
            + " data-stoichiometry-table='{\"id\":99,\"revision\":1}'>placeholder</div>";

    List<Long> ids = updater.findStandaloneStoichiometryIds(html);

    assertEquals(2, ids.size());
    assertTrue(ids.contains(42L));
    assertTrue(ids.contains(99L));
  }

  @Test
  void findStandaloneStoichiometryIds_ignoresChemImages() {
    String html =
        "<img id=\"5\" class=\"chem\" src=\"/chemical/getImageChem/5/1\""
            + " data-stoichiometry-table='{\"id\":77,\"revision\":1}' alt=\"image\"/>";

    List<Long> ids = updater.findStandaloneStoichiometryIds(html);

    assertTrue(ids.isEmpty());
  }
}
