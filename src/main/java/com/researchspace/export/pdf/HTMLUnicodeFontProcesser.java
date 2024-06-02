package com.researchspace.export.pdf;

import java.util.*;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Document.OutputSettings;
import org.jsoup.nodes.Document.OutputSettings.Syntax;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

/**
 * Prepares HTML for PDF export, adding in font-family declarations for a correct font library where
 * required.
 */
@Slf4j
@Service
public class HTMLUnicodeFontProcesser implements UnaryOperator<String> {
  private final PdfFontLocator fontLocator = new PdfFontLocator();

  Pattern fontFamily = Pattern.compile("font-family\\s*:\\s*([\\w\\s]+);?");
  // see https://www.w3schools.com/cssref/pr_font_font.asp for full description
  Pattern font =
      Pattern.compile(
          "font\\s*:\\s*(?:normal|italic|oblique)?" // style
              + "\\s+(?:normal|small-caps|initial|inherit)?\\s*" // variant
              + "(?:normal|bold|bolder|lighter|\\d+|initial|inherit)?\\s*" // weight
              // size/line-height
              + "(?:medium|xx-small|x-small|smaller|larger|x-large|xx-large|small|large|(?:\\d+[^\\s]+)?|initial|inherit)?\\s*"
              + // family
              "([\\w\\s/]+)?", // group1, other groups are non-capturing
          Pattern.COMMENTS);

  @Override
  public String apply(String html) {
    Document d = Jsoup.parse(html);
    Elements all = d.getAllElements();
    Iterator<Element> it = all.iterator();
    while (it.hasNext()) {
      Element el = it.next();
      processElement(el);
    }
    OutputSettings output = new OutputSettings().syntax(Syntax.xml);
    d.outputSettings(output);
    return d.html();
  }

  void processElement(Element el) {
    String style = el.attr("style");

    List<TextNode> textNodes = el.textNodes();
    for (TextNode tNode : textNodes) {
      String text = tNode.text();
      // ignore pure whitespace e.g. for \n or spaces  outside of HTML elements
      if (StringUtils.isBlank(text)) {
        continue;
      }

      List<Pair<String, String>> fontBlocks = new ArrayList<>();
      // initialise with first character
      StringBuilder sb = new StringBuilder();
      sb.append(text.charAt(0));
      String currFont = fontLocator.getReplacementFont(text.charAt(0));
      for (int i = 1; i < text.length(); i++) {
        char currChar = text.charAt(i);

        var font = currChar <= 0x7F ? "" : fontLocator.getReplacementFont(currChar);
        if (font.equals(currFont) || Character.isWhitespace(currChar)) {
          sb.append(currChar);
        } else {
          // we are now using a different font; record the previous text block and font
          fontBlocks.add(ImmutablePair.of(sb.toString(), currFont));
          currFont = font;
          // .. and initialise a new block with the current character
          sb = new StringBuilder(String.valueOf(currChar));
        }
      }
      // add the last piece of text
      fontBlocks.add(ImmutablePair.of(sb.toString(), currFont));

      // convert the string2font mappings to jsoup span elements
      List<Element> fontedEls = processFontBlocks(fontBlocks, style);

      // now replace the original text node with the list of jsoup spans.
      tNode.replaceWith(fontedEls.get(0));
      // append each element after the previous one
      for (int i = 1; i < fontedEls.size(); i++) {
        Element next = fontedEls.get(i);
        fontedEls.get(i - 1).after(next);
      }
    }
  }

  // convert a list of string2font mappings to a list of jsoup span elements
  private List<Element> processFontBlocks(List<Pair<String, String>> fontBlocks, String style) {
    List<Element> els = new ArrayList<>();
    for (Pair<String, String> str2Font : fontBlocks) {
      String toReplace = "";
      toReplace = getStyleReplacement(style, str2Font, toReplace);
      Element el = new Element("span");
      // this will be empty if it's a regular ascii character, we don't need to override the font
      if (!StringUtils.isEmpty(toReplace)) {
        el.attr("style", toReplace);
      }
      el.text(str2Font.getKey());
      els.add(el);
    }
    return els;
  }

  // this deals with replacing the font family when there is more complex font definition
  private String getStyleReplacement(
      String style, Pair<String, String> str2Font, String toReplace) {
    if (!StringUtils.isBlank(style)) {
      Matcher fontFamilyMatcher = fontFamily.matcher(style);
      Matcher fontShortcutMatcher = font.matcher(style);
      if (fontFamilyMatcher.find()) {
        // we must replace 1st group.
        toReplace = fontFamilyMatcher.replaceFirst(fontFamilyFor(str2Font.getValue()));
      } else if (fontShortcutMatcher.find()) {
        // we must replace 1st group.
        toReplace = style.replace(fontShortcutMatcher.group(1), str2Font.getValue());
      } else {
        // no match, we append it on
        toReplace = style + (style.endsWith(";") ? "" : ";") + fontFamilyFor(str2Font.getValue());
      }
      fontFamilyMatcher.reset();
    } else if (!StringUtils.isEmpty(str2Font.getValue())) {
      toReplace = fontFamilyFor(str2Font.getValue());
    }
    return toReplace;
  }

  private String fontFamilyFor(String font) {
    // font can be empty str if it's just a regular ascii char, in which case
    // we don't want to alter the current style.
    if (StringUtils.isBlank(font)) {
      return "";
    }
    return "font-family: " + font + ";";
  }
}
