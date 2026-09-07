package com.researchspace.model.comms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class HTMLCleanerTest {

  HTMLCleaner rtu;

  @BeforeEach
  public void setUp() throws Exception {
    rtu = new HTMLCleaner();
  }

  @AfterEach
  public void tearDown() throws Exception {}

  @Test
  public void testCleanHTMLStrict() {

    String plain = "Plain text with no html";
    assertEquals(plain, rtu.cleanHTMLStrict(plain, true));
    String plainWithNewline = "Plain text \n with newline";
    assertTrue(
        rtu.cleanHTMLStrict(plainWithNewline, true).contains("<br>"),
        rtu.cleanHTMLStrict(plainWithNewline, true));

    String basicHTML = "Simple html <a href='http://www.google.com'>Google</a>";
    assertTrue(
        rtu.cleanHTMLStrict(basicHTML, true).contains("href=\"http://www.google.com\"")
            && rtu.cleanHTMLStrict(basicHTML, true).contains("Google")
            && rtu.cleanHTMLStrict(basicHTML, true).contains("rel=\"nofollow\""),
        rtu.cleanHTMLStrict(basicHTML, true));

    basicHTML = "Simple html <a href='http://www.google.com' target='_blank'>Google</a>";
    assertTrue(
        rtu.cleanHTMLStrict(basicHTML, true).contains("href=\"http://www.google.com\"")
            && rtu.cleanHTMLStrict(basicHTML, true).contains("Google")
            && rtu.cleanHTMLStrict(basicHTML, true).contains("rel=\"nofollow\"")
            && rtu.cleanHTMLStrict(basicHTML, true).contains("target=\"_blank\""),
        rtu.cleanHTMLStrict(basicHTML, true));

    String scriptHTML = "Simple html <script>alert(1);</script>";
    String processedscriptHTML = rtu.cleanHTMLStrict(scriptHTML, true);
    assertFalse(
        processedscriptHTML.contains("<script>") || processedscriptHTML.contains("alert(1)"),
        processedscriptHTML);

    String basicLink = "Simple link \nhttp://www.google.com ";
    String taggedhttp = rtu.cleanHTMLStrict(basicLink, true);
    String EXPECTED =
        "<a href='http://www.google.com' rel='nofollow' class='word-wrap' target='_blank'>";
    assertTrue(taggedhttp.contains(EXPECTED), taggedhttp);

    String basicLink2 = "http://www.google.com google";
    String taggedhttp2 = rtu.cleanHTMLStrict(basicLink2, true);
    String EXPECTED2 =
        "<a href='http://www.google.com' rel='nofollow' class='word-wrap' target='_blank'>";
    assertTrue(taggedhttp2.contains(EXPECTED2), taggedhttp2);

    String twoLinks = "http://www.google.com google http://www.bbc.co.uk bbc";
    String taggedtwoLinks = rtu.cleanHTMLStrict(twoLinks, true);
    String EXPECTED3 =
        "<a href='http://www.google.com' rel='nofollow' class='word-wrap' target='_blank'>";
    String EXPECTED4 =
        "<a href='http://www.bbc.co.uk' rel='nofollow' class='word-wrap' target='_blank'>";
    assertTrue(
        taggedtwoLinks.contains(EXPECTED3) && taggedtwoLinks.contains(EXPECTED4), taggedtwoLinks);

    String httpsLink = "Simple link https://www.google.com ";
    String taggedhttps = rtu.cleanHTMLStrict(httpsLink, true);
    EXPECTED = "<a href='https://www.google.com' rel='nofollow' class='word-wrap' target='_blank'>";
    assertTrue(taggedhttps.contains(EXPECTED), taggedhttps);

    taggedhttps = rtu.cleanHTMLStrict(httpsLink, false);
    assertFalse(taggedhttps.contains(EXPECTED), taggedhttps);
  }
}
