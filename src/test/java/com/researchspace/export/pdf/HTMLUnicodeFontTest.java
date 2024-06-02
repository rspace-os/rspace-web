package com.researchspace.export.pdf;

import static org.junit.jupiter.api.Assertions.*;

import org.jsoup.nodes.Element;
import org.jsoup.parser.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.ValueSource;

public class HTMLUnicodeFontTest {

  HTMLUnicodeFontProcesser fontProcessor;

  @BeforeEach
  void before() {
    fontProcessor = createHtmlUnicodeFontProcesser();
  }

  private HTMLUnicodeFontProcesser createHtmlUnicodeFontProcesser() {
    return new HTMLUnicodeFontProcesser();
  }

  @Test
  void parentStyleAttributesMaintained() {
    // test various CSS font declarations are handled correctly and only font-family name is
    // replaced
    HTMLUnicodeFontProcesser p = createHtmlUnicodeFontProcesser();
    assertTrue(
        p.apply("<p>\u060e</p>").contains("<span style=\"font-family: unifont;\">\u060e</span>"));
    assertTrue(
        p.apply("<p style=\"font-family:Lucida Sans Unicode;font-style:bold;\">\u060e</p>")
            .contains("<span style=\"font-family: unifont;font-style:bold;\">\u060e</span>"));
    assertTrue(
        p.apply("<p style=\"font-size: 24pt;\">\u060e</p>")
            .contains("<span style=\"font-size: 24pt;font-family: unifont;\">\u060e</span>"));
    assertTrue(
        p.apply("<p style=\"font: italic bold 12px/30px Georgia, serif;\">\u060e</p>")
            .contains("<span style=\"font: italic bold 12px/30px unifont, serif;\">\u060e</span>"));
    assertTrue(
        p.apply("<p style=\"font: oblique 300 12px/30px Georgia, serif;\">\u060e</p>")
            .contains("<span style=\"font: oblique 300 12px/30px unifont, serif;\">\u060e</span>"));
    assertTrue(
        p.apply("<p style=\"font: oblique 300 12em Georgia Sans, serif;\">\u060e</p>")
            .contains("<span style=\"font: oblique 300 12em unifont, serif;\">\u060e</span>"));
  }

  String ascii = "a"; // basic ascii
  String latinSupplement = "§"; // in range 7F-FF
  String math = "⟦"; // maths
  String greekExtended = "ἁ"; // should be noto-sans
  String fallback = "圓"; // should  be unifont

  Tag span = Tag.valueOf("span");

  @Test
  @DisplayName("Single character ascii -> 1 span with no style")
  void singleCharAascii1BlockProduces1Element() {
    Element p = createPTagWithText(ascii);
    fontProcessor.processElement(p);
    assertEquals(span, p.child(0).tag());
    assertEquals(1, p.childrenSize());
    assertNoStyleAttribute(p.child(0));
  }

  @Test
  @DisplayName("Multi character ascii -> 1 span with no style")
  void multiCharAascii1BlockProduces1Element() {
    String plainText1 = ascii.repeat(10);
    Element p = createPTagWithText(plainText1);
    fontProcessor.processElement(p);
    assertEquals(span, p.child(0).tag());
    assertEquals(1, p.childrenSize());
    assertNoStyleAttribute(p.child(0));
  }

  @Test
  @DisplayName("Multi character ascii + math -> 2 spans")
  void mixedBlockProduces2Element() {
    String twoBlock = ascii.repeat(10) + math.repeat(10);
    Element p = createPTagWithText(twoBlock);
    fontProcessor.processElement(p);
    assertEquals(2, p.childrenSize());
    assertNoStyleAttribute(p.child(0));
    assertStyleAttribute(p.child(1), PdfFontLocator.MATH_FONT);
  }

  @Test
  @DisplayName("Multi character ascii + math + latin -> 3 spans")
  void lastCharacterDifferent() {
    String twoBlock = ascii.repeat(10) + math.repeat(10) + latinSupplement;
    Element p = createPTagWithText(twoBlock);
    fontProcessor.processElement(p);
    assertEquals(3, p.childrenSize());
    assertNoStyleAttribute(p.child(0));
    assertStyleAttribute(p.child(1), PdfFontLocator.MATH_FONT);
    assertStyleAttribute(p.child(2), PdfFontLocator.STANDARD_FONT);
    assertEquals(latinSupplement, p.child(2).text());
  }

  @DisplayName("Empty content handled")
  @ParameterizedTest
  @EmptySource
  void emptyNodeHandled(String empty) {
    String emptyBlock = empty;
    Element p = createPTagWithText(emptyBlock);
    fontProcessor.processElement(p);
    assertEquals(0, p.childrenSize());
  }

  @DisplayName("blank  content handled")
  @ParameterizedTest
  @ValueSource(strings = {"  ", "  ", "\n\n"})
  void blankNodeHandled(String empty) {
    String emptyBlock = empty;
    Element p = createPTagWithText(emptyBlock);
    fontProcessor.processElement(p);
    assertEquals(0, p.childrenSize());
    assertEquals(1, p.textNodes().size());
    // whitespace is truncated by jsoup
    assertEquals(1, p.textNodes().get(0).text().length());
  }

  @Test
  void allCharTypes() {
    String allCharTypes =
        fallback.repeat(2) + math.repeat(3) + greekExtended.repeat(2) + fallback.repeat(3);
    Element p = createPTagWithText(allCharTypes);
    fontProcessor.processElement(p);
    assertEquals(4, p.childrenSize());
    assertStyleAttribute(p.child(0), PdfFontLocator.FALLBACK_FONT);
    assertStyleAttribute(p.child(1), PdfFontLocator.MATH_FONT);
    assertStyleAttribute(p.child(2), PdfFontLocator.STANDARD_FONT);
    assertStyleAttribute(p.child(3), PdfFontLocator.FALLBACK_FONT);
  }

  private void assertStyleAttribute(Element child, String expectedStyle) {
    String style = String.format("font-family: %s;", expectedStyle);
    assertEquals(style, child.attr("style"));
  }

  private void assertNoStyleAttribute(Element el) {
    assertTrue(el.attr("style").isEmpty());
  }

  private Element createPTagWithText(String plainText1) {
    Element p = new Element("p");
    p.text(plainText1);
    return p;
  }
}
