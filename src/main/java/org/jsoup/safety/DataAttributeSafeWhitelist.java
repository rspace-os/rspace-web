package org.jsoup.safety;

import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Element;

/**
 * This is a hacked version of Safelist that also allows any data-attributes without explicitly
 * listing them upfront. This class extends Safelist, but because of static methods in Safelist, and
 * lack of accessors to internal state, we need to copy the relaxed() method here, and also use the
 * jsoup package name, so that we can invoke protected methods.
 *
 * <p>This class used to extend Whitelist, but Whitelist was deprecated in JSoup 1.14 and replaced
 * by Safelist with no functional changes.
 */
public class DataAttributeSafeWhitelist extends Safelist {

  public static DataAttributeSafeWhitelist relaxedWithDataAttr() {
    DataAttributeSafeWhitelist wl = new DataAttributeSafeWhitelist();
    wl.addTags(
            "a",
            "b",
            "blockquote",
            "br",
            "caption",
            "cite",
            "code",
            "col",
            "colgroup",
            "dd",
            "div",
            "dl",
            "dt",
            "em",
            "h1",
            "h2",
            "h3",
            "h4",
            "h5",
            "h6",
            "i",
            "img",
            "li",
            "object",
            "ol",
            "p",
            "param",
            "pre",
            "q",
            "small",
            "span",
            "strike",
            "strong",
            "sub",
            "sup",
            "table",
            "tbody",
            "td",
            "tfoot",
            "th",
            "thead",
            "tr",
            "u",
            "ul")
        .addAttributes("a", "href", "title", "rel", "target")
        .addAttributes("blockquote", "cite")
        .addAttributes("col", "span", "width")
        .addAttributes("colgroup", "span", "width")
        .addAttributes("img", "align", "alt", "height", "src", "title", "width")
        .addAttributes("object", "data", "type", "name", "width", "height")
        .addAttributes("ol", "start", "type")
        .addAttributes("param", "name", "value")
        .addAttributes("q", "cite")
        .addAttributes("table", "summary", "width", "border", "cellspacing", "cellpadding")
        .addAttributes("td", "abbr", "axis", "colspan", "rowspan", "width")
        .addAttributes("th", "abbr", "axis", "colspan", "rowspan", "scope", "width")
        .addAttributes("ul", "type")
        .addProtocols("a", "href", "ftp", "http", "https", "mailto")
        .addProtocols("blockquote", "cite", "http", "https")
        .addProtocols("cite", "cite", "http", "https")
        .addProtocols("img", "src", "http", "https")
        .addProtocols("q", "cite", "http", "https");
    addExtraRSpaceWhitelist(wl);
    return wl;
  }

  // when jsoup supports wild-card data- attributes, we can use this method on a regular whitelist.
  private static void addExtraRSpaceWhitelist(DataAttributeSafeWhitelist wl) {
    wl.addAttributes(":all", "id")
        .addAttributes(":all", "class")
        .addAttributes(":all", "style")
        .addTags("address")
        .addTags("hr");
  }

  /** Delegates to superclass, in addition it will permit data- attributes */
  protected boolean isSafeAttribute(String tagName, Element el, Attribute attr) {
    boolean safe = super.isSafeAttribute(tagName, el, attr);
    if (!safe) {
      safe = attr.getKey().startsWith("data-");
    }
    if (!safe) {
      safe =
          tagName.equals("img")
              && attr.getKey().equals("src")
              && attr.getValue().startsWith("data:image");
    }
    return safe;
  }
}
