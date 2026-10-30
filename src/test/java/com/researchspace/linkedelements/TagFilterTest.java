package com.researchspace.linkedelements;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import org.junit.jupiter.api.Test;

public class TagFilterTest {

  @Test
  public void stripsKnownTagsWithAttributes() {
    assertEquals(
        "link text",
        TagFilter.removeAllHTMLTags(
            "<a href=\"/x\" title='y'>link</a><br/><p class=\"c\"> text</p>"));
    assertEquals(
        "multi\nline",
        TagFilter.removeAllHTMLTags("<div>multi\n<span data-a=\"1\nb\">line</span>"));
  }

  @Test
  public void stripsTagsWithAngleBracketInAttribute() {
    assertEquals("", TagFilter.removeAllHTMLTags("<img src=x onerror=alert(1) alt=\"<\">"));
    assertEquals("link", TagFilter.removeAllHTMLTags("<a title=\"1<2\" href=\"x\">link</a>"));
  }

  @Test
  public void keepsNonHtmlAngleBrackets() {
    String text = "some text with <angle bracket used with non-HTML tag> and a < b > c";
    assertEquals(text, TagFilter.removeAllHTMLTags(text));
  }

  @Test
  public void unclosedTagsFinishQuickly() {
    String unclosed = "<a " + "</a ".repeat(100_000);
    String closedAtEnd = "<a " + "</a ".repeat(100_000) + ">";
    assertTimeoutPreemptively(
        Duration.ofSeconds(2),
        () -> {
          assertEquals(unclosed, TagFilter.removeAllHTMLTags(unclosed));
          assertEquals("", TagFilter.removeAllHTMLTags(closedAtEnd));
        });
  }
}
