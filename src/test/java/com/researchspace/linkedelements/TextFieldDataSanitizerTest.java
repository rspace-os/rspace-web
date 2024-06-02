package com.researchspace.linkedelements;

import static org.junit.Assert.*;

import com.researchspace.testutils.SpringTransactionalTest;
import org.apache.commons.lang.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class TextFieldDataSanitizerTest extends SpringTransactionalTest {

  @Autowired private TextFieldDataSanitizer fieldDataSanitizer;

  @Before
  public void setUp() throws Exception {
    fieldDataSanitizer.setBaseURL("http://somewhere.com");
  }

  @Test
  public void jsoupFiltersRSPAC_592() {
    String scriptHtml = "<p>text</p><img src=\"/image/123\"><script>alert(1);</script>";
    String cleaned = fieldDataSanitizer.cleanData(scriptHtml);
    assertFalse(cleaned.contains("<script>alert(1);</script>"));
    assertTrue(cleaned.contains("<img src=\"/image/123\" />"));

    String imageHtml = "<p>text</p><img src=\"javascript:alert(1);\"><script>alert(1);</script>";
    cleaned = fieldDataSanitizer.cleanData(imageHtml);
    assertFalse(cleaned.contains("<script>alert(1);</script>"));
    assertFalse(cleaned.contains("javascript:alert(1);"));

    // check data-attributes OK
    String dataHtml =
        "<hr/>\n<p id=\"234\" class=\"cssclass\" style=\"font-color:red\" data-xc=\"123\">text</p>";
    cleaned = fieldDataSanitizer.cleanData(dataHtml);
    // ignore whitespace differences
    dataHtml = StringUtils.remove(dataHtml, " ");
    cleaned = StringUtils.remove(cleaned, " ");
    assertEquals(dataHtml, cleaned);

    String addressHtml = "<address style=\"text-align: left;\">EH8 7NP</address>";
    cleaned = fieldDataSanitizer.cleanData(addressHtml);
    assertEquals(addressHtml, cleaned);

    String tableHtml =
        "<table style=\"width: 559px;\" border=\"1\" cellspacing=\"0\" cellpadding=\"6\"></table>";
    cleaned = fieldDataSanitizer.cleanData(tableHtml);
    assertEquals(tableHtml, cleaned);

    String filestoreLink =
        "<a class=\"nfs_file mceNonEditable\" data-linktype=\"file\" href=\"#\""
            + " rel=\"9:/batch.txt\">batch.txt</a>";
    cleaned = fieldDataSanitizer.cleanData(filestoreLink);
    assertEquals(filestoreLink, cleaned);

    String videoLink =
        "<a id=\"attachOnText_6172\" href=\"/Streamfile/6172\" target=\"_blank\""
            + " data-type=\"Miscellaneous\" data-mce-href=\"/Streamfile/6172\">my video.mp4</a>";
    cleaned = fieldDataSanitizer.cleanData(videoLink);
    assertEquals(videoLink, cleaned);

    String jwplayerObject =
        "<object id=\"videoContainer_6172_1437406969356\" data=\"/scripts/player.swf\""
            + " type=\"application/x-shockwave-flash\" name=\"videoContainer_6172_1437406969356\""
            + " width=\"100%\" height=\"100%\" data-mce-tabindex=\"0\"><param"
            + " name=\"allowfullscreen\" value=\"true\" /><param name=\"allowscriptaccess\""
            + " value=\"never\" /><param name=\"seamlesstabbing\" value=\"true\" /><param"
            + " name=\"wmode\" value=\"opaque\" /><param name=\"flashvars\""
            + " value=\"netstreambasepath=http%3A%2F%2Flocalhost%3A8080%2Fworkspace%2Feditor%2FstructuredDocument%2F104&amp;"
            + "id=videoContainer_6172_1437406969356&amp;className=videoTemp&amp;file=%2FStreamfile%2F6172%2FmyVideo.mp4&amp;controlbar.position=over\""
            + " /></object>";
    cleaned = fieldDataSanitizer.cleanData(jwplayerObject);
    assertEquals(jwplayerObject, cleaned);

    String newHTml = "<img src=\"image.png\" onerror=\"alert('1');\">";
    newHTml = fieldDataSanitizer.cleanData(newHTml);
    System.err.println(newHTml);
    assertFalse(newHTml.contains("alert"));
  }

  @Test
  public void HTMLTagFiltersRSPAC_1572() {
    String scriptHtml = "<p>text</p><img src=\"/image/123\"><script>alert(1);</script>";
    String cleaned = fieldDataSanitizer.textDataOnly(scriptHtml);
    assertFalse(cleaned.contains("<script>"));
    assertFalse(cleaned.contains("</script>"));

    String imageHtml = "<p>text</p><img src=\"javascript:alert(1);\"><script>alert(1);</script>";
    cleaned = fieldDataSanitizer.textDataOnly(imageHtml);
    assertEquals("textalert(1);", cleaned);
    // check data-attributes OK
    String dataHtml =
        "<hr/>\n<p id=\"234\" class=\"cssclass\" style=\"font-color:red\" data-xc=\"123\">text</p>";
    cleaned = fieldDataSanitizer.textDataOnly(dataHtml);
    // ignore whitespace differences
    dataHtml = StringUtils.remove(dataHtml, " ");
    cleaned = StringUtils.remove(cleaned, " ");
    assertEquals("\ntext", cleaned);

    String addressHtml = "<address style=\"text-align: left;\">EH8 7NP</address>";
    cleaned = fieldDataSanitizer.textDataOnly(addressHtml);
    assertEquals("EH8 7NP", cleaned);

    String tableHtml =
        "<table style=\"width: 559px;\" border=\"1\" cellspacing=\"0\" cellpadding=\"6\"></table>";
    cleaned = fieldDataSanitizer.textDataOnly(tableHtml);
    assertEquals("", cleaned);

    String jwplayerObject =
        "<object id=\"videoContainer_6172_1437406969356\" data=\"/scripts/player.swf\""
            + " type=\"application/x-shockwave-flash\" name=\"videoContainer_6172_1437406969356\""
            + " width=\"100%\" height=\"100%\" data-mce-tabindex=\"0\"><param"
            + " name=\"allowfullscreen\" value=\"true\" /><param name=\"allowscriptaccess\""
            + " value=\"never\" /><param name=\"seamlesstabbing\" value=\"true\" /><param"
            + " name=\"wmode\" value=\"opaque\" /><param name=\"flashvars\""
            + " value=\"netstreambasepath=http%3A%2F%2Flocalhost%3A8080%2Fworkspace%2Feditor%2FstructuredDocument%2F104&amp;"
            + "id=videoContainer_6172_1437406969356&amp;className=videoTemp&amp;file=%2FStreamfile%2F6172%2FmyVideo.mp4&amp;controlbar.position=over\""
            + " /></object>";
    cleaned = fieldDataSanitizer.textDataOnly(jwplayerObject);
    assertEquals("", cleaned);
    String noHTMLtag = "some text with <angle bracket used with non-HTML tag>";
    cleaned = fieldDataSanitizer.textDataOnly(noHTMLtag);
    assertEquals(noHTMLtag, cleaned);
  }

  @Test
  public void specialCharacterHandling() {
    // standard ascii not encoded
    String ascii =
        "!\"#$%&amp;'()*+,-./0123456789:;&lt;=&gt;?@ABCDEFGHIJKLMNOPQRSTUVWXYZ["
            + "\\"
            + "]^_`abcdefghijklmnopqrstuvwxyz{|}~";
    String cleaned = fieldDataSanitizer.cleanData(ascii);
    assertEquals(ascii, cleaned);
    // extended chars remain encoded from tinyMCE
    String greek = "&alpha;&beta;&Mu;&imped";
    cleaned = fieldDataSanitizer.cleanData(greek);
    assertEquals(cleaned, cleaned);
  }

  @Test
  public void jsoupCleanDataAllowsYoutubeJoveIframesRSPAC_2393() {

    // random iframe is not allowed (even if safe & valid, e.g. from google calendar)
    String randomIframeHtml =
        "<p>text</p><img src=\"/image/123\"><div class=\"embedIframeDiv mceNonEditable\"><iframe"
            + " src=\"https://calendar.google.com/calendar/embed?src=pl.uk%23holiday%40group.v.calendar.google.com&ctz=Europe%2FWarsaw\""
            + " style=\"border: 0\" width=\"800\" height=\"600\" frameborder=\"0\""
            + " scrolling=\"no\"></iframe></div>";
    String cleaned = fieldDataSanitizer.cleanData(randomIframeHtml);
    assertEquals("<p>text</p><img src=\"/image/123\" />", cleaned);

    // embed iframe from youtube is allowed
    String youtubeEmbedHtml =
        "<p>youtubeTest</p><div class=\"embedIframeDiv mceNonEditable\"><iframe width=\"1280\""
            + " height=\"719\" src=\"https://www.youtube.com/embed/YkRldqVfTJo\" \n"
            + " title=\"YouTube video player\" frameborder=\"0\" \n"
            + " allow=\"accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope;"
            + " picture-in-picture\" \n"
            + " allowfullscreen>\n"
            + "</iframe></div>";
    cleaned = fieldDataSanitizer.cleanData(youtubeEmbedHtml);
    assertTrue("was: " + cleaned, cleaned.contains("youtubeTest"));
    assertTrue(
        "was: " + cleaned,
        cleaned.contains("iframe")
            && cleaned.contains("src=\"https://www.youtube.com/embed/YkRldqVfTJo\"")
            && cleaned.contains(
                "allow=\"accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope;"
                    + " picture-in-picture\""));

    // embed iframe from JoVE is allowed
    String joveEmbedHtml =
        "<p>joveTest</p><iframe id=\"embed-iframe\" allowTransparency=\"true\""
            + " allow=\"encrypted-media *\" \n"
            + " allowfullscreen height=\"415\" width=\"460\" border=\"0\" scrolling=\"no\" \n"
            + " frameborder=\"0\" marginwheight=\"0\" marginwidth=\"0\" \n"
            + " src=\"https://www.jove.com/embed/player?id=54239&t=1&s=1&fpv=1\" >\n"
            + "<p><a title=\"Genome-wide Purification of Extrachromosomal Circular DNA from \n"
            + "Eukaryotic Cells\""
            + " href=\"https://www.jove.com/v/54239/genome-wide-purification-extrachromosomal-circular-dna-from\">\n"
            + "Genome-wide Purification of Extrachromosomal Circular DNA from Eukaryotic"
            + " Cells</a>\n"
            + "</p>\n"
            + "</iframe>";
    cleaned = fieldDataSanitizer.cleanData(joveEmbedHtml);
    assertTrue("was: " + cleaned, cleaned.contains("joveTest"));
    assertTrue(
        "was: " + cleaned,
        cleaned.contains("iframe")
            && cleaned.contains(
                "src=\"https://www.jove.com/embed/player?id=54239&amp;t=1&amp;s=1&amp;fpv=1\"")
            && cleaned.contains("allow=\"encrypted-media *\""));
  }
}
