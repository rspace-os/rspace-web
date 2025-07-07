package com.researchspace.linkedelements;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.velocity.app.VelocityEngine;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attributes;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Tag;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.ui.velocity.VelocityEngineUtils;

/**
 * Methods for encoding/decoding known embed code iframes. Currently supports iframes from Youtube
 * and JoVE.
 */
@Component("textFieldEmbedIframeHandler")
public class TextFieldEmbedIframeHandler {

  @Autowired private VelocityEngine velocity;

  // youtube src url last fragment, e.g. 'YkRldqVfTJo?start=17'
  private static final String YOUTUBE_SRC_SUFFIX_PATTERN =
      "([\\w-])+(\\?((si=[\\w-]+)|((&amp;)?&?start=\\d+)|((&amp;)?&?controls=0))+)?";

  // jove src url last fragment, e.g. 'id=54239&t=1&s=1&fpv=1'
  private static final String JOVE_SRC_SUFFIX_PATTERN =
      "\\?id=\\d+((&\\w+=1)|(&language=\\w+)|(&access=\\w+)|(&utm_source=\\w+))*";

   // TIB AV-Portal src url last fragment
  private static final String AVPORTAL_SRC_SUFFIX_PATTERN = "\\d+";

  private static final String[] knownIframeSrcPatterns = {
    "https://www.youtube.com/embed/" + YOUTUBE_SRC_SUFFIX_PATTERN,
    "https://www.youtube-nocookie.com/embed/" + YOUTUBE_SRC_SUFFIX_PATTERN,
    "https://www.jove.com/embed/player" + JOVE_SRC_SUFFIX_PATTERN,
    "https://app.jove.com/embed/player" + JOVE_SRC_SUFFIX_PATTERN,
    "https://av.tib.eu/player/" + AVPORTAL_SRC_SUFFIX_PATTERN
  };

  private static final Map<String, String> knownIframeAttrsAndPatterns;

  static {
    knownIframeAttrsAndPatterns = new LinkedHashMap<>();
    knownIframeAttrsAndPatterns.put(
        "src", ".+"); /* src was already validated for iframe to be processed */
    knownIframeAttrsAndPatterns.put("title", "[\\w\\s]*");
    knownIframeAttrsAndPatterns.put("width", "\\d+");
    knownIframeAttrsAndPatterns.put("height", "\\d+");
    knownIframeAttrsAndPatterns.put("border", "\\d+");
    knownIframeAttrsAndPatterns.put("frameborder", "\\d+");
    knownIframeAttrsAndPatterns.put("marginwheight", "\\d+");
    knownIframeAttrsAndPatterns.put("marginwidth", "\\d+");
    knownIframeAttrsAndPatterns.put(
        "allow",
        "((\\s?accelerometer;?)|(\\s?autoplay;?)|(\\s?clipboard-write;?)|(\\s?encrypted-media;?)"
            + "|(\\s?encrypted-media \\*;?)|(\\s?fullscreen;?)|(\\s?gyroscope;?)"
            + "|(\\s?picture-in-picture;?)|(\\s?web-share;?))*");
    knownIframeAttrsAndPatterns.put("allowfullscreen", "");
    knownIframeAttrsAndPatterns.put("allowtransparency", "true");
    knownIframeAttrsAndPatterns.put("referrerpolicy", "strict-origin-when-cross-origin");
    knownIframeAttrsAndPatterns.put("scrolling", "no");
  }

  static final String KNOWN_IFRAME_PARAGRAPH_CLASS = "rsKnownIframeReplacement";
  private static final String FRONTEND_IFRAME_WRAPPER_CLASS = "embedIframeDiv";

