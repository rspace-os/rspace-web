package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EmailHtmlToPlainTextTest {

  @Test
  void stripsInlineMarkup() {
    assertEquals(
        "Your username is grace.",
        EmailHtmlToPlainText.toPlainText("<p>Your <b>username</b> is grace.</p>"));
  }

  @Test
  void preservesWhitespaceBetweenInlineElements() {
    assertEquals("Hello world", EmailHtmlToPlainText.toPlainText("<b>Hello</b> <i>world</i>"));
  }

  @Test
  void doesNotLeaveWhitespaceBeforeParagraphBreaks() {
    assertEquals(
        "Hello\n\nworld", EmailHtmlToPlainText.toPlainText("<p><b>Hello</b> </p><p>world</p>"));
  }

  @Test
  void linksKeepTheirTargetVisible() {
    assertEquals(
        "Please visit our website (https://example.com).",
        EmailHtmlToPlainText.toPlainText(
            "Please visit <a href=\"https://example.com\">our website</a>."));
  }

  @Test
  void linkTextEqualToUrlIsNotRepeated() {
    assertEquals(
        "Go to https://example.com now.",
        EmailHtmlToPlainText.toPlainText(
            "Go to <a href=\"https://example.com\">https://example.com</a> now."));
  }

  @Test
  void blockElementsBecomeParagraphBreaks() {
    assertEquals(
        "First paragraph.\n\nSecond paragraph.",
        EmailHtmlToPlainText.toPlainText("<p>First paragraph.</p><p>Second paragraph.</p>"));
  }

  @Test
  void preservesLineBreaksAndListMarkers() {
    assertEquals(
        "one\ntwo\n- first\n- second\n\n1. third\n2. fourth",
        EmailHtmlToPlainText.toPlainText(
            "one<br/>two<ul><li>first</li><li>second</li></ul>"
                + "<ol><li>third</li><li>fourth</li></ol>"));
  }

  @Test
  void collapsesWhitespaceInsideText() {
    assertEquals(
        "Hello Jane, welcome.",
        EmailHtmlToPlainText.toPlainText("<html><body>Hello\n   Jane,\n\t welcome.</body></html>"));
  }
}
