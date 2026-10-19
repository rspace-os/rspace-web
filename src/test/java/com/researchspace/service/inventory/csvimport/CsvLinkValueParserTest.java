package com.researchspace.service.inventory.csvimport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.inventory.LinkTargetResolver;
import com.researchspace.service.inventory.LinkTargetSnapshotResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Parses the "<RelationType> <serverUrl>/globalId/<GID>[vN]" cell written by the CSV exporters. */
public class CsvLinkValueParserTest {

  private final CsvLinkValueParser parser = new CsvLinkValueParser();
  private final User importer = new User("importer");

  @BeforeEach
  void setUp() {
    IPropertyHolder properties = mock(IPropertyHolder.class);
    when(properties.getServerUrl()).thenReturn("https://rspace.example.com/");
    parser.properties = properties;
    MessageSourceUtils messages = mock(MessageSourceUtils.class);
    when(messages.getMessage(eq("errors.inventory.import.linkValueInvalid"), any()))
        .thenReturn("bad link cell");
    when(messages.getMessage(eq("errors.inventory.field.linkTargetVersionNotFound"), any()))
        .thenReturn("no such version");
    parser.messages = messages;
    // unreadable by default: the lenient, dangling-link path
    parser.linkTargetResolver = mock(LinkTargetResolver.class);
    parser.snapshotResolver = mock(LinkTargetSnapshotResolver.class);
  }

  @Test
  void parsesPinnedLinkIntoRelationBaseIdAndVersion() {
    ApiInventoryLink link =
        parser.parse("IsDerivedFrom https://rspace.example.com/globalId/SA123v2", importer);

    assertEquals("IsDerivedFrom", link.getRelationType());
    assertEquals("SA123", link.getTargetGlobalId());
    assertEquals(2L, link.getVersionPin());
    assertTrue(link.isSkipTargetCheck());
  }

  @Test
  void unpinnedLinkHasNullVersion() {
    ApiInventoryLink link =
        parser.parse("Cites https://rspace.example.com/globalId/GL44", importer);

    assertEquals("GL44", link.getTargetGlobalId());
    assertNull(link.getVersionPin());
  }

  @Test
  void nullCellIsNotALink() {
    assertFalse(parser.isParseable(null));
    assertThrows(IllegalArgumentException.class, () -> parser.parse(null, importer));
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
        assertThrows(IllegalArgumentException.class, () -> parser.parse(cell, importer))
            .getMessage());
    assertFalse(parser.isParseable(cell));
  }

  @Test
  void blankServerUrlAcceptsNothingRatherThanMatchingARelativeLookingCell() {
    // without a server URL nothing names a local record, not even a cell with no host at all
    IPropertyHolder unset = mock(IPropertyHolder.class);
    when(unset.getServerUrl()).thenReturn("");
    parser.properties = unset;

    assertFalse(parser.isParseable("Cites /globalId/SA1"));
    assertFalse(parser.isParseable("Cites https://rspace.example.com/globalId/SA1"));
  }

  /** The rule the PIDINST import applies too (InventoryUrls.globalIdOfOwnPage, RSDEV-1528). */
  @Test
  void anAddressOfThisServerInAnotherSchemeStillNamesALocalRecord() {
    ApiInventoryLink link =
        parser.parse("Cites http://rspace.example.com/globalId/SA123", importer);

    assertEquals("SA123", link.getTargetGlobalId());
  }

  @Test
  void isParseableIsTrueForValidCell() {
    assertTrue(parser.isParseable("Cites https://rspace.example.com/globalId/SD9"));
  }

  @Test
  void aPinnedVersionThatAReadableTargetLacksIsRefusedWithTheVersionMessage() {
    when(parser.linkTargetResolver.targetExistsAndIsReadable(any(), eq(importer))).thenReturn(true);
    when(parser.snapshotResolver.resolveRevisionForVersion(GlobalIdPrefix.SA, 123L, 7L))
        .thenReturn(null);

    IllegalArgumentException refused =
        assertThrows(
            IllegalArgumentException.class,
            () -> parser.parse("References https://rspace.example.com/globalId/SA123v7", importer));

    assertEquals("no such version", refused.getMessage());
    verify(parser.messages)
        .getMessage(
            eq("errors.inventory.field.linkTargetVersionNotFound"),
            aryEq(new Object[] {"SA123", 7L}));
  }

  @Test
  void aPinnedVersionThatAReadableTargetHasParses() {
    when(parser.linkTargetResolver.targetExistsAndIsReadable(any(), eq(importer))).thenReturn(true);
    when(parser.snapshotResolver.resolveRevisionForVersion(GlobalIdPrefix.SA, 123L, 2L))
        .thenReturn(44L);

    ApiInventoryLink link =
        parser.parse("References https://rspace.example.com/globalId/SA123v2", importer);

    assertEquals(2L, link.getVersionPin());
    assertTrue(link.isSkipTargetCheck());
  }

  @Test
  void aPinnedVersionOfATargetTheImporterCannotReadIsNotLookedAt() {
    // ADR 0002: the dangling link stays, and nothing about the item's versions is consulted
    when(parser.linkTargetResolver.targetExistsAndIsReadable(any(), eq(importer)))
        .thenReturn(false);

    ApiInventoryLink link =
        parser.parse("References https://rspace.example.com/globalId/SA123v7", importer);

    assertEquals(7L, link.getVersionPin());
    assertTrue(link.isSkipTargetCheck());
    verify(parser.snapshotResolver, never()).resolveRevisionForVersion(any(), any(), any());
  }

  @Test
  void anUnpinnedLinkAsksNothingOfTheTarget() {
    parser.parse("Cites https://rspace.example.com/globalId/GL44", importer);

    verify(parser.linkTargetResolver, never()).targetExistsAndIsReadable(any(), any());
  }
}