  /**
   * Encodes iframes that has known & supported source into paragraph tags
   * ('p.rsKnownIframeReplacement'), so they won't be removed by sanitization code
   * ('JSoup.clean()').
   *
   * <p>Also removes any incoming paragraphs marked with supported iframe class
   * ('rsKnownIframeReplacement'), as these should be only added by our internal (trusted) code. We
   * need to properly decode input into jsoup Document to make sure such paragraphs won't get
   * through.
   */
  public String encodeKnownIframesAsParagraphs(String input) {
    Document d = Jsoup.parse(input);
    boolean contentChanged = false;

    Elements incomingInternalParagraphs = d.getElementsByClass(KNOWN_IFRAME_PARAGRAPH_CLASS);
    for (Element para : incomingInternalParagraphs) {
      para.remove();
      contentChanged = true;
    }

    Elements iframes = d.getElementsByTag("iframe");
    for (Element iframe : iframes) {
      String iframeSrc = iframe.attr("src");
      if (isKnownIframeSrc(iframeSrc)) {
        iframe.replaceWith(encodeKnownIframeAsParagraph(iframe));
        contentChanged = true;
      }
    }
    return contentChanged ? printDocumentAsHtmlString(d) : input;
  }

  boolean isKnownIframeSrc(String iframeSrc) {
    return Arrays.stream(knownIframeSrcPatterns).anyMatch(iframeSrc::matches);
  }

  private Element encodeKnownIframeAsParagraph(Element iframe) {
    Attributes attrs = new Attributes();
    attrs.add("class", "rsKnownIframeReplacement");
    for (String attrName : knownIframeAttrsAndPatterns.keySet()) {
      if (iframe.hasAttr(attrName)) {
        String encodedAttrValue = getEncodedAttrValue(attrName, iframe.attr(attrName));
        if (encodedAttrValue != null) {
          attrs.add("data-" + attrName, encodedAttrValue);
        }
      }
    }
    return new Element(Tag.valueOf("p"), "", attrs);
  }

  String getEncodedAttrValue(String attrName, String attrValue) {
    String attrPattern = knownIframeAttrsAndPatterns.get(attrName);
    if (attrValue != null && attrValue.matches(attrPattern)) {
      return attrValue; // matching, should be used
    }
    return null; // not matching, should be ignored
  }

  private String printDocumentAsHtmlString(Document d) {
    d.outputSettings().syntax(Document.OutputSettings.Syntax.xml);
    return d.body().html();
  }

  /**
   * Decodes iframes encoded in paragraph tags ('p.rsKnownIframeReplacement') into safe iframes,
   * coming from our own template.
   *
   * <p>Also removes empty iframe wrapper divs ('div.embedIframeDiv') that could be left after
   * sanitization code removed the unknown/untrusted iframe.
   */
  public String decodeKnownIframesFromParagraphs(String input) {
    if (!(input.contains(KNOWN_IFRAME_PARAGRAPH_CLASS)
        || input.contains(FRONTEND_IFRAME_WRAPPER_CLASS))) {
      return input; // nothing to decode or skip
    }

    Document d = Jsoup.parse(input);

    boolean contentChanged = removeEmptyIframeWrapperDivs(d);
    Elements knownIframeParagraphs = d.select("p." + KNOWN_IFRAME_PARAGRAPH_CLASS);
    if (!contentChanged && knownIframeParagraphs.isEmpty()) {
      return input;
    }

    for (Element p : knownIframeParagraphs) {
      p.replaceWith(decodeKnownIframeFromParagraph(p));
    }
    return printDocumentAsHtmlString(d);
  }

  private boolean removeEmptyIframeWrapperDivs(Document d) {
    boolean contentChanged = false;
    Elements iframeWrappers = d.select("div." + FRONTEND_IFRAME_WRAPPER_CLASS);
    for (Element div : iframeWrappers) {
      if (div.children().isEmpty()) {
        div.remove();
        contentChanged = true;
      }
    }
    return contentChanged;
  }

  private Element decodeKnownIframeFromParagraph(Element p) {

    Map<String, Object> velocityModel = new HashMap<>();
    for (String attrName : knownIframeAttrsAndPatterns.keySet()) {
      String dataAttrName = "data-" + attrName;
      String attrValue = "";
      if (p.hasAttr(dataAttrName)) {
        attrValue = attrName + "=\"" + p.attr(dataAttrName) + "\"";
      }
      velocityModel.put(attrName, attrValue);
    }

    String iframeHtml =
        VelocityEngineUtils.mergeTemplateIntoString(
            velocity, "embedIframe.vm", "UTF-8", velocityModel);
    return Jsoup.parse(iframeHtml).getElementsByTag("iframe").first();
  }
}
