package com.researchspace.linkedelements;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.*;

import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class TextFieldEmbedIframeHandlerTest extends SpringTransactionalTest {

  @Autowired TextFieldEmbedIframeHandler iframeHandler;

  @Test
  public void checkRandomIframeLeftUnconverted() {
    // random iframe is not converted (even if safe & valid, e.g. from google calendar)
    String randomIframeHtml =
        "<p>start</p><img src=\"/image/123\"><div class=\"embedIframeDiv mceNonEditable\"><iframe"
            + " src=\"https://calendar.google.com/calendar/embed?src=pl.uk%23holiday%40group.v.calendar.google.com&ctz=Europe%2FWarsaw\""
            + " style=\"border: 0\" width=\"800\" height=\"600\" frameborder=\"0\""
            + " scrolling=\"no\"></iframe></div><p>end</p>";

    String convertedHtml = iframeHandler.encodeKnownIframesAsParagraphs(randomIframeHtml);
    assertEquals(randomIframeHtml, convertedHtml);

    // decoding only changed if input contains known encoded paragraph or empty iframe wrapper
    convertedHtml = iframeHandler.decodeKnownIframesFromParagraphs(randomIframeHtml);
    assertEquals(randomIframeHtml, convertedHtml);
  }

  @Test
  public void checkIncomingTagsMarkedAsKnownIframeReplacementAreStripped() {
    // random iframe is not converted (even if safe & valid, e.g. from google calendar)
    String randomIframeHtml =
        "<p>start</p><p class='"
            + TextFieldEmbedIframeHandler.KNOWN_IFRAME_PARAGRAPH_CLASS
            + "' data-test='test'></p><p>end</p>";

    String convertedHtml = iframeHandler.encodeKnownIframesAsParagraphs(randomIframeHtml);
    assertEquals("<p>start</p>\n<p>end</p>", convertedHtml);
  }

  @Test
  public void checkIframeSrcRecognizedAsKnown() {
    // youtube embed code src variants
    assertTrue(iframeHandler.isKnownIframeSrc("https://www.youtube.com/embed/q27r-ZBw9lI"));
    assertTrue(iframeHandler.isKnownIframeSrc("https://www.youtube.com/embed/YkRldqVfTJo"));
    assertTrue(
        iframeHandler.isKnownIframeSrc(
            "https://www.youtube.com/embed/bhRExXIGxek?si=i85Qk9Y2son5jSZ6"));
    assertTrue(
        iframeHandler.isKnownIframeSrc("https://www.youtube.com/embed/YkRldqVfTJo?start=18"));
    assertTrue(
        iframeHandler.isKnownIframeSrc("https://www.youtube.com/embed/YkRldqVfTJo?controls=0"));
    assertTrue(
        iframeHandler.isKnownIframeSrc(
            "https://www.youtube.com/embed/YkRldqVfTJo?controls=0&amp;start=16"));
    assertTrue(
        iframeHandler.isKnownIframeSrc(
            "https://www.youtube.com/embed/bhRExXIGxek?si=i85Qk9Y2son5jSZ6&amp;controls=0&amp;start=2"));
    assertTrue(
        iframeHandler.isKnownIframeSrc(
            "https://www.youtube.com/embed/bhRExXIGxek?si=i85Qk9Y2son5jSZ6&controls=0&start=2"));
    assertTrue(
        iframeHandler.isKnownIframeSrc("https://www.youtube-nocookie.com/embed/YkRldqVfTJo"));
    assertTrue(
        iframeHandler.isKnownIframeSrc(
            "https://www.youtube-nocookie.com/embed/bhRExXIGxek"
                + "?si=i85Qk9Y2son5jSZ6&amp;controls=0&amp;start=2"));
    assertTrue(
        iframeHandler.isKnownIframeSrc(
            "https://www.youtube-nocookie.com/embed/bhRExXIGxek"
                + "?si=i85Qk9Y2son5jSZ6&controls=0&start=2"));

    // jove embed code src variants
    assertTrue(iframeHandler.isKnownIframeSrc("https://www.jove.com/embed/player?id=54239"));
    assertTrue(
        iframeHandler.isKnownIframeSrc(
            "https://www.jove.com/embed/player?id=54239&t=1&a=1&i=1&chap=1&s=1&fpv=1"));
    assertTrue(
        iframeHandler.isKnownIframeSrc(
            "https://www.jove.com/embed/player?id=54239&a=1&s=1&i=1&chap=1&t=1&fpv=1")); // different order
    assertTrue(
        iframeHandler.isKnownIframeSrc(
            "https://www.jove.com/embed/player?id=54239&language=Dutch&t=1&s=1&fpv=1"));
    assertTrue(
        iframeHandler.isKnownIframeSrc("https://app.jove.com/embed/player?id=2359&t=1&s=1&fpv=1"));

    // av.tib.eu videos pattern
    assertTrue(iframeHandler.isKnownIframeSrc("https://av.tib.eu/player/70488"));

    assertFalse(iframeHandler.isKnownIframeSrc(""));
    assertFalse(iframeHandler.isKnownIframeSrc("test"));
    assertFalse(
        iframeHandler.isKnownIframeSrc("https://calendar.google.com/calendar/embed?src=pl.uk"));
    assertFalse(iframeHandler.isKnownIframeSrc(" https://www.youtube.com/embed/YkRldqVfTJo"));
    assertFalse(iframeHandler.isKnownIframeSrc("https://www.youtube.com/embed/YkRldqVfTJo-dummy!"));
    assertFalse(
        iframeHandler.isKnownIframeSrc("otherText_https://www.youtube.com/embed/YkRldqVfTJo"));
  }

  @Test
  public void checkYoutubeIframeConversion_Jan2022() {

    // youtube embed code fragment (taken in January 2022)
    String youtubeEmbed =
        "<iframe width=\"560\" height=\"315\" src=\"https://www.youtube.com/embed/q27r-ZBw9lI\""
            + " title=\"YouTube video player\" frameborder=\"0\" allow=\"accelerometer; autoplay;"
            + " clipboard-write; encrypted-media; gyroscope; picture-in-picture\" "
            + "allowfullscreen></iframe>";
    String expectedConvertedYoutubeEmbed =
        "<p class=\"rsKnownIframeReplacement\""
            + " data-src=\"https://www.youtube.com/embed/q27r-ZBw9lI\" data-title=\"YouTube video"
            + " player\" data-width=\"560\" data-height=\"315\" data-frameborder=\"0\""
            + " data-allow=\"accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope;"
            + " picture-in-picture\" data-allowfullscreen=\"\"></p>";

    // youtube embed code fragment, with 'privacy enhanced mode' selected
    String youtubePrivacyModeEmbed =
        "<iframe width=\"560\" height=\"315\""
            + " src=\"https://www.youtube-nocookie.com/embed/YkRldqVfTJo?start=18\" title=\"YouTube"
            + " video player\" frameborder=\"0\" allow=\"accelerometer; autoplay; clipboard-write;"
            + " encrypted-media; gyroscope; picture-in-picture\" allowfullscreen></iframe>";
    String expectedConvertedYoutubePrivacyModeEmbed =
        "<p class=\"rsKnownIframeReplacement\""
            + " data-src=\"https://www.youtube-nocookie.com/embed/YkRldqVfTJo?start=18\""
            + " data-title=\"YouTube video player\" data-width=\"560\" data-height=\"315\""
            + " data-frameborder=\"0\" data-allow=\"accelerometer; autoplay; clipboard-write;"
            + " encrypted-media; gyroscope; picture-in-picture\" data-allowfullscreen=\"\"></p>";

    String htmlFragmentFormat = "<p>start</p>\n%s <img src=\"123\" />\n%s\n<p>end</p>";
    String htmlFragment = String.format(htmlFragmentFormat, youtubeEmbed, youtubePrivacyModeEmbed);
    String expectedConvertedHtml =
        String.format(
            htmlFragmentFormat,
            expectedConvertedYoutubeEmbed,
            expectedConvertedYoutubePrivacyModeEmbed);

    String convertedHtml = iframeHandler.encodeKnownIframesAsParagraphs(htmlFragment);
    assertEquals(expectedConvertedHtml, convertedHtml);

    String restoredHtml = iframeHandler.decodeKnownIframesFromParagraphs(convertedHtml);
    String restoredHtmlAdjustedForEqualsAssertion =
        restoredHtml
            .replaceAll("/p> <iframe", "/p>\n<iframe")
            .replaceAll("/> <iframe", "/>\n<iframe")
            .replaceAll("> </iframe>", "></iframe>")
            .replaceAll("allowfullscreen=\"\"", "allowfullscreen");
    assertEquals(htmlFragment, restoredHtmlAdjustedForEqualsAssertion);
  }

  @Test
  public void checkYoutubeIframeConversion_Dec2024() {

    // youtube embed code fragment (taken in December 2024)
    String youtubeEmbed =
        "<iframe width=\"560\" height=\"315\" "
            + "src=\"https://www.youtube.com/embed/bhRExXIGxek?si=i85Qk9Y2son5jSZ6\" "
            + "title=\"YouTube video player\" frameborder=\"0\" "
            + "allow=\"accelerometer; autoplay; clipboard-write; encrypted-media; "
            + "gyroscope; picture-in-picture; web-share\" "
            + "referrerpolicy=\"strict-origin-when-cross-origin\" allowfullscreen></iframe>";
    String expectedConvertedYoutubeEmbed =
        "<p class=\"rsKnownIframeReplacement\""
            + " data-src=\"https://www.youtube.com/embed/bhRExXIGxek?si=i85Qk9Y2son5jSZ6\""
            + " data-title=\"YouTube video player\" data-width=\"560\" data-height=\"315\""
            + " data-frameborder=\"0\" data-allow=\"accelerometer; autoplay; clipboard-write;"
            + " encrypted-media; gyroscope; picture-in-picture; web-share\""
            + " data-allowfullscreen=\"\""
            + " data-referrerpolicy=\"strict-origin-when-cross-origin\"></p>";

    // youtube embed code fragment, with 'privacy enhanced mode', no controls, and start time
    String youtubePrivacyModeEmbed =
        "<iframe width=\"560\" height=\"315\""
            + " src=\"https://www.youtube-nocookie.com/embed/bhRExXIGxek?si=i85Qk9Y2son5jSZ6&amp;controls=0&amp;start=2\""
            + " title=\"YouTube video player\" frameborder=\"0\" allow=\"accelerometer; autoplay;"
            + " clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share\""
            + " referrerpolicy=\"strict-origin-when-cross-origin\" allowfullscreen></iframe>";
    String expectedConvertedYoutubePrivacyModeEmbed =
        "<p class=\"rsKnownIframeReplacement\""
            + " data-src=\"https://www.youtube-nocookie.com/embed/bhRExXIGxek?si=i85Qk9Y2son5jSZ6&amp;controls=0&amp;start=2\""
            + " data-title=\"YouTube video player\" data-width=\"560\" data-height=\"315\""
            + " data-frameborder=\"0\" data-allow=\"accelerometer; autoplay; clipboard-write;"
            + " encrypted-media; gyroscope; picture-in-picture; web-share\""
            + " data-allowfullscreen=\"\""
            + " data-referrerpolicy=\"strict-origin-when-cross-origin\"></p>";

    String htmlFragmentFormat = "<p>start</p>\n%s <img src=\"123\" />\n%s\n<p>end</p>";
    String htmlFragment = String.format(htmlFragmentFormat, youtubeEmbed, youtubePrivacyModeEmbed);
    String expectedConvertedHtml =
        String.format(
            htmlFragmentFormat,
            expectedConvertedYoutubeEmbed,
            expectedConvertedYoutubePrivacyModeEmbed);

    String convertedHtml = iframeHandler.encodeKnownIframesAsParagraphs(htmlFragment);
    assertEquals(expectedConvertedHtml, convertedHtml);

    String restoredHtml = iframeHandler.decodeKnownIframesFromParagraphs(convertedHtml);
    String restoredHtmlAdjustedForEqualsAssertion =
        restoredHtml
            .replaceAll("/p> <iframe", "/p>\n<iframe")
            .replaceAll("/> <iframe", "/>\n<iframe")
            .replaceAll("> </iframe>", "></iframe>")
            .replaceAll("allowfullscreen=\"\"", "allowfullscreen");
    assertEquals(htmlFragment, restoredHtmlAdjustedForEqualsAssertion);
  }

  @Test
  public void checkJoveIframeConversion_Jan2022() {

    // jove embed code fragment (taken in January 2022)
    String joveEmbed =
        "<iframe id=\"embed-iframe\" allowTransparency=\"true\" allow=\"encrypted-media *\""
            + " allowfullscreen height=\"415\" width=\"460\" border=\"0\" scrolling=\"no\""
            + " frameborder=\"0\" marginwheight=\"0\" marginwidth=\"0\""
            + " src=\"https://www.jove.com/embed/player?id=54239&t=1&s=1&fpv=1\" ><p><a"
            + " title=\"Genome-wide Purification of Extrachromosomal Circular DNA from Eukaryotic"
            + " Cells\" "
            + "href=\"https://www.jove.com/v/54239/genome-wide-purification-extrachromosomal-circular-dna-from\">Genome-wide"
            + " Purification of Extrachromosomal Circular DNA from Eukaryotic Cells"
            + "</a></p></iframe>";
    String expectedConvertedJoveEmbed =
        "<p class=\"rsKnownIframeReplacement\" "
            + "data-src=\"https://www.jove.com/embed/player?id=54239&amp;t=1&amp;s=1&amp;fpv=1\" "
            + "data-width=\"460\" data-height=\"415\" data-border=\"0\" data-frameborder=\"0\" "
            + "data-marginwheight=\"0\" data-marginwidth=\"0\" "
            + "data-allow=\"encrypted-media *\" data-allowfullscreen=\"\" "
            + "data-allowtransparency=\"true\" data-scrolling=\"no\"></p>";
    String expectedRestoredJoveEmbed =
        "<iframe width=\"460\" height=\"415\""
            + " src=\"https://www.jove.com/embed/player?id=54239&amp;t=1&amp;s=1&amp;fpv=1\""
            + " border=\"0\" frameborder=\"0\" marginwheight=\"0\" marginwidth=\"0\""
            + " allow=\"encrypted-media *\" allowfullscreen=\"\" allowtransparency=\"true\""
            + " scrolling=\"no\"> </iframe>";

    String htmlFragmentFormat = "<p>start</p>\n%s <img src=\"123\" />\n<p>end</p>";
    String htmlFragment = String.format(htmlFragmentFormat, joveEmbed);
    String expectedConvertedHtml = String.format(htmlFragmentFormat, expectedConvertedJoveEmbed);

    String convertedHtml = iframeHandler.encodeKnownIframesAsParagraphs(htmlFragment);
    assertEquals(expectedConvertedHtml, convertedHtml);

    String restoredHtml = iframeHandler.decodeKnownIframesFromParagraphs(convertedHtml);
    String restoredHtmlAdjustedForEqualsAssertion =
        restoredHtml.replaceAll("/p> <iframe", "/p>\n<iframe");
    String expectedRestoredHtml = String.format(htmlFragmentFormat, expectedRestoredJoveEmbed);
    assertEquals(expectedRestoredHtml, restoredHtmlAdjustedForEqualsAssertion);
  }

  @Test
  public void checkJoveIframeConversion_Dec2024() {

    // jove embed code fragment (taken in December 2024)
    String joveEmbed =
        "<iframe id=\"embed-iframe\" allowTransparency=\"true\" allow=\"encrypted-media *\""
            + " allowfullscreen height=\"415\" width=\"460\" border=\"0\" scrolling=\"no\""
            + " frameborder=\"0\" marginwheight=\"0\" marginwidth=\"0\""
            + " src=\"https://app.jove.com/embed/player?id=2359&t=1&s=1&fpv=1\"></iframe>";
    String expectedConvertedJoveEmbed =
        "<p class=\"rsKnownIframeReplacement\" "
            + "data-src=\"https://app.jove.com/embed/player?id=2359&amp;t=1&amp;s=1&amp;fpv=1\" "
            + "data-width=\"460\" data-height=\"415\" data-border=\"0\" data-frameborder=\"0\" "
            + "data-marginwheight=\"0\" data-marginwidth=\"0\" "
            + "data-allow=\"encrypted-media *\" data-allowfullscreen=\"\" "
            + "data-allowtransparency=\"true\" data-scrolling=\"no\"></p>";
    String expectedRestoredJoveEmbed =
        "<iframe width=\"460\" height=\"415\""
            + " src=\"https://app.jove.com/embed/player?id=2359&amp;t=1&amp;s=1&amp;fpv=1\""
            + " border=\"0\" frameborder=\"0\" marginwheight=\"0\" marginwidth=\"0\""
            + " allow=\"encrypted-media *\" allowfullscreen=\"\" allowtransparency=\"true\""
            + " scrolling=\"no\"> </iframe>";

    String htmlFragmentFormat = "<p>start</p>\n%s <img src=\"123\" />\n<p>end</p>";
    String htmlFragment = String.format(htmlFragmentFormat, joveEmbed);
    String expectedConvertedHtml = String.format(htmlFragmentFormat, expectedConvertedJoveEmbed);

    String convertedHtml = iframeHandler.encodeKnownIframesAsParagraphs(htmlFragment);
    assertEquals(expectedConvertedHtml, convertedHtml);

    String restoredHtml = iframeHandler.decodeKnownIframesFromParagraphs(convertedHtml);
    String restoredHtmlAdjustedForEqualsAssertion =
        restoredHtml.replaceAll("/p> <iframe", "/p>\n<iframe");
    String expectedRestoredHtml = String.format(htmlFragmentFormat, expectedRestoredJoveEmbed);
    assertEquals(expectedRestoredHtml, restoredHtmlAdjustedForEqualsAssertion);
  }

  @Test
  public void checkTibAvPortalIframeConversion_Jul2025() {

    // TIV AV portal embed code fragment (taken in July 2025)
    String avportalEmbed =
        "<iframe width=\"560\" src=\"https://av.tib.eu/player/70488\" allow=\"fullscreen\" "
            + "style=\"aspect-ratio: 16 / 9;\"></iframe>";
    String expectedConvertedAvportalEmbed =
        "<p class=\"rsKnownIframeReplacement\" "
            + "data-src=\"https://av.tib.eu/player/70488\" "
            + "data-width=\"560\" data-allow=\"fullscreen\"></p>";
    String expectedRestoredAvportalEmbed =
        "<iframe width=\"560\" src=\"https://av.tib.eu/player/70488\" allow=\"fullscreen\"> "
            + "</iframe>";

    String htmlFragmentFormat = "<p>start</p>\n%s <img src=\"123\" />\n<p>end</p>";
    String htmlFragment = String.format(htmlFragmentFormat, avportalEmbed);
    String expectedConvertedHtml =
        String.format(htmlFragmentFormat, expectedConvertedAvportalEmbed);

    String convertedHtml = iframeHandler.encodeKnownIframesAsParagraphs(htmlFragment);
    assertEquals(expectedConvertedHtml, convertedHtml);

    String restoredHtml = iframeHandler.decodeKnownIframesFromParagraphs(convertedHtml);
    String restoredHtmlAdjustedForEqualsAssertion =
        restoredHtml.replaceAll("/p> <iframe", "/p>\n<iframe");
    String expectedRestoredHtml = String.format(htmlFragmentFormat, expectedRestoredAvportalEmbed);
    assertEquals(expectedRestoredHtml, restoredHtmlAdjustedForEqualsAssertion);
  }

  @Test
  public void checkOnlyKnownValidAttributesEncoded() {

    // youtube embed code fragment with invalid/unknown attributes
    String youtubeEmbed =
        "<iframe width=\"<?>\" height=\"315a\" "
            + "src=\"https://www.youtube.com/embed/YkRldqVfTJo\" "
            + "title=\"YouTube video player\" allow=\"everything\" "
            + "allowTransparency=\"why\" scrolling=\"sure\" unknown=\"yes\">"
            + "</iframe>";
    String expectedConvertedYoutubeEmbed =
        "<p class=\"rsKnownIframeReplacement\" "
            + "data-src=\"https://www.youtube.com/embed/YkRldqVfTJo\" "
            + "data-title=\"YouTube video player\"></p>";

    String htmlFragmentFormat = "<p>start</p> <img src=\"123\" />\n%s\n<p>end</p>";
    String htmlFragment = String.format(htmlFragmentFormat, youtubeEmbed);
    String expectedConvertedHtml = String.format(htmlFragmentFormat, expectedConvertedYoutubeEmbed);

    String convertedHtml = iframeHandler.encodeKnownIframesAsParagraphs(htmlFragment);
    assertEquals(expectedConvertedHtml, convertedHtml);
  }

  @Test
  public void checkAttributeValidityPatterns() {

    assertNull(iframeHandler.getEncodedAttrValue("title", "Matt's title"));
    assertEquals("Test Title", iframeHandler.getEncodedAttrValue("title", "Test Title"));

    assertNull(iframeHandler.getEncodedAttrValue("width", "abc"));
    assertNull(iframeHandler.getEncodedAttrValue("width", "-5"));
    assertEquals("5", iframeHandler.getEncodedAttrValue("width", "5"));
    assertNull(iframeHandler.getEncodedAttrValue("height", "abc"));
    assertEquals("123", iframeHandler.getEncodedAttrValue("width", "123"));

    assertNull(iframeHandler.getEncodedAttrValue("border", "abc"));
    assertEquals("123", iframeHandler.getEncodedAttrValue("border", "123"));
    assertNull(iframeHandler.getEncodedAttrValue("frameborder", "abc"));
    assertEquals("123", iframeHandler.getEncodedAttrValue("frameborder", "123"));
    assertNull(iframeHandler.getEncodedAttrValue("marginwheight", "abc"));
    assertEquals("123", iframeHandler.getEncodedAttrValue("marginwheight", "123"));
    assertNull(iframeHandler.getEncodedAttrValue("marginwidth", "abc"));
    assertEquals("123", iframeHandler.getEncodedAttrValue("marginwidth", "123"));

    assertNull(iframeHandler.getEncodedAttrValue("allow", "unknown"));
    assertEquals(
        "encrypted-media *", iframeHandler.getEncodedAttrValue("allow", "encrypted-media *"));
    assertEquals("accelerometer;", iframeHandler.getEncodedAttrValue("allow", "accelerometer;"));
    assertEquals("autoplay", iframeHandler.getEncodedAttrValue("allow", "autoplay"));
    assertEquals(
        "gyroscope; picture-in-picture; gyroscope;",
        iframeHandler.getEncodedAttrValue("allow", "gyroscope; picture-in-picture; gyroscope;"));

    assertNull(iframeHandler.getEncodedAttrValue("allowfullscreen", "abc"));
    assertEquals("", iframeHandler.getEncodedAttrValue("allowfullscreen", ""));
    assertNull(iframeHandler.getEncodedAttrValue("allowtransparency", "yes"));
    assertEquals("true", iframeHandler.getEncodedAttrValue("allowtransparency", "true"));
    assertNull(iframeHandler.getEncodedAttrValue("scrolling", "true"));
    assertEquals("no", iframeHandler.getEncodedAttrValue("scrolling", "no"));
  }
}
