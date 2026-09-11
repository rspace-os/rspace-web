package com.researchspace.service.inventory.csvimport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.MessageSourceUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Parses the "<RelationType> <serverUrl>/globalId/<GID>[vN]" cell written by the CSV exporters. */
public class CsvLinkValueParserTest {

  private final CsvLinkValueParser parser = new CsvLinkValueParser();

  @BeforeEach
  void setUp() {
    IPropertyHolder properties = mock(IPropertyHolder.class);
    when(properties.getServerUrl()).thenReturn("https://rspace.example.com/");
    parser.properties = properties;
    MessageSourceUtils messages = mock(MessageSourceUtils.class);
    when(messages.getMessage(eq("errors.inventory.import.linkValueInvalid"), any()))
        .thenReturn("bad link cell");
    parser.messages = messages;
  }

  @Test
  void parsesPinnedLinkIntoRelationBaseIdAndVersion() {
    ApiInventoryLink link =
        parser.parse("IsDerivedFrom https://rspace.example.com/globalId/SA123v2");

    assertEquals("IsDerivedFrom", link.getRelationType());
    assertEquals("SA123", link.getTargetGlobalId());
    assertEquals(2L, link.getVersionPin());
    assertTrue(link.isSkipTargetCheck());
  }

  @Test
  void unpinnedLinkHasNullVersion() {
    ApiInventoryLink link = parser.parse("Cites https://rspace.example.com/globalId/GL44");

    assertEquals("GL44", link.getTargetGlobalId());
    assertNull(link.getVersionPin());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "NotARelation https://rspace.example.com/globalId/SA123",
        "Cites https://other.example.com/globalId/SA123",
        "Cites SA123",
        "Cites https://rspace.example.com/globalId/US123",
        "https://rspace.example.com/globalId/SA123",
        "Cites https://rspace.example.com/globalId/SA123 extra"
      })
  void rejectsMalformedForeignHostBareIdAndDisallowedPrefix(String cell) {
    assertEquals(
        "bad link cell",
        assertThrows(IllegalArgumentException.class, () -> parser.parse(cell)).getMessage());
    assertFalse(parser.isParseable(cell));
  }

  @Test
  void isParseableIsTrueForValidCell() {
    assertTrue(parser.isParseable("Cites https://rspace.example.com/globalId/SD9"));
  }
}
