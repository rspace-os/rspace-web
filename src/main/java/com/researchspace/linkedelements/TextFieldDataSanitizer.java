package com.researchspace.linkedelements;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document.OutputSettings;
import org.jsoup.nodes.Document.OutputSettings.Syntax;
import org.jsoup.safety.DataAttributeSafeWhitelist;
import org.jsoup.safety.Safelist;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Methods for parsing and sanitizing text field content. */
@Component("textFieldDataSanitizer")
public class TextFieldDataSanitizer {

  @Value("${server.urls.prefix}")
  private String baseURL;

  public void setBaseURL(String baseURL) {
    this.baseURL = baseURL;
  }

  @Autowired private TextFieldEmbedIframeHandler iframeHandler;

  private final Safelist whitelist =
      DataAttributeSafeWhitelist.relaxedWithDataAttr().preserveRelativeLinks(true);

  /**
   * Sanitised incoming HTML
   *
   * @param input
   * @return
   */
  public String cleanData(String input) {
    if (StringUtils.isEmpty(input)) {
      return input;
    }

    // we need to prefix with this element in order to preserve relative links
    // so that jsoup can resolve them against a URL
    input = String.format("<base href='%s'/>", baseURL) + input;
    OutputSettings settings = new OutputSettings().syntax(Syntax.xml);

    input = encodeKnownUnsafeElementsBeforeCleanup(input);
    input = Jsoup.clean(input, baseURL, whitelist, settings);
    input = decodeKnownUnsafeElementsAfterCleanup(input);

    return input;
  }

  private String encodeKnownUnsafeElementsBeforeCleanup(String input) {
    return iframeHandler.encodeKnownIframesAsParagraphs(input);
  }

  private String decodeKnownUnsafeElementsAfterCleanup(String input) {
    return iframeHandler.decodeKnownIframesFromParagraphs(input);
  }

  /**
   * Blacklists all HTML tags but retains non-HTML tags, &lt; e.g. this &gt; RSPAC-1572
   *
   * @param input
   * @return The modified string
   */
  public String textDataOnly(String input) {
    if (StringUtils.isEmpty(input)) {
      return input;
    }

    input = TagFilter.removeAllHTMLTags(input);
    return input;
  }
}
